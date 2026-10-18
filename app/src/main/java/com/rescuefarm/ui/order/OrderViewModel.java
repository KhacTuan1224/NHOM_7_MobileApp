package com.rescuefarm.ui.order;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.Payment;
import com.rescuefarm.domain.model.Shipment;
import java.util.List;

public class OrderViewModel extends ViewModel {
    private final OrderRepository repository; private final MutableLiveData<OrderLifecycleState> state=new MutableLiveData<>(OrderLifecycleState.idle());
    public OrderViewModel(OrderRepository repository){this.repository=repository;}
    public LiveData<OrderLifecycleState> getState(){return state;}
    public void getOrderItems(String id, OrderRepository.ItemCallback callback) { repository.getOrderItems(id, callback); }
    public LiveData<List<Order>> observeCustomerOrders(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()) {
            return new MutableLiveData<>(java.util.Collections.emptyList());
        }
        return repository.observeCustomerOrderPage(ownerId);
    }
    public LiveData<List<Order>> observeReviewOrders(String ownerId) {
        return ownerId == null || ownerId.trim().isEmpty()
                ? new MutableLiveData<>(java.util.Collections.emptyList()) : repository.observeOrders(ownerId);
    }
    public void refreshReviewOrders(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()) return;
        repository.refreshOrders(ownerId, new OrderRepository.ActionCallback() {
            public void onSuccess() { }
            public void onError(OrderRepository.ErrorCode error, String message) {
                state.postValue(OrderLifecycleState.error(message));
            }
        });
    }
    public void refreshCustomerOrders(String ownerId) {
        loadCustomerPage(ownerId, true);
    }
    private boolean customerPageLoading;
    public boolean isCustomerPageLoading() { return customerPageLoading; }
    public boolean hasMoreCustomerOrders(String ownerId) { return repository.hasMoreCustomerOrders(ownerId); }
    public void loadCustomerPage(String ownerId, boolean reset) {
        if (customerPageLoading) return;
        if (ownerId == null || ownerId.trim().isEmpty()) return;
        customerPageLoading = true;
        state.setValue(OrderLifecycleState.loading());
        repository.refreshCustomerOrderPage(ownerId, reset, new OrderRepository.ActionCallback() {
            @Override public void onSuccess() { customerPageLoading = false; state.postValue(OrderLifecycleState.idle()); }
            @Override public void onError(OrderRepository.ErrorCode error, String message) {
                customerPageLoading = false;
                state.postValue(OrderLifecycleState.error(message));
            }
        });
    }
    public LiveData<List<Order>> observeSellerOrders(String sellerId){return repository.observeSellerOrders(sellerId);}
    private boolean sellerPageLoading;
    public void refreshSellerOrders(String sellerId){loadSellerPage(sellerId, true);}
    public void loadMoreSellerOrders(String sellerId){loadSellerPage(sellerId, false);}
    private void loadSellerPage(String sellerId, boolean reset) {
        if (sellerPageLoading) return;
        sellerPageLoading = true;
        state.setValue(OrderLifecycleState.loading());
        OrderRepository.ActionCallback callback = new OrderRepository.ActionCallback() {
            public void onSuccess() { sellerPageLoading = false; state.postValue(OrderLifecycleState.idle()); }
            public void onError(OrderRepository.ErrorCode code, String message) {
                sellerPageLoading = false; state.postValue(OrderLifecycleState.error(message));
            }
        };
        if (reset) repository.refreshSellerOrders(sellerId, callback);
        else repository.loadMoreSellerOrders(sellerId, callback);
    }
    public boolean hasMoreSellerOrders(String sellerId){return repository.hasMoreSellerOrders(sellerId);}
    public void load(String id){state.setValue(OrderLifecycleState.loading());repository.getOrder(id,callback());}
    public void observeOrder(String id){state.setValue(OrderLifecycleState.loading());repository.observeOrder(id,callback());}
    public void stopObservingOrder(String id){repository.stopObservingOrder(id);}
    public void transition(String id,OrderStatus target){state.setValue(OrderLifecycleState.loading());repository.transitionOrder(id,target,callback());}
    public void updateShipmentDetails(String id, String carrier, String tracking,
            OrderRepository.ActionCallback callback) {
        repository.updateShipmentDetails(id, carrier, tracking, callback);
    }
    public boolean canConfirmGuestReceipt(String id) { return repository.canConfirmGuestReceipt(id); }
    public void confirmTransfer(String id,String reference){state.setValue(OrderLifecycleState.loading());repository.confirmBankTransfer(id,reference,callback());}
    private OrderRepository.LifecycleCallback callback(){return new OrderRepository.LifecycleCallback(){@Override public void onSuccess(Order o,Payment p,Shipment s,boolean replay){state.postValue(OrderLifecycleState.success(o,p,s,replay));}@Override public void onError(OrderRepository.ErrorCode e,String message){state.postValue(OrderLifecycleState.error(message));}};}
    public void findGuestOrder(String orderCode, String receiverPhone, OrderRepository.OrderCallback callback) {
        state.setValue(OrderLifecycleState.loading());
        repository.findGuestOrder(orderCode, receiverPhone, new OrderRepository.OrderCallback() {
            @Override
            public void onSuccess(Order order, boolean idempotentReplay) {
                state.postValue(OrderLifecycleState.idle());
                callback.onSuccess(order, idempotentReplay);
            }

            @Override
            public void onError(OrderRepository.ErrorCode error, String message) {
                state.postValue(OrderLifecycleState.error(message));
                callback.onError(error, message);
            }
        });
    }
    @Override protected void onCleared(){repository.close();}
}
