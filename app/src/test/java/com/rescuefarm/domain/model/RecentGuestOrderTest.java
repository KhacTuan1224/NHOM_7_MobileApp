package com.rescuefarm.domain.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class RecentGuestOrderTest {

    @Test
    public void defaultConstructorSetsSensibleDefaults() {
        RecentGuestOrder order = new RecentGuestOrder();
        assertEquals("", order.getOrderId());
        assertEquals("", order.getOrderCode());
        assertEquals("", order.getPhone());
        assertEquals(0.0, order.getTotalAmount(), 0.001);
        assertEquals("PENDING", order.getStatus());
        assertEquals("COD", order.getPaymentMethod());
        assertTrue(order.getCreatedAtMillis() > 0);
    }

    @Test
    public void parameterizedConstructorTrimsAndHandlesNulls() {
        RecentGuestOrder order = new RecentGuestOrder(" order-1 ", " RF-1001 ", " 0901234567 ",
                150000.0, null, null, 123456789L);
        assertEquals("order-1", order.getOrderId());
        assertEquals("RF-1001", order.getOrderCode());
        assertEquals("0901234567", order.getPhone());
        assertEquals(150000.0, order.getTotalAmount(), 0.001);
        assertEquals("PENDING", order.getStatus());
        assertEquals("COD", order.getPaymentMethod());
        assertEquals(123456789L, order.getCreatedAtMillis());
    }

    @Test
    public void toJsonStringAndFromJsonStringRoundTrip() {
        RecentGuestOrder original = new RecentGuestOrder(
                "ord-99", "RF-20261021-001", "0987654321",
                250000.0, "CONFIRMED", "BANK_TRANSFER", 1700000000000L
        );
        String json = original.toJsonString();
        assertNotNull(json);

        RecentGuestOrder restored = RecentGuestOrder.fromJsonString(json);
        assertNotNull(restored);
        assertEquals(original.getOrderId(), restored.getOrderId());
        assertEquals(original.getOrderCode(), restored.getOrderCode());
        assertEquals(original.getPhone(), restored.getPhone());
        assertEquals(original.getTotalAmount(), restored.getTotalAmount(), 0.001);
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getPaymentMethod(), restored.getPaymentMethod());
        assertEquals(original.getCreatedAtMillis(), restored.getCreatedAtMillis());
    }

    @Test
    public void listToJsonAndListFromJsonRoundTrip() {
        List<RecentGuestOrder> list = new ArrayList<>();
        list.add(new RecentGuestOrder("1", "RF-001", "0901", 10000, "PENDING", "COD", 100L));
        list.add(new RecentGuestOrder("2", "RF-002", "0902", 20000, "SHIPPING", "COD", 200L));
        list.add(new RecentGuestOrder("3", "RF-003", "0903", 30000, "DELIVERED", "BANK_TRANSFER", 300L));

        String serialized = RecentGuestOrder.listToJson(list);
        assertNotNull(serialized);
        assertTrue(serialized.contains("RF-001"));
        assertTrue(serialized.contains("RF-002"));
        assertTrue(serialized.contains("RF-003"));

        List<RecentGuestOrder> parsed = RecentGuestOrder.listFromJson(serialized);
        assertEquals(3, parsed.size());
        assertEquals("RF-001", parsed.get(0).getOrderCode());
        assertEquals("RF-002", parsed.get(1).getOrderCode());
        assertEquals("RF-003", parsed.get(2).getOrderCode());
    }

    @Test
    public void listFromJsonHandlesNullAndEmptyString() {
        List<RecentGuestOrder> empty = RecentGuestOrder.listFromJson(null);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        empty = RecentGuestOrder.listFromJson("");
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        empty = RecentGuestOrder.listFromJson("[]");
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        empty = RecentGuestOrder.listFromJson("invalid json");
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
    }

    @Test
    public void listFromJsonFiltersOutEntriesWithoutOrderCode() {
        String json = "[{\"orderId\":\"1\"},{\"orderId\":\"2\",\"orderCode\":\"RF-VALID\"}]";
        List<RecentGuestOrder> parsed = RecentGuestOrder.listFromJson(json);
        assertEquals(1, parsed.size());
        assertEquals("RF-VALID", parsed.get(0).getOrderCode());
    }
}
