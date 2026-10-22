package com.rescuefarm.data;

import com.rescuefarm.data.local.entity.OrderCacheEntity;
import com.rescuefarm.data.local.mapper.OrderCacheMapper;
import com.rescuefarm.domain.enums.*;
import com.rescuefarm.domain.model.Order;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderCacheMapperTest {
    @Test public void roundTripPreservesReadOnlyOrderSnapshot() {
        Order source = Order.restore("order-1", OrderOwnerType.CUSTOMER, "customer-1", "RF-1",
                "seller-1", "", FulfillmentType.DELIVERY, "Nguyen Van A", "0900000000",
                "HCM", 10D, 106D, 100D, 5D, 10D, 105D, PaymentMethod.COD,
                PaymentStatus.UNPAID, OrderStatus.CONFIRMED, "note", new Date(1000L),
                new Date(2000L), Collections.emptyList()).withExpiryReview(true, "BATCH_EXPIRED");
        OrderCacheEntity entity = OrderCacheMapper.toEntity(source, 3000L);
        Order restored = OrderCacheMapper.toDomain(entity);
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getOwnerId(), restored.getOwnerId());
        assertEquals(source.getSellerId(), restored.getSellerId());
        assertEquals(source.getStatus(), restored.getStatus());
        assertEquals(source.getPaymentStatus(), restored.getPaymentStatus());
        assertTrue(restored.isExpiryReviewRequired());
        assertEquals("BATCH_EXPIRED", restored.getCancellationReason());
        assertEquals(105D, restored.getTotalAmount(), 0.001D);
        assertEquals(3000L, entity.cachedAtEpochMillis);
    }
}
