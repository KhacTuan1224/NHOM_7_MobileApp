package com.rescuefarm.service;

import com.rescuefarm.domain.enums.*;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderPricingCompatibilityTest {
    @Test public void legacyOrderKeepsAuthoritativeTotalIncludingItsHistoricalAdjustment() {
        Order order = Order.restore("old", OrderOwnerType.CUSTOMER, "customer", "RF-OLD", "seller", "",
                FulfillmentType.PICKUP, "Khách hàng", "0900000000", "", 0, 0,
                120000, 6000, 0, 114000, PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING,
                OrderStatus.PENDING, "", new Date(), new Date(), Collections.emptyList());
        assertEquals(114000, order.getTotalAmount(), 0);
        assertEquals(114000, order.calculateTotal(), 0);
        assertEquals(6000, order.getQuantityDiscount(), 0);
    }
    @Test public void newOrderHasNoDiscountAtTheFormerVolumeThreshold() {
        OrderItem item = OrderItem.snapshot("item", "new", "product", "batch", "Rau", "", "kg", 20000, 15000, 15000, 10);
        Order order = Order.create("new", OrderOwnerType.GUEST, "guest", "RF-NEW", "seller", "",
                FulfillmentType.DELIVERY, "Khách hàng", "0900000000", "Địa chỉ", 10.8, 106.7,
                150000, 15000, PaymentMethod.COD, PaymentStatus.UNPAID, "", Collections.singletonList(item), new Date());
        assertEquals(0, order.getQuantityDiscount(), 0);
        assertEquals(165000, order.calculateTotal(), 0);
    }
}
