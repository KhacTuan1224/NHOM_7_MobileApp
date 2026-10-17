package com.rescuefarm.ui.checkout;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.data.repository.OrderRepository;
import com.rescuefarm.data.repository.UnavailableUserRepository;
import com.rescuefarm.domain.enums.CartOwnerType;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.User;
import com.rescuefarm.service.order.CheckoutRequest;
import java.lang.reflect.Proxy;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class CheckoutRecipientLocationTest {
    @Rule public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private CheckoutRequest submitted;
    private String observedUser;
    private final MutableLiveData<User> customer = new MutableLiveData<>();

    private CheckoutViewModel model(CartOwnerType owner) {
        Cart cart = Cart.empty(owner, "current-customer");
        cart.addItem(new CartItem("item", "product", "batch", "seller", 1, 20000));
        CartRepository carts = (CartRepository) Proxy.newProxyInstance(
                CartRepository.class.getClassLoader(), new Class<?>[]{CartRepository.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getOwnerKey": return "current-customer";
                        case "getOwnerType": return owner;
                        case "observeCart": return new MutableLiveData<>(cart);
                        default: throw new AssertionError(method.getName());
                    }
                });
        OrderRepository orders = (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(), new Class<?>[]{OrderRepository.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("checkout")) throw new AssertionError(method.getName());
                    submitted = (CheckoutRequest) args[0];
                    return null;
                });
        return new CheckoutViewModel(carts, orders, new UnavailableUserRepository() {
            @Override public androidx.lifecycle.LiveData<User> observeUser(String id) {
                observedUser = id;
                return customer;
            }
        }, new SavedStateHandle());
    }

    @Test public void readsOnlyCurrentCustomersProfileForTheirSavedLocation() {
        assertSame(customer, model(CartOwnerType.CUSTOMER).observeCustomer());
        assertEquals("current-customer", observedUser);
    }

    @Test public void guestDoesNotReadCustomerProfile() {
        assertNull(model(CartOwnerType.GUEST).observeCustomer().getValue());
        assertNull(observedUser);
    }

    @Test public void checkoutUsesChosenRecipientsCoordinatesAndAddress() {
        model(CartOwnerType.CUSTOMER).submit("Nguyen An", "0399746618", "47 Ho Thi Ky, HCM",
                "10.763", "106.675", FulfillmentType.DELIVERY, PaymentMethod.COD, "", 20000, 15000);
        assertNotNull(submitted);
        assertEquals(10.763, submitted.latitude, 0.000001);
        assertEquals(106.675, submitted.longitude, 0.000001);
        assertEquals("47 Ho Thi Ky, HCM", submitted.receiverAddress);
        assertEquals("seller", submitted.items.get(0).getSellerId());
    }

    @Test public void missingRecipientCoordinatesCannotBecomeSellerLocation() {
        CheckoutViewModel vm = model(CartOwnerType.CUSTOMER);
        vm.submit("Nguyen An", "0399746618", "47 Ho Thi Ky, HCM", "0", "0",
                FulfillmentType.DELIVERY, PaymentMethod.COD, "");
        assertNull(submitted);
        assertEquals(CheckoutState.Status.ERROR, vm.getState().getValue().getStatus());
        assertFalse(vm.getState().getValue().getMessage().contains("Hãy lấy GPS"));
    }

    @Test public void storePickupDoesNotRequireRecipientCoordinates() {
        model(CartOwnerType.CUSTOMER).submit("Nguyen An", "0399746618", "", "0", "0",
                FulfillmentType.PICKUP, PaymentMethod.CASH_ON_SITE, "");
        assertNotNull(submitted);
    }
}
