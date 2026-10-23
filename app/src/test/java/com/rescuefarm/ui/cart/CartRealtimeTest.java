package com.rescuefarm.ui.cart;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.rescuefarm.data.repository.CartRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.CartOwnerType;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.model.Cart;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import org.junit.Rule;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CartRealtimeTest {
    @Rule public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Test public void remotePriceStockAndDeletionUpdateDisplayWithoutWritingBackOrChangingQuantity() {
        MutableLiveData<Cart> remoteCart = new MutableLiveData<>();
        MutableLiveData<List<Product>> products = new MutableLiveData<>(Collections.emptyList());
        MutableLiveData<List<ProductBatch>> batches = new MutableLiveData<>(Collections.emptyList());
        AtomicInteger writes = new AtomicInteger();
        CartRepository carts = (CartRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{CartRepository.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "observeCart": return remoteCart;
                case "getOwnerType": return CartOwnerType.CUSTOMER;
                case "getOwnerKey": return "buyer";
                case "close": return null;
                default: writes.incrementAndGet(); throw new AssertionError("Snapshot must not write: " + method.getName());
            }
        });
        ProductRepository catalog = (ProductRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ProductRepository.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "observeProducts": return products;
                case "observeBatches": return batches;
                case "close": return null;
                default: throw new AssertionError("Unexpected request: " + method.getName());
            }
        });
        CartViewModel vm = new CartViewModel(carts, catalog);
        Observer<Cart> observer = value -> { };
        vm.getCart().observeForever(observer);
        try {
            CartItem item = new CartItem("item", "product", "batch", "seller", 2, 80, true);
            remoteCart.setValue(new Cart("buyer", CartOwnerType.CUSTOMER, "buyer", Collections.singletonList(item), new Date()));
            batches.setValue(Collections.singletonList(batch(10)));
            products.setValue(Collections.singletonList(product(80)));
            assertEquals(160, vm.getCart().getValue().calculateSubtotal(), .001);
            products.setValue(Collections.singletonList(product(90)));
            assertEquals(180, vm.getCart().getValue().calculateSubtotal(), .001);
            assertEquals(80, remoteCart.getValue().getItems().get(0).getUnitPrice(), .001);
            assertEquals(2, vm.getCart().getValue().getItems().get(0).getQuantity(), .001);
            batches.setValue(Collections.singletonList(batch(1)));
            assertTrue(vm.getCartWarnings().getValue().containsKey("item"));
            batches.setValue(Collections.singletonList(batch(10)));
            assertTrue(vm.getCartWarnings().getValue().isEmpty());
            products.setValue(Collections.emptyList());
            assertTrue(vm.getCartWarnings().getValue().containsKey("item"));
            assertEquals(0, writes.get());
            remoteCart.setValue(Cart.empty(CartOwnerType.CUSTOMER, "buyer"));
            assertTrue(vm.getCart().getValue().getItems().isEmpty());
            assertTrue(vm.getCartWarnings().getValue().isEmpty());
        } finally { vm.getCart().removeObserver(observer); }
    }
    private Product product(double price) {
        return new Product("product", "seller", "category", "Rau", "", 100, price, "kg", "", "", Collections.emptyList(), ProductStatus.ACTIVE);
    }
    private ProductBatch batch(double stock) {
        return ProductBatch.restore("batch", "product", null, new Date(1), new Date(System.currentTimeMillis() + 3600000),
                10, stock, 0, 10 - stock, BatchStatus.AVAILABLE, 1);
    }
}
