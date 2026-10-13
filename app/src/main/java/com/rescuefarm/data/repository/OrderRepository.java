package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.model.Shipment;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.service.order.CheckoutRequest;
import java.util.List;

public interface OrderRepository {
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
    void findGuestOrder(String orderCode, String receiverPhone, OrderCallback callback);
    void getOrder(String orderId, LifecycleCallback callback);
    void observeOrder(String orderId, LifecycleCallback callback);
    void stopObservingOrder(String orderId);
    void transitionOrder(String orderId, OrderStatus targetStatus, LifecycleCallback callback);
    void confirmBankTransfer(String orderId, String referenceCode, LifecycleCallback callback);
    default void close() { }
}
