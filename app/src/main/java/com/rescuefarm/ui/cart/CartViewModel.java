package com.rescuefarm.ui.cart;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.CartOwnerType;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Promotion;
import com.rescuefarm.service.cart.CartGroupingService;
import com.rescuefarm.service.cart.CartRevalidationService;
import com.rescuefarm.service.pricing.PricingService;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import com.rescuefarm.service.backend.RenderBackendClient;
import java.util.concurrent.atomic.AtomicInteger;

public class CartViewModel extends ViewModel {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final LiveData<Cart> cart;
    private final PricingService pricingService = new PricingService();
    private final CartGroupingService groupingService = new CartGroupingService();
    private final CartRevalidationService revalidationService = new CartRevalidationService(pricingService);
    private final MutableLiveData<CartScreenState> state = new MutableLiveData<>(CartScreenState.idle());
    private final MutableLiveData<Map<String, String>> sellerNames = new MutableLiveData<>(new HashMap<>());
    private final Set<String> requestedSellerNames = new HashSet<>();

    public CartViewModel(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository; this.productRepository = productRepository;
        this.cart = cartRepository.observeCart();
    }
    public LiveData<Cart> getCart() { return cart; }
    public LiveData<List<Product>> getProducts() { return productRepository.observeProducts(); }
    public LiveData<CartScreenState> getState() { return state; }
    public LiveData<Map<String, String>> getSellerNames() { return sellerNames; }

    public void loadSellerNames(Set<String> sellerIds) {
        List<String> missing = new ArrayList<>();
        for (String id : sellerIds) {
            if (id != null && !id.trim().isEmpty() && requestedSellerNames.add(id)) missing.add(id);
        }
        for (int start = 0; start < missing.size(); start += 50) {
            List<String> batch = new ArrayList<>(missing.subList(start, Math.min(start + 50, missing.size())));
            JSONObject body = new JSONObject();
            try { body.put("sellerIds", new JSONArray(batch)); }
            catch (org.json.JSONException error) { requestedSellerNames.removeAll(batch); continue; }
            RenderBackendClient.postGuest("/sellers/names", body, new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    Map<String, String> names = new HashMap<>(sellerNames.getValue());
                    JSONObject values = response.optJSONObject("sellerNames");
                    if (values != null) for (String id : batch) {
                        String name = values.optString(id, "").trim();
                        if (!name.isEmpty()) names.put(id, name);
                    }
                    sellerNames.setValue(names);
                }
                @Override public void onError(String message) {
                    // Retry on the next cart refresh; never expose an internal ID as a name.
                    requestedSellerNames.removeAll(batch);
                }
            });
        }
    }
    public boolean isGuestCart() { return cartRepository.getOwnerType() == CartOwnerType.GUEST; }
    public Map<String, List<CartItem>> groupSelectedBySeller(Cart cart) {
        return groupingService.groupSelectedBySeller(cart);
    }

    public void increaseQuantity(CartItem item, double step) {
        if (item == null) return;
        updateQuantity(item.getId(), Math.round((item.getQuantity() + step) * 10.0) / 10.0);
    }

    public void decreaseQuantity(CartItem item, double step) {
        if (item == null) return;
        double newQuantity = Math.round((item.getQuantity() - step) * 10.0) / 10.0;
        if (newQuantity <= 0.0) {
            remove(item.getId());
        } else {
            updateQuantity(item.getId(), newQuantity);
        }
    }

    public void add(Product product, ProductBatch batch, double quantity, double displayedUnitPrice) {
        if (product == null || batch == null || !Double.isFinite(quantity) || quantity <= 0D) {
            state.setValue(CartScreenState.of(CartScreenState.Status.ERROR, "Sản phẩm, batch hoặc số lượng không hợp lệ.")); return;
        }
        if (!batch.isSellable(new Date()) || !batch.hasAvailableStock(quantity)) {
            state.setValue(CartScreenState.of(CartScreenState.Status.ERROR, "Batch đã hết hạn hoặc không đủ tồn kho.")); return;
        }
        try {
            CartItem item = new CartItem(CartItem.itemId(product.getId(), batch.getId()), product.getId(),
                    batch.getId(), product.getSellerId(), quantity, displayedUnitPrice);
            double combinedQuantity = quantity;
            Cart current = cart.getValue();
            if (current != null) for (CartItem existing : current.getItems()) {
                if (existing.getId().equals(item.getId())) combinedQuantity += existing.getQuantity();
            }
            CartItem validationItem = item.withQuantity(combinedQuantity);
            productRepository.getCartQuote(product.getId(), batch.getId(),
                    new ProductRepository.CartQuoteCallback() {
                @Override public void onSuccess(Product latestProduct, ProductBatch latestBatch,
                        Promotion promotion) {
                    CartRevalidationService.Result result = revalidationService.revalidate(
                            validationItem, latestProduct, latestBatch, promotion, new Date());
                    if (!result.isValid()) {
                        state.postValue(CartScreenState.of(CartScreenState.Status.ERROR,
                                result.getMessage())); return;
                    }
                    cartRepository.addOrReplace(item.withQuote(result.getQuote().getFinalUnitPrice()),
                            action("Đã thêm vào giỏ với giá/tồn kho mới nhất."));
                }
                @Override public void onError(ProductRepository.ErrorCode error, String message) {
                    if (isGuestCart() && (error == ProductRepository.ErrorCode.NOT_CONFIGURED
                            || error == ProductRepository.ErrorCode.NETWORK)) {
                        cartRepository.addOrReplace(item,
                                action("Đã lưu guest cart local; sẽ revalidate khi có mạng."));
                    } else state.postValue(CartScreenState.of(CartScreenState.Status.ERROR, message));
                }
            });
        } catch (IllegalArgumentException error) {
            state.setValue(CartScreenState.of(CartScreenState.Status.ERROR, error.getMessage()));
        }
    }
    public void updateQuantity(String itemId, double quantity) {
        Cart current = cart.getValue(); CartItem existing = null;
        if (current != null) for (CartItem item : current.getItems()) if (item.getId().equals(itemId)) {
            existing = item; break;
        }
        if (existing == null) {
            state.setValue(CartScreenState.of(CartScreenState.Status.ERROR, "Không tìm thấy item trong giỏ.")); return;
        }
        if (!Double.isFinite(quantity) || quantity <= 0D) {
            state.setValue(CartScreenState.of(CartScreenState.Status.ERROR, "Số lượng phải là số dương.")); return;
        }
        CartItem candidate = existing.withQuantity(quantity);
        productRepository.getCartQuote(existing.getProductId(), existing.getBatchId(),
                new ProductRepository.CartQuoteCallback() {
            @Override public void onSuccess(Product product, ProductBatch batch, Promotion promotion) {
                CartRevalidationService.Result result = revalidationService.revalidate(
                        candidate, product, batch, promotion, new Date());
                if (!result.isValid()) {
                    state.postValue(CartScreenState.of(CartScreenState.Status.ERROR, result.getMessage())); return;
                }
                cartRepository.replace(candidate.withQuote(result.getQuote().getFinalUnitPrice()),
                        action("Đã cập nhật và revalidate số lượng."));
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                if (isGuestCart() && (error == ProductRepository.ErrorCode.NOT_CONFIGURED
                        || error == ProductRepository.ErrorCode.NETWORK)) {
                    cartRepository.updateQuantity(itemId, quantity,
                            action("Đã cập nhật local; sẽ revalidate khi có mạng."));
                } else state.postValue(CartScreenState.of(CartScreenState.Status.ERROR, message));
            }
        });
    }
    public void setSelected(String itemId, boolean selected) {
        cartRepository.setSelected(itemId, selected, action(null));
    }
    public void remove(String itemId) { cartRepository.remove(itemId, action("Đã xóa item.")); }
    public void clear() { cartRepository.clear(action("Đã xóa toàn bộ giỏ.")); }

    public void refreshAndRevalidate() {
        state.setValue(CartScreenState.of(CartScreenState.Status.LOADING, "Đang đồng bộ giỏ…"));
        cartRepository.refresh(new CartRepository.ActionCallback() {
            @Override public void onSuccess() {
                state.postValue(CartScreenState.of(isGuestCart() ? CartScreenState.Status.LOCAL_READY
                        : CartScreenState.Status.SYNCED, isGuestCart()
                        ? "Giỏ guest được lưu cục bộ trên thiết bị."
                        : "Giỏ customer đã đồng bộ Firestore."));
                revalidateCurrentCart();
            }
            @Override public void onError(CartRepository.ErrorCode error, String message) {
                state.postValue(CartScreenState.of(error == CartRepository.ErrorCode.NETWORK
                        ? CartScreenState.Status.OFFLINE : CartScreenState.Status.ERROR, message));
            }
        });
    }

    public void revalidateCurrentCart() {
        Cart cart = getCart().getValue();
        if (cart == null || cart.getItems().isEmpty()) return;
        AtomicInteger remaining = new AtomicInteger(cart.getItems().size());
        AtomicInteger issues = new AtomicInteger(); AtomicInteger changed = new AtomicInteger();
        for (CartItem item : cart.getItems()) {
            productRepository.getCartQuote(item.getProductId(), item.getBatchId(),
                    new ProductRepository.CartQuoteCallback() {
                @Override public void onSuccess(Product product, ProductBatch batch, Promotion promotion) {
                    CartRevalidationService.Result result = revalidationService.revalidate(
                            item, product, batch, promotion, new Date());
                    if (!result.isValid()) {
                        issues.incrementAndGet(); finishOne(remaining, issues, changed); return;
                    }
                    if (!result.isPriceChanged()) {
                        finishOne(remaining, issues, changed); return;
                    }
                    changed.incrementAndGet();
                    cartRepository.updateQuote(item.getId(), result.getQuote().getFinalUnitPrice(),
                            new CartRepository.ActionCallback() {
                        @Override public void onSuccess() { finishOne(remaining, issues, changed); }
                        @Override public void onError(CartRepository.ErrorCode error, String message) {
                            issues.incrementAndGet(); finishOne(remaining, issues, changed);
                        }
                    });
                }
                @Override public void onError(ProductRepository.ErrorCode error, String message) {
                    issues.incrementAndGet(); finishOne(remaining, issues, changed);
                }
            });
        }
    }

    private void finishOne(AtomicInteger remaining, AtomicInteger issues, AtomicInteger changed) {
        if (remaining.decrementAndGet() != 0) return;
        String message = "Đã revalidate giá/tồn kho: " + changed.get() + " giá thay đổi, "
                + issues.get() + " item cần kiểm tra.";
        state.postValue(CartScreenState.of(CartScreenState.Status.REVALIDATED, message));
    }
    private CartRepository.ActionCallback action(String success) {
        return new CartRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (success != null) state.postValue(CartScreenState.of(CartScreenState.Status.SAVED, success));
            }
            @Override public void onError(CartRepository.ErrorCode error, String message) {
                state.postValue(CartScreenState.of(error == CartRepository.ErrorCode.NETWORK
                        ? CartScreenState.Status.OFFLINE : CartScreenState.Status.ERROR, message));
            }
        };
    }
    @Override protected void onCleared() {
        cartRepository.close(); productRepository.close(); super.onCleared();
    }
}
