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
        return repository.observeOrders(ownerId);
    }
    public void refreshCustomerOrders(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()) return;
        state.setValue(OrderLifecycleState.loading());
        repository.refreshOrders(ownerId, new OrderRepository.ActionCallback() {
            @Override public void onSuccess() { state.postValue(OrderLifecycleState.idle()); }
            @Override public void onError(OrderRepository.ErrorCode error, String message) {
                state.postValue(OrderLifecycleState.error(message));
            }
        });
    }
    public LiveData<List<Order>> observeSellerOrders(String sellerId){return repository.observeSellerOrders(sellerId);}
    public void refreshSellerOrders(String sellerId){repository.refreshSellerOrders(sellerId,new OrderRepository.ActionCallback(){@Override public void onSuccess(){}@Override public void onError(OrderRepository.ErrorCode e,String message){state.postValue(OrderLifecycleState.error(message));}});}
    public void load(String id){state.setValue(OrderLifecycleState.loading());repository.getOrder(id,callback());}
    public void observeOrder(String id){state.setValue(OrderLifecycleState.loading());repository.observeOrder(id,callback());}
    public void stopObservingOrder(String id){repository.stopObservingOrder(id);}
    public void transition(String id,OrderStatus target){state.setValue(OrderLifecycleState.loading());repository.transitionOrder(id,target,callback());}
    public void updateShipmentDetails(String id, String carrier, String tracking,
            OrderRepository.ActionCallback callback) {
        repository.updateShipmentDetails(id, carrier, tracking, callback);
    }
    public void confirmTransfer(String id,String reference){state.setValue(OrderLifecycleState.loading());repository.confirmBankTransfer(id,reference,callback());}
    private OrderRepository.LifecycleCallback callback(){return new OrderRepository.LifecycleCallback(){@Override public void onSuccess(Order o,Payment p,Shipment s,boolean replay){state.postValue(OrderLifecycleState.success(o,p,s,replay));}@Override public void onError(OrderRepository.ErrorCode e,String message){state.postValue(OrderLifecycleState.error(message));}};}
    @Override protected void onCleared(){repository.close();}
}
