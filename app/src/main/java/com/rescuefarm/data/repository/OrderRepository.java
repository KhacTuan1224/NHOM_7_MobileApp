package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.model.Shipment;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.service.order.CheckoutRequest;
import java.util.List;

public interface OrderRepository {
    interface ItemCallback { void success(List<com.rescuefarm.domain.model.OrderItem> items); void error(String message); }
    default void getOrderItems(String id, ItemCallback callback) { callback.error("Cần kết nối mạng để tải sản phẩm của đơn."); }
    enum ErrorCode { VALIDATION, INSUFFICIENT_STOCK, MULTI_SELLER, CAMPAIGN_CONFLICT,
        FORBIDDEN, NETWORK, NOT_FOUND, CONFLICT, UNKNOWN }
    interface OrderCallback {
        void onSuccess(Order order, boolean idempotentReplay);
        void onError(ErrorCode error, String message);
    }
    interface ActionCallback { void onSuccess(); void onError(ErrorCode error, String message); }
    interface LifecycleCallback {
        void onSuccess(Order order, Payment payment, Shipment shipment, boolean idempotentReplay);
        void onError(ErrorCode error, String message);
    }
    LiveData<List<Order>> observeOrders(String ownerId);
    LiveData<List<Order>> observeSellerOrders(String sellerId);
    void checkout(CheckoutRequest request, OrderCallback callback);
    void refreshOrders(String ownerId, ActionCallback callback);
    void refreshSellerOrders(String sellerId, ActionCallback callback);
    default void loadMoreSellerOrders(String sellerId, ActionCallback callback) { callback.onSuccess(); }
    default boolean hasMoreSellerOrders(String sellerId) { return false; }
    void findGuestOrder(String orderCode, String receiverPhone, OrderCallback callback);
    void getOrder(String orderId, LifecycleCallback callback);
    void observeOrder(String orderId, LifecycleCallback callback);
    void stopObservingOrder(String orderId);
    void transitionOrder(String orderId, OrderStatus targetStatus, LifecycleCallback callback);
    default void updateShipmentDetails(String orderId, String carrierName, String trackingCode,
            ActionCallback callback) {
        callback.onError(ErrorCode.NOT_FOUND, "Chức năng cập nhật shipment chưa được cấu hình.");
    }
    default boolean canConfirmGuestReceipt(String orderId) { return false; }
    void confirmBankTransfer(String orderId, String referenceCode, LifecycleCallback callback);
    default void close() { }
}
