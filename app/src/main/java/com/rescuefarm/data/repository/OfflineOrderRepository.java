package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.rescuefarm.data.local.dao.OrderCacheDao;
import com.rescuefarm.data.local.mapper.OrderCacheMapper;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.service.order.CheckoutRequest;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class OfflineOrderRepository implements OrderRepository {
    private static final String READ_MESSAGE = "Đang offline; danh sách đơn tiếp tục dùng Room cache.";
    private static final String WRITE_MESSAGE = "Thao tác đơn hàng quan trọng cần kết nối mạng và không được xếp hàng offline.";
    private final OrderCacheDao dao; private final ExecutorService executor;
    public OfflineOrderRepository(OrderCacheDao dao, ExecutorService executor) { this.dao=dao;this.executor=executor; }
    @Override public LiveData<List<Order>> observeOrders(String ownerId){return Transformations.map(
            dao.observeOwnerOrders(ownerId),OrderCacheMapper::orders);}
    @Override public LiveData<List<Order>> observeSellerOrders(String sellerId){return Transformations.map(
            dao.observeSellerOrders(sellerId),OrderCacheMapper::orders);}
    @Override public void checkout(CheckoutRequest request,OrderCallback callback){callback.onError(ErrorCode.NETWORK,WRITE_MESSAGE);}
    @Override public void refreshOrders(String ownerId,ActionCallback callback){callback.onError(ErrorCode.NETWORK,READ_MESSAGE);}
    @Override public void refreshSellerOrders(String sellerId,ActionCallback callback){callback.onError(ErrorCode.NETWORK,READ_MESSAGE);}
    @Override public void loadMoreSellerOrders(String sellerId,ActionCallback callback){callback.onError(ErrorCode.NETWORK,READ_MESSAGE);}
    @Override public void findGuestOrder(String code,String phone,OrderCallback callback){callback.onError(ErrorCode.NETWORK,"Tra cứu guest cần kết nối mạng; không thể tin cậy cache theo số điện thoại.");}
    @Override public void getOrder(String id,LifecycleCallback callback){callback.onError(ErrorCode.NETWORK,"Chi tiết payment/shipment cần kết nối mạng; danh sách cache chỉ để tham khảo.");}
    @Override public void observeOrder(String id,LifecycleCallback callback){
        callback.onError(ErrorCode.NETWORK,"Theo dõi trạng thái chi tiết cần kết nối mạng.");
    }
    @Override public void stopObservingOrder(String id) { }
    @Override public void transitionOrder(String id,OrderStatus target,LifecycleCallback callback){callback.onError(ErrorCode.NETWORK,WRITE_MESSAGE);}
    @Override public void confirmBankTransfer(String id,String reference,LifecycleCallback callback){callback.onError(ErrorCode.NETWORK,WRITE_MESSAGE);}
    @Override public void close(){executor.shutdownNow();}
}
