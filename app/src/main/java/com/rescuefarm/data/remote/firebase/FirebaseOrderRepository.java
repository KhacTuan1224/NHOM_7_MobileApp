package com.rescuefarm.data.remote.firebase;

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
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.enums.PromotionType;
import com.rescuefarm.domain.enums.ShipmentStatus;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Promotion;
import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.model.Shipment;
import com.rescuefarm.service.inventory.InventoryService;
import com.rescuefarm.service.network.NetworkStatusProvider;
import com.rescuefarm.service.order.CheckoutPolicy;
import com.rescuefarm.service.order.CheckoutIdempotencyPolicy;
import com.rescuefarm.service.order.CheckoutRequest;
import com.rescuefarm.service.order.OrderLifecycleService;
import com.rescuefarm.service.pricing.PricingService;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class FirebaseOrderRepository implements OrderRepository {
    private static final String ORDERS="orders", ITEMS="items", PRODUCTS="products",
            BATCHES="productBatches", CAMPAIGNS="campaigns", PROMOTIONS="promotions",
            PAYMENTS="payments", SHIPMENTS="shipments";
    private final FirebaseFirestore db=FirebaseFirestore.getInstance();
    private final FirebaseAuth auth=FirebaseAuth.getInstance(); private final NetworkStatusProvider network;
    private final RescueFarmDatabase database; private final OrderCacheDao orderDao;
    private final ExecutorService cacheExecutor;
    private final Map<String, ListenerRegistration> orderDetailListeners = new HashMap<>();
    private final PricingService pricing=new PricingService(); private final InventoryService inventory=new InventoryService();
    private final OrderLifecycleService lifecycle=new OrderLifecycleService();
    public FirebaseOrderRepository(RescueFarmDatabase database, ExecutorService cacheExecutor,
            NetworkStatusProvider network){this.database=database;this.orderDao=database.orderCacheDao();
        this.cacheExecutor=cacheExecutor;this.network=network;}
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
    @Override public LiveData<List<Order>> observeSellerOrders(String sellerId){return Transformations.map(
            orderDao.observeSellerOrders(sellerId),OrderCacheMapper::orders);}

    @Override public void checkout(CheckoutRequest request, OrderCallback callback) {
        if (request == null || request.ownerType == OrderOwnerType.GUEST) {
            callback.onError(ErrorCode.FORBIDDEN,
                    "Checkout guest đang tạm khóa vì chưa có dịch vụ backend đáng tin cậy. Hãy đăng nhập để đặt hàng.");
            return;
        }
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Checkout cần kết nối mạng.");return;}
        final String sellerId;
        try { sellerId=CheckoutPolicy.requireSingleSeller(request.items); validateRequest(request); }
        catch(IllegalArgumentException e){callback.onError("MULTI_SELLER".equals(e.getMessage())?ErrorCode.MULTI_SELLER:ErrorCode.VALIDATION,
                "MULTI_SELLER".equals(e.getMessage())?"Mỗi Order chỉ được chứa sản phẩm của một seller.":
                "PAYMENT_FULFILLMENT_MISMATCH".equals(e.getMessage())?"COD chỉ dùng cho giao hàng; tiền mặt tại chỗ chỉ dùng cho pickup/điểm giải cứu.":e.getMessage());return;}
        DocumentReference orderRef=db.collection(ORDERS).document(request.requestId);
        db.runTransaction(tx->{
            DocumentSnapshot existing=tx.get(orderRef);
            if(existing.exists()){
                CheckoutIdempotencyPolicy.isReplay(existing.getString("requestId"),existing.getString("ownerId"),request);
                return new Result(mapOrder(existing,new ArrayList<>()),true);
            }
            double shippingDistanceKm = 0D, shippingFee = 0D;
            DocumentSnapshot sellerProfile = null;
            if (request.fulfillmentType == FulfillmentType.DELIVERY
                    || request.paymentMethod == PaymentMethod.BANK_TRANSFER) {
                sellerProfile = tx.get(db.collection("sellerPublicProfiles").document(sellerId));
                if (!sellerProfile.exists()) throw new IllegalStateException(
                        request.paymentMethod == PaymentMethod.BANK_TRANSFER
                                ? "SELLER_BANK_DETAILS_MISSING" : "SHIPPING_CONFIG_MISSING");
            }
            if (request.fulfillmentType == FulfillmentType.DELIVERY) {
                DocumentSnapshot shippingConfig = tx.get(db.collection("appConfig").document("shipping"));
                if (!shippingConfig.exists()) throw new IllegalStateException("SHIPPING_CONFIG_MISSING");
                double sellerLat = number(sellerProfile, "latitude"), sellerLng = number(sellerProfile, "longitude");
                if ((sellerLat == 0D && sellerLng == 0D) || request.latitude == 0D && request.longitude == 0D)
                    throw new IllegalStateException("SHIPPING_COORDINATES_MISSING");
                shippingDistanceKm = distanceKm(sellerLat, sellerLng, request.latitude, request.longitude);
                double base = number(shippingConfig, "baseFeeVnd");
                double perKm = number(shippingConfig, "perKmFeeVnd");
                double freeKm = number(shippingConfig, "freeDistanceKm");
                shippingFee = base + Math.ceil(Math.max(0D, shippingDistanceKm - freeKm) * perKm);
            }
            Map<String,DocumentSnapshot> productDocs=new HashMap<>(),batchDocs=new HashMap<>(),promotionDocs=new HashMap<>();
            for(CartItem item:request.items){
                DocumentSnapshot product=tx.get(db.collection(PRODUCTS).document(item.getProductId()));
                DocumentSnapshot batch=tx.get(db.collection(BATCHES).document(item.getBatchId()));
                DocumentSnapshot promotion=tx.get(db.collection(PROMOTIONS).document(item.getProductId()));
                productDocs.put(item.getId(),product);batchDocs.put(item.getId(),batch);promotionDocs.put(item.getId(),promotion);
            }
            Set<String> campaignIds=new HashSet<>();
            for(DocumentSnapshot batch:batchDocs.values()){
                String campaign=clean(batch.getString("activeCampaignId")); if(!campaign.isEmpty())campaignIds.add(campaign);
            }
            if(campaignIds.size()>1)throw new IllegalStateException("CAMPAIGN_CONFLICT");
            String campaignId=campaignIds.isEmpty()?"":campaignIds.iterator().next();
            if(request.fulfillmentType==FulfillmentType.ON_SITE_RESCUE&&campaignId.isEmpty())
                throw new IllegalStateException("CAMPAIGN_CONFLICT");
            DocumentSnapshot campaignDoc=campaignId.isEmpty()?null:tx.get(db.collection(CAMPAIGNS).document(campaignId));
            double campaignReserve=0D,subtotal=0D; List<OrderItem> snapshots=new ArrayList<>(); Date now=new Date();
            Map<String,ProductBatch> batches=new HashMap<>();
            for(CartItem cartItem:request.items){
                DocumentSnapshot pDoc=productDocs.get(cartItem.getId()),bDoc=batchDocs.get(cartItem.getId());
                Product product=requireProduct(pDoc,sellerId); ProductBatch batch=requireBatch(bDoc,product.getId());
                Promotion promotion=mapPromotion(promotionDocs.get(cartItem.getId()));
                inventory.validateAvailableStock(batch,cartItem.getQuantity(),now);
                double finalPrice=pricing.calculatePrice(product.getOriginalPrice(),product.getRescuePrice(),
                        cartItem.getQuantity(),promotion,now).getFinalUnitPrice();
                String image=product.getImageUrls().isEmpty()?"":product.getImageUrls().get(0);
                OrderItem snapshot=OrderItem.snapshot(cartItem.getBatchId(),request.requestId,product.getId(),
                        batch.getId(),product.getName(),image,product.getUnit(),product.getOriginalPrice(),
                        product.getRescuePrice(),finalPrice,cartItem.getQuantity());
                snapshots.add(snapshot);subtotal+=snapshot.getSubtotal();inventory.reserveStock(batch,cartItem.getQuantity(),now);
                batches.put(batch.getId(),batch);if(!clean(batch.getActiveCampaignId()).isEmpty())campaignReserve+=cartItem.getQuantity();
            }
            if(campaignDoc!=null){
                if(!campaignDoc.exists()||!sellerId.equals(campaignDoc.getString("sellerId"))
                        || !CampaignStatus.ACTIVE.name().equals(campaignDoc.getString("status")))throw new IllegalStateException("CAMPAIGN_CONFLICT");
                if(request.fulfillmentType==FulfillmentType.ON_SITE_RESCUE
                        && "MOBILE_POINT".equals(campaignDoc.getString("rescueMode"))){
                    Timestamp locationUpdated=campaignDoc.getTimestamp("locationUpdatedAt");
                    if(!Boolean.TRUE.equals(campaignDoc.getBoolean("locationSharingEnabled"))
                            ||locationUpdated==null||now.before(locationUpdated.toDate())
                            ||now.getTime()-locationUpdated.toDate().getTime()>10L*60L*1000L)
                        throw new IllegalStateException("CAMPAIGN_CONFLICT");
                }
                Object rawTargets=campaignDoc.get("batchTargets");
                if(!(rawTargets instanceof Map<?,?>))throw new IllegalStateException("CAMPAIGN_CONFLICT");
                for(ProductBatch batch:batches.values())if(!clean(batch.getActiveCampaignId()).isEmpty()){
                    Object target=((Map<?,?>)rawTargets).get(batch.getId());
                    double requested=snapshots.stream().filter(v->v.getBatchId().equals(batch.getId())).findFirst().get().getQuantity();
                    if(!(target instanceof Number)||((Number)target).doubleValue()+0.000001<requested)
                        throw new IllegalStateException("CAMPAIGN_CONFLICT");
                }
                double remaining=number(campaignDoc,"targetQuantity")-number(campaignDoc,"reservedQuantity")-number(campaignDoc,"rescuedQuantity");
                if(remaining+0.000001<campaignReserve)throw new IllegalStateException("CAMPAIGN_CONFLICT");
            }
            if (Double.isFinite(request.expectedSubtotal)
                    && Math.abs(request.expectedSubtotal - subtotal) > 0.5D
                    || Double.isFinite(request.expectedShippingFee)
                    && Math.abs(request.expectedShippingFee - shippingFee) > 0.5D) {
                throw new IllegalStateException("QUOTE_CHANGED");
            }
            PaymentStatus paymentStatus=request.paymentMethod==PaymentMethod.BANK_TRANSFER?PaymentStatus.PENDING:PaymentStatus.UNPAID;
            Order order=Order.create(request.requestId,request.ownerType,request.ownerId,
                    CheckoutPolicy.orderCode(request.requestId),sellerId,campaignId,request.fulfillmentType,
                    request.receiverName,request.receiverPhone,request.receiverAddress,request.latitude,request.longitude,
                    subtotal,0D,shippingFee,request.paymentMethod,paymentStatus,request.note,snapshots,now);
            Map<String,Object> orderData=orderMap(order);orderData.put("requestId",request.requestId);
            orderData.put("campaignReservedQuantity",campaignReserve);
            Map<String,Double> reservations=new HashMap<>();for(OrderItem item:snapshots)reservations.put(item.getBatchId(),item.getQuantity());
            orderData.put("batchReservations",reservations);
            orderData.put("inventoryFinalized",false);orderData.put("finalizedStatus","");
            orderData.put("createdAt",FieldValue.serverTimestamp());orderData.put("updatedAt",FieldValue.serverTimestamp());tx.set(orderRef,orderData);
            Payment payment=Payment.create(request.requestId,request.paymentMethod,order.getTotalAmount());
            if (request.paymentMethod == PaymentMethod.BANK_TRANSFER) {
                String bankCode = clean(sellerProfile.getString("bankCode"));
                String accountName = clean(sellerProfile.getString("bankAccountName"));
                String accountNumber = clean(sellerProfile.getString("bankAccountNumber"));
                if (bankCode.isEmpty() || accountName.isEmpty() || accountNumber.isEmpty())
                    throw new IllegalStateException("SELLER_BANK_DETAILS_MISSING");
                payment.setBankDetails(bankCode, accountName, accountNumber);
            }
            tx.set(db.collection(PAYMENTS).document(request.requestId),paymentMap(payment));
            if(lifecycle.requiresShipment(request.fulfillmentType)){
                Shipment shipment=Shipment.create(request.requestId,shippingDistanceKm,order.getShippingFee());
                tx.set(db.collection(SHIPMENTS).document(request.requestId),shipmentMap(shipment));
            }
            for(OrderItem item:snapshots)tx.set(orderRef.collection(ITEMS).document(item.getId()),itemMap(item));
            for(Map.Entry<String,ProductBatch> entry:batches.entrySet()){
                DocumentSnapshot before=batchDocs.values().stream().filter(v->v.getId().equals(entry.getKey())).findFirst().orElseThrow();
                ProductBatch batch=entry.getValue(); Map<String,Object> updates=new HashMap<>();
                updates.put("availableQuantity",batch.getAvailableQuantity());updates.put("reservedQuantity",batch.getReservedQuantity());
                updates.put("status",batch.getStatus().name());updates.put("inventoryVersion",longValue(before,"inventoryVersion")+1L);
                updates.put("lastReservationOrderId",request.requestId);updates.put("lastReservationQuantity",
                        snapshots.stream().filter(v->v.getBatchId().equals(batch.getId())).findFirst().get().getQuantity());
                updates.put("updatedAt",FieldValue.serverTimestamp());tx.update(before.getReference(),updates);
            }
            if(campaignDoc!=null)tx.update(campaignDoc.getReference(),"reservedQuantity",
                    number(campaignDoc,"reservedQuantity")+campaignReserve,"lastReservationOrderId",request.requestId,
                    "lastReservationQuantity",campaignReserve,"updatedAt",FieldValue.serverTimestamp());
            return new Result(order,false);
        }).addOnSuccessListener(r->{cache(r.order);callback.onSuccess(r.order,r.replay);})
                .addOnFailureListener(e->failure(e,callback));
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
        db.collection(ORDERS).whereEqualTo("sellerId",sellerId).get().addOnSuccessListener(s->{
            List<Order> values=new ArrayList<>();for(DocumentSnapshot d:s.getDocuments())values.add(mapOrder(d,new ArrayList<>()));
            cacheExecutor.execute(()->{database.runInTransaction(()->{orderDao.clearSellerOrders(sellerId);
                orderDao.replaceOrders(toEntities(values));});callback.onSuccess();});}).addOnFailureListener(e->failure(e,callback));
    }
    @Override public void findGuestOrder(String code,String phone,OrderCallback callback){
        callback.onError(ErrorCode.FORBIDDEN,
                "Tra cứu đơn guest đã tắt để tránh lộ đơn bằng mã đơn và số điện thoại. Hãy đăng nhập tài khoản sở hữu đơn.");
    }

    @Override public void getOrder(String orderId,LifecycleCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Cần kết nối mạng để tải trạng thái đơn.");return;}
        fetchLifecycle(clean(orderId),false,callback);
    }

    @Override public void observeOrder(String orderId, LifecycleCallback callback) {
        String id = clean(orderId);
        if (id.isEmpty()) { callback.onError(ErrorCode.VALIDATION, "Mã đơn hàng không hợp lệ."); return; }
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
        ListenerRegistration listener = orderDetailListeners.remove(clean(orderId));
        if (listener != null) listener.remove();
    }

    @Override public void transitionOrder(String orderId,OrderStatus target,LifecycleCallback callback){
        if(!network.isOnline()){callback.onError(ErrorCode.NETWORK,"Cập nhật trạng thái cần kết nối mạng.");return;}
        if(clean(orderId).isEmpty()||target==null){callback.onError(ErrorCode.VALIDATION,"Đơn hoặc trạng thái không hợp lệ.");return;}
        DocumentReference orderRef=db.collection(ORDERS).document(orderId);
        db.runTransaction(tx->{
            DocumentSnapshot orderDoc=tx.get(orderRef);
            if(!orderDoc.exists())throw new IllegalStateException("ORDER_NOT_FOUND");
            Order order=mapOrder(orderDoc,new ArrayList<>());
            boolean replay=lifecycle.isReplay(order.getStatus(),target);
            if(replay)return true;
            lifecycle.requireLegalTransition(order.getFulfillmentType(),order.getStatus(),target,
                    order.getPaymentMethod(),order.getPaymentStatus());
            if(Boolean.TRUE.equals(orderDoc.getBoolean("inventoryFinalized")))throw new IllegalStateException("ORDER_ALREADY_FINALIZED");
            DocumentReference paymentRef=db.collection(PAYMENTS).document(orderId);
            DocumentSnapshot paymentDoc=tx.get(paymentRef);
            if(!paymentDoc.exists())throw new IllegalStateException("PAYMENT_NOT_FOUND");
            DocumentReference shipmentRef=db.collection(SHIPMENTS).document(orderId);
            DocumentSnapshot shipmentDoc=null;
            if(lifecycle.requiresShipment(order.getFulfillmentType())){
                shipmentDoc=tx.get(shipmentRef);if(!shipmentDoc.exists())throw new IllegalStateException("SHIPMENT_NOT_FOUND");
            }
            Map<String,Double> reservations=reservationMap(orderDoc.get("batchReservations"));
            Map<String,DocumentSnapshot> batchDocs=new HashMap<>();
            for(String batchId:reservations.keySet())batchDocs.put(batchId,tx.get(db.collection(BATCHES).document(batchId)));
            DocumentSnapshot campaignDoc=clean(order.getCampaignId()).isEmpty()?null:
                    tx.get(db.collection(CAMPAIGNS).document(order.getCampaignId()));

            PaymentStatus nextPayment=lifecycle.paymentStatusForTerminal(order.getPaymentMethod(),
                    order.getPaymentStatus(),target);
            boolean terminal=target==OrderStatus.DELIVERED||target==OrderStatus.CANCELLED;
            if(terminal){
                for(Map.Entry<String,Double> entry:reservations.entrySet()){
                    DocumentSnapshot before=batchDocs.get(entry.getKey());
                    if(before==null||!before.exists())throw new IllegalStateException("BATCH_UNAVAILABLE");
                    ProductBatch batch=requireBatch(before,before.getString("productId"));
                    if(target==OrderStatus.DELIVERED)inventory.commitReservedStock(batch,entry.getValue());
                    else inventory.releaseReservedStock(batch,entry.getValue());
                    Map<String,Object> update=new HashMap<>();
                    update.put("availableQuantity",batch.getAvailableQuantity());update.put("reservedQuantity",batch.getReservedQuantity());
                    update.put("soldQuantity",batch.getSoldQuantity());update.put("status",batch.getStatus().name());
                    update.put("inventoryVersion",longValue(before,"inventoryVersion")+1L);
                    update.put("lastLifecycleOrderId",orderId);update.put("lastLifecycleAction",target.name());
                    update.put("lastLifecycleQuantity",entry.getValue());update.put("updatedAt",FieldValue.serverTimestamp());
                    tx.update(before.getReference(),update);
                }
                double campaignQuantity=number(orderDoc,"campaignReservedQuantity");
                if(campaignDoc!=null&&campaignQuantity>0D){
                    if(!campaignDoc.exists()||number(campaignDoc,"reservedQuantity")+0.000001<campaignQuantity)
                        throw new IllegalStateException("CAMPAIGN_CONFLICT");
                    Map<String,Object> update=new HashMap<>();
                    update.put("reservedQuantity",number(campaignDoc,"reservedQuantity")-campaignQuantity);
                    double rescued=number(campaignDoc,"rescuedQuantity")+(target==OrderStatus.DELIVERED?campaignQuantity:0D);
                    update.put("rescuedQuantity",rescued);update.put("lastLifecycleOrderId",orderId);
                    update.put("lastLifecycleAction",target.name());update.put("lastLifecycleQuantity",campaignQuantity);
                    if(target==OrderStatus.DELIVERED&&rescued+0.000001>=number(campaignDoc,"targetQuantity"))update.put("status",CampaignStatus.COMPLETED.name());
                    update.put("updatedAt",FieldValue.serverTimestamp());tx.update(campaignDoc.getReference(),update);
                }
            }
            Map<String,Object> orderUpdate=new HashMap<>();orderUpdate.put("status",target.name());
            orderUpdate.put("paymentStatus",nextPayment.name());orderUpdate.put("updatedAt",FieldValue.serverTimestamp());
            if(terminal){orderUpdate.put("inventoryFinalized",true);orderUpdate.put("finalizedStatus",target.name());}
            tx.update(orderRef,orderUpdate);
            if(terminal){Map<String,Object> paymentUpdate=new HashMap<>();paymentUpdate.put("status",nextPayment.name());
                paymentUpdate.put("updatedAt",FieldValue.serverTimestamp());
                if(nextPayment==PaymentStatus.PAID&&!PaymentStatus.PAID.name().equals(paymentDoc.getString("status")))
                    paymentUpdate.put("paidAt",FieldValue.serverTimestamp());
                tx.update(paymentRef,paymentUpdate);}
            if(shipmentDoc!=null){Map<String,Object> shipmentUpdate=new HashMap<>();
                shipmentUpdate.put("status",lifecycle.shipmentStatusFor(target).name());
                shipmentUpdate.put("updatedAt",FieldValue.serverTimestamp());tx.update(shipmentRef,shipmentUpdate);}
            return false;
        }).addOnSuccessListener(replay->fetchLifecycle(orderId,replay,callback))
                .addOnFailureListener(e->failure(e,callback));
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
            tx.update(orderRef,"paymentStatus",PaymentStatus.PAID.name(),"updatedAt",FieldValue.serverTimestamp());return false;
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

    private void validateRequest(CheckoutRequest r){if(r==null||clean(r.requestId).isEmpty()||r.ownerType==null||clean(r.ownerId).isEmpty()
            ||clean(r.receiverName).length()<2||clean(r.receiverPhone).length()<9||r.fulfillmentType==null||r.paymentMethod==null)
        throw new IllegalArgumentException("Thông tin checkout chưa hợp lệ.");
        lifecycle.requirePaymentCompatible(r.fulfillmentType,r.paymentMethod);
        String uid=auth.getCurrentUser()==null?"":auth.getCurrentUser().getUid();
        if(r.ownerType==OrderOwnerType.CUSTOMER&&!r.ownerId.equals(uid))throw new IllegalArgumentException("Customer session không hợp lệ.");}
    private Product requireProduct(DocumentSnapshot d,String seller){if(!d.exists()||!seller.equals(d.getString("sellerId"))
            ||!ProductStatus.ACTIVE.name().equals(d.getString("status")))throw new IllegalStateException("PRODUCT_UNAVAILABLE");
        List<String> images=new ArrayList<>();Object raw=d.get("imageUrls");if(raw instanceof List<?>)for(Object x:(List<?>)raw)if(x instanceof String)images.add((String)x);
        return Product.restore(d.getId(),d.getString("sellerId"),d.getString("categoryId"),d.getString("name"),d.getString("description"),
                number(d,"originalPrice"),number(d,"rescuePrice"),d.getString("unit"),d.getString("origin"),d.getString("province"),images,
                number(d,"averageRating"),(int)longValue(d,"reviewCount"),ProductStatus.ACTIVE);}
    private ProductBatch requireBatch(DocumentSnapshot d,String productId){if(!d.exists()||!productId.equals(d.getString("productId")))throw new IllegalStateException("BATCH_UNAVAILABLE");
        return ProductBatch.restore(d.getId(),productId,d.getString("activeCampaignId"),date(d,"harvestDate"),date(d,"expiryDate"),number(d,"initialQuantity"),
                number(d,"availableQuantity"),number(d,"reservedQuantity"),number(d,"soldQuantity"),enumValue(BatchStatus.class,d.getString("status"),BatchStatus.AVAILABLE),longValue(d,"inventoryVersion"));}
    private Promotion mapPromotion(DocumentSnapshot d){if(d==null||!d.exists())return null;try{Map<String,Double> tiers=new HashMap<>();Object raw=d.get("quantityDiscountTiers");
        if(raw instanceof Map<?,?>)for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet())if(e.getKey() instanceof String&&e.getValue() instanceof Number)tiers.put((String)e.getKey(),((Number)e.getValue()).doubleValue());
        return Promotion.restore(d.getId(),d.getString("sellerId"),d.getString("productId"),enumValue(PromotionType.class,d.getString("type"),null),number(d,"value"),tiers,date(d,"startDate"),date(d,"endDate"),Boolean.TRUE.equals(d.getBoolean("active")));}catch(RuntimeException e){return null;}}
    private Map<String,Object> orderMap(Order o){Map<String,Object> m=new HashMap<>();m.put("id",o.getId());m.put("orderCode",o.getOrderCode());m.put("ownerType",o.getOwnerType().name());m.put("ownerId",o.getOwnerId());m.put("sellerId",o.getSellerId());m.put("campaignId",o.getCampaignId());m.put("fulfillmentType",o.getFulfillmentType().name());m.put("receiverName",o.getReceiverName());m.put("receiverPhone",o.getReceiverPhone());m.put("receiverAddress",o.getReceiverAddress());m.put("receiverLatitude",o.getReceiverLatitude());m.put("receiverLongitude",o.getReceiverLongitude());m.put("subtotal",o.getSubtotal());m.put("quantityDiscount",o.getQuantityDiscount());m.put("shippingFee",o.getShippingFee());m.put("totalAmount",o.getTotalAmount());m.put("paymentMethod",o.getPaymentMethod().name());m.put("paymentStatus",o.getPaymentStatus().name());m.put("status",o.getStatus().name());m.put("note",o.getNote());return m;}
    private Map<String,Object> itemMap(OrderItem i){Map<String,Object> m=new HashMap<>();m.put("id",i.getId());m.put("orderId",i.getOrderId());m.put("productId",i.getProductId());m.put("batchId",i.getBatchId());m.put("productNameSnapshot",i.getProductNameSnapshot());m.put("productImageSnapshot",i.getProductImageSnapshot());m.put("unitSnapshot",i.getUnitSnapshot());m.put("originalPriceSnapshot",i.getOriginalPriceSnapshot());m.put("rescuePriceSnapshot",i.getRescuePriceSnapshot());m.put("finalPriceSnapshot",i.getFinalPriceSnapshot());m.put("quantity",i.getQuantity());m.put("subtotal",i.getSubtotal());return m;}
    private OrderItem mapOrderItem(DocumentSnapshot d){return OrderItem.snapshot(d.getId(),d.getString("orderId"),
            d.getString("productId"),d.getString("batchId"),d.getString("productNameSnapshot"),
            d.getString("productImageSnapshot"),d.getString("unitSnapshot"),number(d,"originalPriceSnapshot"),
            number(d,"rescuePriceSnapshot"),number(d,"finalPriceSnapshot"),number(d,"quantity"));}
    private Map<String,Object> paymentMap(Payment p){Map<String,Object> m=new HashMap<>();m.put("id",p.getId());m.put("orderId",p.getOrderId());m.put("method",p.getMethod().name());m.put("status",p.getStatus().name());m.put("amount",p.getAmount());m.put("referenceCode",clean(p.getReferenceCode()));m.put("bankCode",clean(p.getBankCode()));m.put("bankAccountName",clean(p.getBankAccountName()));m.put("bankAccountNumber",clean(p.getBankAccountNumber()));m.put("paidAt",null);m.put("createdAt",FieldValue.serverTimestamp());m.put("updatedAt",FieldValue.serverTimestamp());return m;}
    private Map<String,Object> shipmentMap(Shipment s){Map<String,Object> m=new HashMap<>();m.put("id",s.getId());m.put("orderId",s.getOrderId());m.put("carrierName",clean(s.getCarrierName()));m.put("trackingCode",clean(s.getTrackingCode()));m.put("status",s.getStatus().name());m.put("distanceKm",s.getDistanceKm());m.put("shippingFee",s.getShippingFee());m.put("createdAt",FieldValue.serverTimestamp());m.put("updatedAt",FieldValue.serverTimestamp());return m;}
    private Payment mapPayment(DocumentSnapshot d){Payment value=Payment.restore(d.getId(),d.getString("orderId"),enumValue(PaymentMethod.class,d.getString("method"),PaymentMethod.COD),enumValue(PaymentStatus.class,d.getString("status"),PaymentStatus.UNPAID),number(d,"amount"),d.getString("referenceCode"),date(d,"paidAt"));value.setBankDetails(d.getString("bankCode"),d.getString("bankAccountName"),d.getString("bankAccountNumber"));return value;}
    private Shipment mapShipment(DocumentSnapshot d){return Shipment.restore(d.getId(),d.getString("orderId"),d.getString("carrierName"),d.getString("trackingCode"),enumValue(ShipmentStatus.class,d.getString("status"),ShipmentStatus.PENDING),number(d,"distanceKm"),number(d,"shippingFee"));}
    private Map<String,Double> reservationMap(Object raw){Map<String,Double> result=new HashMap<>();if(raw instanceof Map<?,?>)for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet())if(e.getKey() instanceof String&&e.getValue() instanceof Number){double quantity=((Number)e.getValue()).doubleValue();if(quantity>0D)result.put((String)e.getKey(),quantity);}if(result.isEmpty())throw new IllegalStateException("RESERVATIONS_MISSING");return result;}
    private Order mapOrder(DocumentSnapshot d,List<OrderItem> items){return Order.restore(d.getId(),enumValue(OrderOwnerType.class,d.getString("ownerType"),OrderOwnerType.GUEST),d.getString("ownerId"),d.getString("orderCode"),d.getString("sellerId"),d.getString("campaignId"),enumValue(FulfillmentType.class,d.getString("fulfillmentType"),FulfillmentType.PICKUP),d.getString("receiverName"),d.getString("receiverPhone"),d.getString("receiverAddress"),number(d,"receiverLatitude"),number(d,"receiverLongitude"),number(d,"subtotal"),number(d,"quantityDiscount"),number(d,"shippingFee"),number(d,"totalAmount"),enumValue(PaymentMethod.class,d.getString("paymentMethod"),PaymentMethod.COD),enumValue(PaymentStatus.class,d.getString("paymentStatus"),PaymentStatus.UNPAID),enumValue(OrderStatus.class,d.getString("status"),OrderStatus.PENDING),d.getString("note"),date(d,"createdAt"),date(d,"updatedAt"),items);}
    private void cache(Order value){if(value!=null)cacheExecutor.execute(()->orderDao.replaceOrder(
            OrderCacheMapper.toEntity(value,System.currentTimeMillis())));}
    private List<OrderCacheEntity> toEntities(List<Order> values){List<OrderCacheEntity> result=new ArrayList<>();
        long now=System.currentTimeMillis();for(Order value:values)result.add(OrderCacheMapper.toEntity(value,now));return result;}
    private void failure(Exception e,OrderCallback c){String m=e.getMessage();if("QUOTE_CHANGED".equals(m)){c.onError(ErrorCode.CONFLICT,"QUOTE_CHANGED: Giá sản phẩm hoặc phí giao hàng vừa thay đổi. Báo giá mới đã được tải; vui lòng kiểm tra và xác nhận lại.");return;}if("SELLER_BANK_DETAILS_MISSING".equals(m)){c.onError(ErrorCode.VALIDATION,"Seller chưa cấu hình đủ mã ngân hàng, tên chủ tài khoản và số tài khoản trong hồ sơ cửa hàng.");return;}if("SHIPPING_CONFIG_MISSING".equals(m)){c.onError(ErrorCode.VALIDATION,"Chưa cấu hình phí giao hàng hoặc tọa độ seller. Cần tạo appConfig/shipping và cập nhật hồ sơ cửa hàng.");return;}if("SHIPPING_COORDINATES_MISSING".equals(m)){c.onError(ErrorCode.VALIDATION,"Thiếu tọa độ cửa hàng hoặc địa chỉ nhận hàng nên chưa thể tính phí theo km.");return;}if(m!=null&&(m.contains("Insufficient")||m.contains("unavailable")||m.contains("BATCH")||m.contains("PRODUCT"))){c.onError(ErrorCode.INSUFFICIENT_STOCK,"Tồn kho đã thay đổi hoặc batch không còn bán.");return;}if("CAMPAIGN_CONFLICT".equals(m)){c.onError(ErrorCode.CAMPAIGN_CONFLICT,"Nhận tại điểm giải cứu cần Campaign hợp lệ, cùng batch được phân bổ và còn đủ lượng khả dụng.");return;}if("IDEMPOTENCY_CONFLICT".equals(m)){c.onError(ErrorCode.CONFLICT,"Idempotency key đã được dùng cho request khác.");return;}if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED){c.onError(ErrorCode.FORBIDDEN,"Firestore Rules từ chối checkout.");return;}c.onError(ErrorCode.UNKNOWN,"Không thể tạo đơn; transaction đã rollback.");}
    private void failure(Exception e,ActionCallback c){if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED)c.onError(ErrorCode.FORBIDDEN,"Không có quyền đọc đơn.");else c.onError(ErrorCode.UNKNOWN,"Không thể tải đơn.");}
    private void failure(Exception e,LifecycleCallback c){String m=e.getMessage()==null?"":e.getMessage();if(m.contains("ORDER_NOT_FOUND")){c.onError(ErrorCode.NOT_FOUND,"Không tìm thấy đơn.");return;}if(m.contains("ILLEGAL_ORDER_TRANSITION")){c.onError(ErrorCode.VALIDATION,"Chuyển trạng thái đơn không hợp lệ.");return;}if(m.contains("BANK_TRANSFER_NOT_CONFIRMED")){c.onError(ErrorCode.CONFLICT,"Cần xác nhận chuyển khoản thủ công trước khi hoàn tất đơn.");return;}if(m.contains("PAID_ORDER_REQUIRES_REFUND")){c.onError(ErrorCode.CONFLICT,"Đơn đã thanh toán cần quy trình hoàn tiền, không thể hủy trực tiếp.");return;}if(m.contains("FINALIZED")||m.contains("reserved")||m.contains("CAMPAIGN")||m.contains("RESERVATIONS")){c.onError(ErrorCode.CONFLICT,"Tồn kho hoặc campaign đã thay đổi; transaction đã rollback.");return;}if(e instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)e).getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED){c.onError(ErrorCode.FORBIDDEN,"Bạn không có quyền cập nhật đơn này.");return;}c.onError(ErrorCode.UNKNOWN,"Không thể cập nhật đơn; transaction đã rollback.");}
    private static double number(DocumentSnapshot d,String f){Object v=d.get(f);return v instanceof Number?((Number)v).doubleValue():0D;}private static long longValue(DocumentSnapshot d,String f){Long v=d.getLong(f);return v==null?0L:v;}private static Date date(DocumentSnapshot d,String f){Timestamp v=d.getTimestamp(f);return v==null?null:v.toDate();}private static String clean(String v){return v==null?"":v.trim();}private static <T extends Enum<T>>T enumValue(Class<T> t,String v,T fallback){try{return Enum.valueOf(t,v==null?"":v);}catch(Exception e){return fallback;}}
    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2D) * Math.sin(dLat / 2D)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2D) * Math.sin(dLon / 2D);
        return 6371D * 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
    }
    private static final class Result{final Order order;final boolean replay;Result(Order o,boolean r){order=o;replay=r;}}
    @Override public void close(){for(ListenerRegistration listener:orderDetailListeners.values())listener.remove();
        orderDetailListeners.clear();cacheExecutor.shutdownNow();}
}
