package com.rescuefarm.ui.order;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.domain.model.Order;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class CustomerOrderHistoryTest {
    @Rule public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private final MutableLiveData<List<Order>> cached =
            new MutableLiveData<>(Collections.emptyList());
    private String observedOwner;
    private String refreshedOwner;
    private OrderRepository.ActionCallback refreshCallback;
    private final OrderRepository repository = (OrderRepository) Proxy.newProxyInstance(
            OrderRepository.class.getClassLoader(), new Class<?>[]{OrderRepository.class},
            (proxy, method, args) -> {
                if (method.getName().equals("observeOrders")) {
                    observedOwner = (String) args[0];
                    return cached;
                }
                if (method.getName().equals("refreshOrders")) {
                    refreshedOwner = (String) args[0];
                    refreshCallback = (OrderRepository.ActionCallback) args[1];
                    return null;
                }
                throw new AssertionError("Unexpected repository call: " + method.getName());
            });

    @Test public void customerHistoryUsesOwnerQueryAndKeepsCacheOnNetworkFailure() {
        OrderViewModel vm = new OrderViewModel(repository);
        assertSame(cached, vm.observeCustomerOrders("customer-1"));
        vm.refreshCustomerOrders("customer-1");
        assertEquals("customer-1", observedOwner);
        assertEquals("customer-1", refreshedOwner);
        assertEquals(OrderLifecycleState.Status.LOADING, vm.getState().getValue().getStatus());
        List<Order> previous = cached.getValue();
        refreshCallback.onError(OrderRepository.ErrorCode.NETWORK, "Offline");
        assertSame(previous, cached.getValue());
        assertEquals(OrderLifecycleState.Status.ERROR, vm.getState().getValue().getStatus());
        assertEquals("Offline", vm.getState().getValue().getMessage());
        vm.refreshCustomerOrders("customer-1");
        refreshCallback.onSuccess();
        assertEquals(OrderLifecycleState.Status.IDLE, vm.getState().getValue().getStatus());
        assertEquals("", vm.getState().getValue().getMessage());
    }

    @Test public void missingOwnerNeverQueriesOrRefreshesOrders() {
        OrderViewModel vm = new OrderViewModel(repository);
        assertTrue(vm.observeCustomerOrders(null).getValue().isEmpty());
        assertTrue(vm.observeCustomerOrders(" ").getValue().isEmpty());
        vm.refreshCustomerOrders(null);
        vm.refreshCustomerOrders("");
        assertNull(observedOwner);
        assertNull(refreshedOwner);
    }
}
