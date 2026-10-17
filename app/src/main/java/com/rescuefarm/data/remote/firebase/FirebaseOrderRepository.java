package com.rescuefarm.data.remote.firebase;

import android.os.Handler;
import android.os.Looper;
import com.rescuefarm.data.local.preferences.SharedPreferencesGuestSessionRepository;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;
import org.json.JSONArray;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.data.local.dao.OrderCacheDao;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.OrderCacheEntity;
import com.rescuefarm.data.local.mapper.OrderCacheMapper;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderOwnerType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import com.rescuefarm.domain.enums.ShipmentStatus;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.model.Shipment;
import com.rescuefarm.service.inventory.InventoryService;
import com.rescuefarm.service.network.NetworkStatusProvider;
import com.rescuefarm.service.order.CheckoutPolicy;
import com.rescuefarm.service.order.CheckoutRequest;
import com.rescuefarm.service.order.OrderLifecycleService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class FirebaseOrderRepository implements OrderRepository {
    private static final String ORDERS="orders", ITEMS="items",
            BATCHES="productBatches", CAMPAIGNS="campaigns",
            PAYMENTS="payments", SHIPMENTS="shipments";
    private final FirebaseFirestore db=FirebaseFirestore.getInstance();
    private final FirebaseAuth auth=FirebaseAuth.getInstance(); private final NetworkStatusProvider network;
    private final RescueFarmDatabase database; private final OrderCacheDao orderDao;
    private final ExecutorService cacheExecutor;
    private final Map<String, ListenerRegistration> orderDetailListeners = new HashMap<>();
    private final Map<String, DocumentSnapshot> sellerOrderCursors = new HashMap<>();
    private final Map<String, Boolean> sellerOrderHasMore = new HashMap<>();
    private final Map<String, ListenerRegistration> sellerOrderListeners = new HashMap<>();
    private static final int SELLER_ORDER_PAGE_SIZE = 50;
    private final SharedPreferencesGuestSessionRepository guestSession;
    private final Handler guestHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> guestPolls = new HashMap<>();
    private final InventoryService inventory=new InventoryService();
    private final OrderLifecycleService lifecycle=new OrderLifecycleService();
    public FirebaseOrderRepository(RescueFarmDatabase database, ExecutorService cacheExecutor,
            NetworkStatusProvider network, SharedPreferencesGuestSessionRepository guestSession){this.database=database;this.orderDao=database.orderCacheDao();
        this.cacheExecutor=cacheExecutor;this.network=network;this.guestSession=guestSession;}
    @Override public LiveData<List<Order>> observeOrders(String ownerId){
        MediatorLiveData<List<Order>> result = new MediatorLiveData<>();
        result.addSource(Transformations.map(orderDao.observeOwnerOrders(ownerId),
                OrderCacheMapper::orders), result::setValue);
        LiveData<List<Order>> remote = new LiveData<List<Order>>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                if (ownerId == null || ownerId.trim().isEmpty()) {
                    postValue(new ArrayList<>());
                    return;
                }
                registration = db.collection(ORDERS).whereEqualTo("ownerId", ownerId)
                        .whereEqualTo("ownerType", OrderOwnerType.CUSTOMER.name())
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            List<Order> values = new ArrayList<>();
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                values.add(mapOrder(document, new ArrayList<>()));
                            }
                            postValue(values);
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
        result.addSource(remote, values -> {
            if (values == null) return;
            result.setValue(values);
            cacheExecutor.execute(() -> database.runInTransaction(() -> {
                orderDao.clearOwnerOrders(ownerId);
                orderDao.replaceOrders(toEntities(values));
            }));
        });
        return result;
    }
    @Override public LiveData<List<Order>> observeSellerOrders(String sellerId){
        MediatorLiveData<List<Order>> result = new MediatorLiveData<>();
        result.addSource(Transformations.map(orderDao.observeSellerOrders(sellerId), OrderCacheMapper::orders), result::setValue);
        LiveData<List<Order>> remote = new LiveData<List<Order>>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                if (sellerId == null || sellerId.trim().isEmpty()) { postValue(new ArrayList<>()); return; }
                registration = db.collection(ORDERS).whereEqualTo("sellerId", sellerId)
                        .orderBy("createdAt", Query.Direction.DESCENDING).limit(SELLER_ORDER_PAGE_SIZE)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            if (!snapshot.getDocuments().isEmpty()) {
                                synchronized (sellerOrderCursors) {
                                  if (!sellerOrderCursors.containsKey(sellerId)) {
                                    sellerOrderCursors.put(sellerId, snapshot.getDocuments().get(snapshot.size() - 1));
                                    sellerOrderHasMore.put(sellerId, snapshot.size() == SELLER_ORDER_PAGE_SIZE);
                                  }
                                }
                            }
                            List<Order> values = new ArrayList<>();
                            for (DocumentSnapshot document : snapshot.getDocuments()) values.add(mapOrder(document, new ArrayList<>()));
                            postValue(values);
                            cacheExecutor.execute(() -> orderDao.replaceOrders(toEntities(values)));
                        });
                sellerOrderListeners.put(sellerId, registration);
            }
            @Override protected void onInactive() {
                if (registration != null) registration.remove();
                if (sellerId != null) sellerOrderListeners.remove(sellerId);
                registration = null;
            }
        };
        result.addSource(remote, unused -> { });
        return result;
    }

    @Override public void checkout(CheckoutRequest request, OrderCallback callback) {
        if (!network.isOnline()) { callback.onError(ErrorCode.NETWORK, "Đặt hàng cần kết nối mạng."); return; }
        try {
            CheckoutPolicy.requireSingleSeller(request.items); validateRequest(request);
            boolean guest = request.ownerType == OrderOwnerType.GUEST;
            JSONObject data = new JSONObject().put("requestId", request.requestId)
                    .put("receiverName", request.receiverName).put("receiverPhone", request.receiverPhone)
                    .put("receiverAddress", request.receiverAddress).put("latitude", request.latitude)
                    .put("longitude", request.longitude).put("fulfillmentType", request.fulfillmentType.name())
                    .put("paymentMethod", request.paymentMethod.name()).put("note", request.note);
            JSONArray items = new JSONArray();
            for (CartItem item : request.items) items.put(new JSONObject().put("productId", item.getProductId())
                    .put("batchId", item.getBatchId()).put("sellerId", item.getSellerId()).put("quantity", item.getQuantity()));
            data.put("items", items);
            if (Double.isFinite(request.expectedSubtotal)) data.put("expectedSubtotal", request.expectedSubtotal);
            if (Double.isFinite(request.expectedShippingFee)) data.put("expectedShippingFee", request.expectedShippingFee);
            if (guest) {
                if (!guestSession.getOrCreateGuestId().equals(request.ownerId))
                    throw new IllegalArgumentException("Phiên Guest đã thay đổi.");
                data.put("guestId", request.ownerId).put("guestToken", guestSession.getOrCreateGuestToken());
            }
            RenderBackendClient.Callback responseCallback = new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    try {
                        Order order = OrderJsonMapper.order(response.optJSONObject("order"));
                        boolean replay = response.optBoolean("replay");
                        if (guest) guestSession.saveOrderAccessToken(order.getId(), response.optString("accessToken"));
                        cacheExecutor.execute(() -> {
                            if (guest) consumeGuestCartOnce(request);
                            orderDao.replaceOrder(OrderCacheMapper.toEntity(order, System.currentTimeMillis()));
                            callback.onSuccess(order, replay);
                        });
                    } catch (Exception invalid) { callback.onError(ErrorCode.UNKNOWN, "Không đọc được kết quả đặt hàng. Hãy thử lại cùng yêu cầu."); }
                }
                @Override public void onError(String message) {
                    callback.onError(message != null && message.contains("QUOTE_CHANGED") ? ErrorCode.CONFLICT
                            : message != null && message.startsWith("HTTP 4") ? ErrorCode.VALIDATION : ErrorCode.NETWORK, message);
                }
            };
            if (guest) RenderBackendClient.postGuest("/checkoutGuest", data, responseCallback);
            else RenderBackendClient.post("/checkoutCustomer", data, responseCallback);
        } catch (Exception invalid) { callback.onError(ErrorCode.VALIDATION, invalid.getMessage()); }
    }

    private void consumeGuestCartOnce(CheckoutRequest request) {
        database.runInTransaction(() -> {
            String receiptKey = "guest-checkout:" + request.ownerId + ":" + request.requestId;
            if (database.cacheMetadataDao().find(receiptKey) != null) return;
            for (CartItem purchased : request.items) {
                com.rescuefarm.data.local.entity.GuestCartItemEntity existing =
                        database.guestCartDao().findItem(request.ownerId, purchased.getId());
                if (existing == null) continue;
                double remaining = existing.quantity - purchased.getQuantity();
                if (remaining <= 0.000001) database.guestCartDao().deleteById(request.ownerId, purchased.getId());
                else { existing.quantity = remaining; database.guestCartDao().upsert(existing); }
            }
            com.rescuefarm.data.local.entity.CacheMetadataEntity receipt =
                    new com.rescuefarm.data.local.entity.CacheMetadataEntity(receiptKey);
            receipt.lastSuccessAtEpochMillis = System.currentTimeMillis();
            database.cacheMetadataDao().upsert(receipt);
        });
    }


    @Override public void refreshOrders(String ownerId, ActionCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Không thể tải đơn khi offline.");return;}
        db.collection(ORDERS).whereEqualTo("ownerId",ownerId)
                .whereEqualTo("ownerType",OrderOwnerType.CUSTOMER.name()).get().addOnSuccessListener(s->{
            List<Order> values=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())values.add(mapOrder(d,new ArrayList<>()));
            cacheExecutor.execute(()->{database.runInTransaction(()->{orderDao.clearOwnerOrders(ownerId);
                orderDao.replaceOrders(toEntities(values));});callback.onSuccess();});}).addOnFailureListener(e->failure(e,callback));
    }
    @Override public void refreshSellerOrders(String sellerId,ActionCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Không thể tải đơn seller khi offline.");return;}
        db.collection(ORDERS).whereEqualTo("sellerId",sellerId).orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(SELLER_ORDER_PAGE_SIZE).get().addOnSuccessListener(s->{
            List<Order> values=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())values.add(mapOrder(d,new ArrayList<>()));
            synchronized (sellerOrderCursors) {
                sellerOrderHasMore.put(sellerId, s.size() == SELLER_ORDER_PAGE_SIZE);
                if (s.isEmpty()) sellerOrderCursors.remove(sellerId);
                else sellerOrderCursors.put(sellerId, s.getDocuments().get(s.size() - 1));
            }
            cacheExecutor.execute(()->{database.runInTransaction(()->{orderDao.clearSellerOrders(sellerId);
                orderDao.replaceOrders(toEntities(values));});callback.onSuccess();});}).addOnFailureListener(e->failure(e,callback));
    }
    @Override public void loadMoreSellerOrders(String sellerId, ActionCallback callback) {
        if (!network.isOnline()) { callback.onError(ErrorCode.NETWORK, "Tải thêm đơn cần kết nối mạng."); return; }
        DocumentSnapshot cursor; boolean hasMore;
        synchronized (sellerOrderCursors) { cursor = sellerOrderCursors.get(sellerId); hasMore = Boolean.TRUE.equals(sellerOrderHasMore.get(sellerId)); }
        if (!hasMore || cursor == null) { callback.onSuccess(); return; }
        db.collection(ORDERS).whereEqualTo("sellerId", sellerId).orderBy("createdAt", Query.Direction.DESCENDING)
                .startAfter(cursor).limit(SELLER_ORDER_PAGE_SIZE).get().addOnSuccessListener(snapshot -> {
                    List<Order> values = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) values.add(mapOrder(document, new ArrayList<>()));
                    synchronized (sellerOrderCursors) {
                        sellerOrderHasMore.put(sellerId, snapshot.size() == SELLER_ORDER_PAGE_SIZE);
                        if (!snapshot.isEmpty()) sellerOrderCursors.put(sellerId, snapshot.getDocuments().get(snapshot.size() - 1));
                    }
                    cacheExecutor.execute(() -> { orderDao.replaceOrders(toEntities(values)); callback.onSuccess(); });
                }).addOnFailureListener(error -> failure(error, callback));
    }
    @Override public boolean hasMoreSellerOrders(String sellerId) {
        synchronized (sellerOrderCursors) { return Boolean.TRUE.equals(sellerOrderHasMore.get(sellerId)); }
    }
    @Override public void findGuestOrder(String code,String phone,OrderCallback callback){
        try {
            JSONObject request = new JSONObject().put("orderCode", clean(code))
                    .put("receiverPhone", clean(phone));
            RenderBackendClient.postGuest("/lookupGuestOrder", request, new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    try {
                        Order order = OrderJsonMapper.order(response.optJSONObject("order"));
                        guestSession.saveOrderAccessToken(order.getId(), response.optString("accessToken"));
                        cache(order); callback.onSuccess(order, false);
                    } catch (Exception invalid) { callback.onError(ErrorCode.UNKNOWN, "Không đọc được đơn Guest từ máy chủ."); }
                }
                @Override public void onError(String message) { callback.onError(ErrorCode.NOT_FOUND, message); }
            });
        } catch (Exception invalid) { callback.onError(ErrorCode.VALIDATION, "Kiểm tra mã đơn và số điện thoại."); }
    }

    @Override public void getOrderItems(String orderId, ItemCallback callback) {
        if (hasGuestAccess(orderId)) {
            getGuestOrder(orderId, new LifecycleCallback() {
                public void onSuccess(Order order, Payment p, Shipment s, boolean replay) { callback.success(order.getItems()); }
                public void onError(ErrorCode error, String message) { callback.error(message); }
            }); return;
        }
        db.collection(ORDERS).document(orderId).collection(ITEMS).get()
            .addOnSuccessListener(snapshot -> {
                List<OrderItem> items = new ArrayList<>();
                for (DocumentSnapshot d : snapshot.getDocuments()) items.add(mapOrderItem(d));
                callback.success(items);
            }).addOnFailureListener(e -> callback.error("Không tải được mặt hàng của đơn."));
    }
    @Override public void getOrder(String orderId,LifecycleCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Cần kết nối mạng để tải trạng thái đơn.");return;}
        if (hasGuestAccess(orderId)) { getGuestOrder(orderId, callback); return; }
        fetchLifecycle(clean(orderId),false,callback);
    }

    @Override public void observeOrder(String orderId, LifecycleCallback callback) {
        String id = clean(orderId);
        if (id.isEmpty()) { callback.onError(ErrorCode.VALIDATION, "Mã đơn hàng không hợp lệ."); return; }
        if (hasGuestAccess(id)) {
            stopObservingOrder(id);
            Runnable poll = new Runnable() {
                @Override public void run() {
                    if (guestPolls.get(id) != this) return;
                    Runnable currentPoll = this;
                    getGuestOrder(id, new LifecycleCallback() {
                        public void onSuccess(Order order, Payment p, Shipment s, boolean replay) {
                            if (guestPolls.get(id) != currentPoll) return;
                            callback.onSuccess(order, p, s, replay); guestHandler.postDelayed(currentPoll, 15000L);
                        }
                        public void onError(ErrorCode error, String message) {
                            if (guestPolls.get(id) != currentPoll) return;
                            callback.onError(error, message); guestHandler.postDelayed(currentPoll, 30000L);
                        }
                    });
                }
            };
            guestPolls.put(id, poll); poll.run(); return;
        }
        ListenerRegistration old = orderDetailListeners.remove(id);
        if (old != null) old.remove();
        ListenerRegistration listener = db.collection(ORDERS).document(id).addSnapshotListener((snapshot, error) -> {
            if (error != null) { failure(error, callback); return; }
            if (snapshot == null || !snapshot.exists()) { callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy đơn."); return; }
            fetchLifecycle(id, false, callback);
        });
        orderDetailListeners.put(id, listener);
    }

    @Override
    public void stopObservingOrder(String orderId) {
        Runnable poll = guestPolls.remove(clean(orderId));
        if (poll != null) guestHandler.removeCallbacks(poll);
        ListenerRegistration listener = orderDetailListeners.remove(clean(orderId));
        if (listener != null) listener.remove();
    }

    private boolean hasGuestAccess(String orderId) {
        return !guestSession.getOrderAccessToken(clean(orderId)).isEmpty();
    }

    private void getGuestOrder(String orderId, LifecycleCallback callback) {
        try {
            JSONObject request = new JSONObject().put("orderId", clean(orderId))
                    .put("accessToken", guestSession.getOrderAccessToken(clean(orderId)));
            RenderBackendClient.postGuest("/guestOrderDetails", request, new RenderBackendClient.Callback() {
                public void onSuccess(JSONObject response) {
                    try {
                        Order order = OrderJsonMapper.order(response.optJSONObject("order"));
                        Payment payment = OrderJsonMapper.payment(response.optJSONObject("payment"));
                        Shipment shipment = OrderJsonMapper.shipment(response.optJSONObject("shipment"));
                        cache(order); callback.onSuccess(order, payment, shipment, false);
                    } catch (Exception invalid) { callback.onError(ErrorCode.UNKNOWN, "Không tải đủ thông tin đơn Guest."); }
                }
                public void onError(String message) { callback.onError(ErrorCode.FORBIDDEN, message); }
            });
        } catch (Exception invalid) { callback.onError(ErrorCode.VALIDATION, "Hãy tra cứu lại đơn Guest."); }
    }

    @Override public void updateShipmentDetails(String orderId, String carrierName,
            String trackingCode, ActionCallback callback) {
        if (!network.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Cần kết nối mạng để cập nhật thông tin vận chuyển.");
            return;
        }
        String id = clean(orderId);
        String carrier = clean(carrierName);
        String tracking = clean(trackingCode);
        if (id.isEmpty() || carrier.isEmpty() || carrier.length() > 120
                || tracking.isEmpty() || tracking.length() > 120) {
            callback.onError(ErrorCode.VALIDATION, "Hãng vận chuyển và mã vận đơn là bắt buộc (tối đa 120 ký tự).");
            return;
        }
        DocumentReference orderRef = db.collection(ORDERS).document(id);
        DocumentReference shipmentRef = db.collection(SHIPMENTS).document(id);
        db.runTransaction(tx -> {
            DocumentSnapshot orderDoc = tx.get(orderRef);
            DocumentSnapshot shipmentDoc = tx.get(shipmentRef);
            if (!orderDoc.exists() || !shipmentDoc.exists()) throw new IllegalStateException("NOT_FOUND");
            String sellerId = clean(orderDoc.getString("sellerId"));
            String currentUser = FirebaseAuth.getInstance().getCurrentUser() == null ? ""
                    : FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (!currentUser.equals(sellerId)
                    || !FulfillmentType.DELIVERY.name().equals(orderDoc.getString("fulfillmentType"))) {
                throw new IllegalStateException("FORBIDDEN");
            }
            OrderStatus status = enumValue(OrderStatus.class, orderDoc.getString("status"), OrderStatus.PENDING);
            if (status != OrderStatus.CONFIRMED && status != OrderStatus.PREPARING
                    && status != OrderStatus.SHIPPING) throw new IllegalStateException("SHIPMENT_LOCKED");
            tx.update(shipmentRef, "carrierName", carrier, "trackingCode", tracking,
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    String reason = error.getMessage() == null ? "" : error.getMessage();
                    if (reason.contains("SHIPMENT_LOCKED")) {
                        callback.onError(ErrorCode.CONFLICT, "Chỉ được sửa vận đơn khi đơn đang xử lý giao hàng.");
                    } else if (reason.contains("NOT_FOUND")) {
                        callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hoặc shipment.");
                    } else if (reason.contains("FORBIDDEN") || (error instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) error).getCode()
                            == FirebaseFirestoreException.Code.PERMISSION_DENIED)) {
                        callback.onError(ErrorCode.FORBIDDEN, "Bạn không có quyền sửa shipment này.");
                    } else {
                        callback.onError(ErrorCode.UNKNOWN, "Không thể lưu thông tin vận chuyển.");
                    }
                });
    }

    @Override public void transitionOrder(String orderId,OrderStatus target,LifecycleCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Cập nhật trạng thái cần kết nối mạng.");return;}
        if(clean(orderId).isEmpty()||target==null){callback.onError(ErrorCode.VALIDATION,"Đơn hoặc trạng thái không hợp lệ.");return;}
        try {
            JSONObject request = new JSONObject().put("orderId", orderId).put("targetStatus", target.name());
            RenderBackendClient.post("/orders/transition", request, new RenderBackendClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    fetchLifecycle(orderId, response.optBoolean("replay"), callback);
                }
                @Override public void onError(String message) {
                    callback.onError(ErrorCode.CONFLICT, message);
                }
            });
        } catch (org.json.JSONException error) {
            callback.onError(ErrorCode.VALIDATION, "Không tạo được yêu cầu cập nhật đơn.");
        }
    }

    @Override public void confirmBankTransfer(String orderId,String referenceCode,LifecycleCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Xác nhận chuyển khoản cần kết nối mạng.");return;}
        String reference=clean(referenceCode);
        if(clean(orderId).isEmpty()||reference.length()<3){callback.onError(ErrorCode.VALIDATION,"Cần mã tham chiếu chuyển khoản.");return;}
        DocumentReference orderRef=db.collection(ORDERS).document(orderId),paymentRef=db.collection(PAYMENTS).document(orderId);
        db.runTransaction(tx->{DocumentSnapshot order=tx.get(orderRef),payment=tx.get(paymentRef);
            if(!order.exists())throw new IllegalStateException("ORDER_NOT_FOUND");
            if(!payment.exists())throw new IllegalStateException("PAYMENT_NOT_FOUND");
            if(!PaymentMethod.BANK_TRANSFER.name().equals(payment.getString("method")))throw new IllegalStateException("NOT_BANK_TRANSFER");
            if(PaymentStatus.PAID.name().equals(payment.getString("status")))return true;
            if(!PaymentStatus.PENDING.name().equals(payment.getString("status"))
                    ||OrderStatus.CANCELLED.name().equals(order.getString("status")))throw new IllegalStateException("PAYMENT_NOT_CONFIRMABLE");
            tx.update(paymentRef,"status",PaymentStatus.PAID.name(),"referenceCode",reference,
                    "paidAt",FieldValue.serverTimestamp(),"updatedAt",FieldValue.serverTimestamp());
            tx.update(orderRef,"paymentStatus",PaymentStatus.PAID.name(),"updatedAt",FieldValue.serverTimestamp());com.rescuefarm.service.notification.NotificationOutbox.enqueue(tx,db,"ORDER_PAYMENT","ORDER",orderId);return false;
        }).addOnSuccessListener(replay->fetchLifecycle(orderId,replay,callback))
                .addOnFailureListener(e->failure(e,callback));
    }

    private void fetchLifecycle(String orderId,boolean replay,LifecycleCallback callback){
        db.collection(ORDERS).document(orderId).get().addOnSuccessListener(orderDoc->{
            if(!orderDoc.exists()){callback.onError(ErrorCode.NOT_FOUND,"Không tìm thấy đơn.");return;}
            db.collection(PAYMENTS).document(orderId).get().addOnSuccessListener(paymentDoc->{
                if(!paymentDoc.exists()){callback.onError(ErrorCode.CONFLICT,"Đơn chưa có payment record.");return;}
                db.collection(ORDERS).document(orderId).collection(ITEMS).get().addOnSuccessListener(itemDocs -> {
                    List<OrderItem> orderItems = new ArrayList<>();
                    for (DocumentSnapshot itemDoc : itemDocs.getDocuments()) orderItems.add(mapOrderItem(itemDoc));
                    Order order=mapOrder(orderDoc,orderItems);Payment payment=mapPayment(paymentDoc);
                    cache(order);if(!lifecycle.requiresShipment(order.getFulfillmentType())){callback.onSuccess(order,payment,null,replay);return;}
                    db.collection(SHIPMENTS).document(orderId).get().addOnSuccessListener(shipmentDoc->{
                        if(!shipmentDoc.exists()){callback.onError(ErrorCode.CONFLICT,"Đơn giao hàng chưa có shipment record.");return;}
                        callback.onSuccess(order,payment,mapShipment(shipmentDoc),replay);
                    }).addOnFailureListener(e->failure(e,callback));
                }).addOnFailureListener(e->failure(e,callback));
            }).addOnFailureListener(e->failure(e,callback));
        }).addOnFailureListener(e->failure(e,callback));
    }

    private void writeOrderNotification(com.google.firebase.firestore.Transaction tx, String orderId, String uid, OrderStatus status) {
        String id = orderId + "_" + status.name();
        Map<String,Object> data = new HashMap<>();
        data.put("id",id); data.put("userId",uid); data.put("type","ORDER"); data.put("referenceId",orderId);
        data.put("orderStatus",status.name());
        data.put("title","Cập nhật đơn hàng");
        String[] labels={"Chờ xác nhận","Đã xác nhận","Đang chuẩn bị","Sẵn sàng nhận hàng","Đang giao","Đã giao","Đã hủy"};
        data.put("body", "Trạng thái đơn hàng: " + labels[status.ordinal()]);
        data.put("isRead",false); data.put("createdAt",FieldValue.serverTimestamp());
        tx.set(db.collection("notifications").document(id),data);
    }
    private void validateRequest(CheckoutRequest r){if(r==null||clean(r.requestId).isEmpty()||r.ownerType==null||clean(r.ownerId).isEmpty()
            ||clean(r.receiverName).length()<2||clean(r.receiverPhone).length()<9||r.fulfillmentType==null||r.paymentMethod==null)
        throw new IllegalArgumentException("Thông tin checkout chưa hợp lệ.");
        lifecycle.requirePaymentCompatible(r.fulfillmentType,r.paymentMethod);
        String uid=auth.getCurrentUser()==null?"":auth.getCurrentUser().getUid();
        if(r.ownerType==OrderOwnerType.CUSTOMER&&!r.ownerId.equals(uid))throw new IllegalArgumentException("Customer session không hợp lệ.");}
    private ProductBatch requireBatch(DocumentSnapshot d,String productId){if(!d.exists()||!productId.equals(d.getString("productId")))throw new IllegalStateException("BATCH_UNAVAILABLE");
        return ProductBatch.restore(d.getId(),productId,d.getString("activeCampaignId"),date(d,"harvestDate"),date(d,"expiryDate"),number(d,"initialQuantity"),
                number(d,"availableQuantity"),number(d,"reservedQuantity"),number(d,"soldQuantity"),enumValue(BatchStatus.class,d.getString("status"),BatchStatus.AVAILABLE),longValue(d,"inventoryVersion"));}
    private OrderItem mapOrderItem(DocumentSnapshot d){return OrderItem.snapshot(d.getId(),d.getString("orderId"),
            d.getString("productId"),d.getString("batchId"),d.getString("productNameSnapshot"),
            d.getString("productImageSnapshot"),d.getString("unitSnapshot"),number(d,"originalPriceSnapshot"),
            number(d,"rescuePriceSnapshot"),number(d,"finalPriceSnapshot"),number(d,"quantity"));}
    private Payment mapPayment(DocumentSnapshot d){Payment value=Payment.restore(d.getId(),d.getString("orderId"),enumValue(PaymentMethod.class,d.getString("method"),PaymentMethod.COD),enumValue(PaymentStatus.class,d.getString("status"),PaymentStatus.UNPAID),number(d,"amount"),d.getString("referenceCode"),date(d,"paidAt"));value.setBankDetails(d.getString("bankCode"),d.getString("bankAccountName"),d.getString("bankAccountNumber"));value.setQrUrl(d.getString("qrUrl"));return value;}
    private Shipment mapShipment(DocumentSnapshot d){return Shipment.restore(d.getId(),d.getString("orderId"),d.getString("carrierName"),d.getString("trackingCode"),enumValue(ShipmentStatus.class,d.getString("status"),ShipmentStatus.PENDING),number(d,"distanceKm"),number(d,"shippingFee"));}
    private Map<String,Double> reservationMap(Object raw){Map<String,Double> result=new HashMap<>();if(raw instanceof Map<?,?>)for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet())if(e.getKey() instanceof String&&e.getValue() instanceof Number){double quantity=((Number)e.getValue()).doubleValue();if(quantity>0D)result.put((String)e.getKey(),quantity);}if(result.isEmpty())throw new IllegalStateException("RESERVATIONS_MISSING");return result;}
    private Order mapOrder(DocumentSnapshot d,List<OrderItem> items){return Order.restore(d.getId(),enumValue(OrderOwnerType.class,d.getString("ownerType"),OrderOwnerType.GUEST),d.getString("ownerId"),d.getString("orderCode"),d.getString("sellerId"),d.getString("campaignId"),enumValue(FulfillmentType.class,d.getString("fulfillmentType"),FulfillmentType.PICKUP),d.getString("receiverName"),d.getString("receiverPhone"),d.getString("receiverAddress"),number(d,"receiverLatitude"),number(d,"receiverLongitude"),number(d,"subtotal"),number(d,"quantityDiscount"),number(d,"shippingFee"),number(d,"totalAmount"),enumValue(PaymentMethod.class,d.getString("paymentMethod"),PaymentMethod.COD),enumValue(PaymentStatus.class,d.getString("paymentStatus"),PaymentStatus.UNPAID),enumValue(OrderStatus.class,d.getString("status"),OrderStatus.PENDING),d.getString("note"),date(d,"createdAt"),date(d,"updatedAt"),items);}
    private void cache(Order value){if(value!=null)cacheExecutor.execute(()->orderDao.replaceOrder(
            OrderCacheMapper.toEntity(value,System.currentTimeMillis())));}
    private List<OrderCacheEntity> toEntities(List<Order> values){List<OrderCacheEntity> result=new ArrayList<>();
        long now=System.currentTimeMillis();for(Order value:values)result.add(OrderCacheMapper.toEntity(value,now));return result;}
    private void failure(Exception e,OrderCallback c){String m=e.getMessage();if("QUOTE_CHANGED".equals(m)){c.onError(ErrorCode.CONFLICT,"QUOTE_CHANGED: Giá sản phẩm hoặc phí giao hàng vừa thay đổi. Báo giá mới đã được tải; vui lòng kiểm tra và xác nhận lại.");return;}if("SELLER_BANK_DETAILS_MISSING".equals(m)){c.onError(ErrorCode.VALIDATION,"Seller chưa cấu hình đủ mã ngân hàng, tên chủ tài khoản và số tài khoản trong hồ sơ cửa hàng.");return;}if("SHIPPING_CONFIG_MISSING".equals(m)){c.onError(ErrorCode.VALIDATION,"Thiếu biểu phí giao hàng toàn hệ thống hoặc tọa độ GPS hợp lệ của Seller/địa chỉ nhận.");return;}if("SHIPPING_COORDINATES_MISSING".equals(m)){c.onError(ErrorCode.VALIDATION,"Thiếu tọa độ cửa hàng hoặc địa chỉ nhận hàng nên chưa thể tính phí theo km.");return;}if(m!=null&&(m.contains("Insufficient")||m.contains("unavailable")||m.contains("BATCH")||m.contains("PRODUCT"))){c.onError(ErrorCode.INSUFFICIENT_STOCK,"Tồn kho đã thay đổi hoặc batch không còn bán.");return;}if("CAMPAIGN_CONFLICT".equals(m)){c.onError(ErrorCode.CAMPAIGN_CONFLICT,"Nhận tại điểm giải cứu cần Campaign hợp lệ, cùng batch được phân bổ và còn đủ lượng khả dụng.");return;}if("IDEMPOTENCY_CONFLICT".equals(m)){c.onError(ErrorCode.CONFLICT,"Idempotency key đã được dùng cho request khác.");return;}if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED){c.onError(ErrorCode.FORBIDDEN,"Firestore Rules từ chối checkout.");return;}c.onError(ErrorCode.UNKNOWN,"Không thể tạo đơn; transaction đã rollback.");}
    private void failure(Exception e,ActionCallback c){if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED)c.onError(ErrorCode.FORBIDDEN,"Không có quyền đọc đơn.");else c.onError(ErrorCode.UNKNOWN,"Không thể tải đơn.");}
    private void failure(Exception e,LifecycleCallback c){String m=e.getMessage()==null?"":e.getMessage();if(m.contains("ORDER_NOT_FOUND")){c.onError(ErrorCode.NOT_FOUND,"Không tìm thấy đơn.");return;}if(m.contains("ILLEGAL_ORDER_TRANSITION")){c.onError(ErrorCode.VALIDATION,"Chuyển trạng thái đơn không hợp lệ.");return;}if(m.contains("BANK_TRANSFER_NOT_CONFIRMED")){c.onError(ErrorCode.CONFLICT,"Cần xác nhận chuyển khoản thủ công trước khi hoàn tất đơn.");return;}if(m.contains("PAID_ORDER_REQUIRES_REFUND")){c.onError(ErrorCode.CONFLICT,"Đơn đã thanh toán cần quy trình hoàn tiền, không thể hủy trực tiếp.");return;}if(m.contains("FINALIZED")||m.contains("reserved")||m.contains("CAMPAIGN")||m.contains("RESERVATIONS")){c.onError(ErrorCode.CONFLICT,"Tồn kho hoặc campaign đã thay đổi; transaction đã rollback.");return;}if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED){c.onError(ErrorCode.FORBIDDEN,"Bạn không có quyền cập nhật đơn này.");return;}c.onError(ErrorCode.UNKNOWN,"Không thể cập nhật đơn; transaction đã rollback.");}
    private static double number(DocumentSnapshot d,String f){Object v=d==null?null:d.get(f);return v instanceof Number?((Number)v).doubleValue():0D;}
    private static long longValue(DocumentSnapshot d,String f){Long v=d.getLong(f);return v==null?0L:v;}private static Date date(DocumentSnapshot d,String f){Timestamp v=d.getTimestamp(f);return v==null?null:v.toDate();}private static String clean(String v){return v==null?"":v.trim();}private static <T extends Enum<T>>T enumValue(Class<T> t,String v,T fallback){try{return Enum.valueOf(t,v==null?"":v);}catch(Exception e){return fallback;}}
    @Override public void close(){for(Runnable poll:guestPolls.values())guestHandler.removeCallbacks(poll);guestPolls.clear();for(ListenerRegistration listener:orderDetailListeners.values())listener.remove();
        orderDetailListeners.clear();for(ListenerRegistration listener:sellerOrderListeners.values())listener.remove();sellerOrderListeners.clear();cacheExecutor.shutdownNow();}
}
