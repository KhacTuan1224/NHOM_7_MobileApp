package com.rescuefarm.data.remote.firebase;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.domain.enums.CartOwnerType;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.service.network.NetworkStatusProvider;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseCartRepository implements CartRepository {
    private static final String CARTS = "customerCarts";
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final String customerId;
    private final NetworkStatusProvider network;
    private final MutableLiveData<Cart> cart;
    private final ListenerRegistration listener;

    public FirebaseCartRepository(String customerId, NetworkStatusProvider network) {
        this.customerId = customerId; this.network = network;
        cart = new MutableLiveData<>(Cart.empty(CartOwnerType.CUSTOMER, customerId));
        listener = firestore.collection(CARTS).document(customerId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                android.util.Log.e("FirebaseCartRepository", "Listen for customerCarts/" + customerId + " failed: " + error.getMessage(), error);
                return;
            }
            if (snapshot != null) {
                cart.setValue(snapshot.exists() ? mapCart(snapshot) : Cart.empty(CartOwnerType.CUSTOMER, customerId));
            }
        });
    }

    @Override public LiveData<Cart> observeCart(String ignored) { return cart; }
    @Override public CartOwnerType getOwnerType() { return CartOwnerType.CUSTOMER; }
    @Override public String getOwnerKey() { return customerId; }

    @Override public void addOrReplace(CartItem item, ActionCallback callback) {
        mutate(callback, value -> {
            CartItem existing = find(value, item.getId());
            value.addItem(existing == null ? item : item.withQuantity(
                    existing.getQuantity() + item.getQuantity()));
        });
    }

    public void mergeItems(List<CartItem> incomingItems, ActionCallback callback) {
        if (incomingItems == null || incomingItems.isEmpty()) {
            if (callback != null) callback.onSuccess();
            return;
        }
        mutate(callback, value -> {
            for (CartItem item : incomingItems) {
                if (item != null) {
                    CartItem existing = find(value, item.getId());
                    value.addItem(existing == null ? item : item.withQuantity(
                            existing.getQuantity() + item.getQuantity()));
                }
            }
        });
    }
    @Override public void replace(CartItem item, ActionCallback callback) {
        mutate(callback, value -> {
            requireItem(value, item.getId()); value.addItem(item);
        });
    }
    @Override public void updateQuantity(String itemId, double quantity, ActionCallback callback) {
        if (!Double.isFinite(quantity) || quantity <= 0D) {
            callback.onError(ErrorCode.VALIDATION, "Số lượng phải là số dương."); return;
        }
        mutate(callback, value -> value.addItem(requireItem(value, itemId).withQuantity(quantity)));
    }
    @Override public void setSelected(String itemId, boolean selected, ActionCallback callback) {
        mutate(callback, value -> value.addItem(requireItem(value, itemId).withSelection(selected)));
    }
    @Override public void updateQuote(String itemId, double unitPrice, ActionCallback callback) {
        mutate(callback, value -> value.addItem(requireItem(value, itemId).withQuote(unitPrice)));
    }
    @Override public void remove(String itemId, ActionCallback callback) {
        mutate(callback, value -> value.removeItem(itemId));
    }
    @Override public void clear(ActionCallback callback) {
        if (!requireOnline(callback)) return;
        firestore.collection(CARTS).document(customerId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> failure(error, callback));
    }
    @Override public void refresh(ActionCallback callback) {
        if (!requireOnline(callback)) return;
        firestore.collection(CARTS).document(customerId).get()
                .addOnSuccessListener(snapshot -> {
                    cart.setValue(snapshot.exists() ? mapCart(snapshot)
                            : Cart.empty(CartOwnerType.CUSTOMER, customerId));
                    callback.onSuccess();
                }).addOnFailureListener(error -> failure(error, callback));
    }

    private void mutate(ActionCallback callback, CartMutation mutation) {
        if (!requireOnline(callback)) return;
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(firestore.collection(CARTS).document(customerId));
            Cart value = snapshot.exists() ? mapCart(snapshot)
                    : Cart.empty(CartOwnerType.CUSTOMER, customerId);
            mutation.apply(value);
            Map<String, Object> data = cartMap(value);
            data.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(snapshot.getReference(), data); return value;
        }).addOnSuccessListener(value -> { cart.setValue(value); callback.onSuccess(); })
                .addOnFailureListener(error -> failure(error, callback));
    }

    private Cart mapCart(DocumentSnapshot snapshot) {
        List<CartItem> items = new ArrayList<>(); Object rawItems = snapshot.get("items");
        if (rawItems instanceof List<?>) for (Object raw : (List<?>) rawItems) {
            if (!(raw instanceof Map<?, ?>)) continue;
            Map<?, ?> item = (Map<?, ?>) raw;
            try {
                items.add(new CartItem(text(item.get("id")), text(item.get("productId")),
                        text(item.get("batchId")), text(item.get("sellerId")),
                        number(item.get("quantity")), number(item.get("unitPrice")),
                        Boolean.TRUE.equals(item.get("selected"))));
            } catch (IllegalArgumentException ignored) { }
        }
        Timestamp updated = snapshot.getTimestamp("updatedAt");
        return new Cart(snapshot.getId(), CartOwnerType.CUSTOMER, customerId, items,
                updated == null ? new Date() : updated.toDate());
    }
    private Map<String, Object> cartMap(Cart value) {
        Map<String, Object> data = new HashMap<>(); data.put("id", customerId);
        data.put("ownerType", CartOwnerType.CUSTOMER.name()); data.put("ownerKey", customerId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (CartItem item : value.getItems()) {
            Map<String, Object> map = new HashMap<>(); map.put("id", item.getId());
            map.put("productId", item.getProductId()); map.put("batchId", item.getBatchId());
            map.put("sellerId", item.getSellerId()); map.put("quantity", item.getQuantity());
            map.put("unitPrice", item.getUnitPrice()); map.put("selected", item.isSelected());
            items.add(map);
        }
        data.put("items", items); return data;
    }
    private CartItem requireItem(Cart value, String itemId) {
        CartItem item = find(value, itemId);
        if (item == null) throw new IllegalStateException("NOT_FOUND"); return item;
    }
    private CartItem find(Cart value, String itemId) {
        for (CartItem item : value.getItems()) if (item.getId().equals(itemId)) return item;
        return null;
    }
    private boolean requireOnline(ActionCallback callback) {
        if (network.isOnline()) return true;
        callback.onError(ErrorCode.NETWORK, "Giỏ customer chưa đồng bộ: thiết bị đang offline."); return false;
    }
    private void failure(Exception error, ActionCallback callback) {
        android.util.Log.e("FirebaseCartRepository", "Cart operation failed: " + error.getMessage(), error);
        String message = error.getMessage();
        if ("NOT_FOUND".equals(message)) { callback.onError(ErrorCode.NOT_FOUND, "Item không còn trong giỏ."); return; }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) error).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                callback.onError(ErrorCode.FORBIDDEN, "Không có quyền truy cập giỏ customer."); return;
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                callback.onError(ErrorCode.NETWORK, "Không thể đồng bộ giỏ customer."); return;
            }
        }
        callback.onError(ErrorCode.UNKNOWN, "Không thể cập nhật giỏ customer.");
    }
    private static String text(Object value) { return value instanceof String ? (String) value : ""; }
    private static double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : 0D; }
    @Override public void close() { listener.remove(); }
    private interface CartMutation { void apply(Cart cart); }
}
