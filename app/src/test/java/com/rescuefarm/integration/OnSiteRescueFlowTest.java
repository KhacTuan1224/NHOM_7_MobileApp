package com.rescuefarm.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderOwnerType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Order;
import com.rescuefarm.domain.model.OrderItem;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.inventory.InventoryService;
import com.rescuefarm.service.order.OrderLifecycleService;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;

/** Cross-domain contract for the five-minute mobile rescue demo. */
public class OnSiteRescueFlowTest {
    @Test public void mobileRescueOnSitePurchaseDelivered_marksQuantityAsRescued() {
        Date now = new Date();
        ProductBatch batch = ProductBatch.restore("batch-1", "product-1", "campaign-1",
                new Date(now.getTime() - 86_400_000L), new Date(now.getTime() + 86_400_000L),
                8D, 8D, 0D, 0D, BatchStatus.AVAILABLE, 1L);
        RescueCampaign campaign = RescueCampaign.restore("campaign-1", "seller-1", "Mobile rescue",
                "Rescue demo batch", RescueReason.NEAR_EXPIRY, UrgencyLevel.CRITICAL,
                RescueMode.MOBILE_POINT, Collections.singletonMap("batch-1", 3D),
                3D, 0D, 0D, new Date(now.getTime() - 60_000L),
                new Date(now.getTime() + 86_400_000L), 10D, 106D, 10D, 106D,
                now, true, "Demo pickup point", CampaignStatus.ACTIVE);
        campaign.updateCurrentLocation(10.01D, 106.01D, now);
        assertTrue("seller location is fresh for discovery", campaign.isLocationFresh(now));

        OrderItem item = OrderItem.snapshot("batch-1", "order-1", "product-1", "batch-1",
                "Rescue produce", "", "kg", 20D, 15D, 15D, 2D);
        Order order = Order.create("order-1", OrderOwnerType.CUSTOMER, "customer-1", "RFDEMO01",
                "seller-1", "campaign-1", FulfillmentType.ON_SITE_RESCUE,
                "Demo customer", "0900000000", "", 10D, 106D, 30D, 0D,
                PaymentMethod.CASH_ON_SITE, PaymentStatus.UNPAID, "", Collections.singletonList(item), now);
        OrderLifecycleService lifecycle = new OrderLifecycleService();
        InventoryService inventory = new InventoryService();
        lifecycle.requirePaymentCompatible(order.getFulfillmentType(), order.getPaymentMethod());

        inventory.reserveStock(batch, campaign, item.getQuantity());
        assertEquals(6D, batch.getAvailableQuantity(), 0.000001D);
        assertEquals(2D, campaign.getReservedQuantity(), 0.000001D);

        lifecycle.requireLegalTransition(order.getFulfillmentType(), OrderStatus.PENDING,
                OrderStatus.CONFIRMED, order.getPaymentMethod(), order.getPaymentStatus());
        lifecycle.requireLegalTransition(order.getFulfillmentType(), OrderStatus.CONFIRMED,
                OrderStatus.PREPARING, order.getPaymentMethod(), order.getPaymentStatus());
        lifecycle.requireLegalTransition(order.getFulfillmentType(), OrderStatus.PREPARING,
                OrderStatus.DELIVERED, order.getPaymentMethod(), order.getPaymentStatus());
        PaymentStatus paid = lifecycle.paymentStatusForTerminal(order.getPaymentMethod(),
                order.getPaymentStatus(), OrderStatus.DELIVERED);
        inventory.commitReservedStock(batch, campaign, item.getQuantity());

        assertEquals(PaymentStatus.PAID, paid);
        assertEquals("Rescue produce", item.getProductNameSnapshot());
        assertEquals(15D, item.getFinalPriceSnapshot(), 0.000001D);
        assertFalse("on-site rescue does not create a shipment",
                lifecycle.requiresShipment(order.getFulfillmentType()));
        assertEquals(0D, batch.getReservedQuantity(), 0.000001D);
        assertEquals(2D, batch.getSoldQuantity(), 0.000001D);
        assertEquals(0D, campaign.getReservedQuantity(), 0.000001D);
        assertEquals(2D, campaign.getRescuedQuantity(), 0.000001D);
        assertEquals(200D / 3D, campaign.calculateProgress(), 0.000001D);
    }

    @Test public void cancelledOnSiteOrder_releasesBatchAndCampaignReservation() {
        ProductBatch batch = new ProductBatch("batch-2", "product-2", 5D);
        batch.updateDetails(new java.util.Date(1), new java.util.Date(Long.MAX_VALUE), 5, new java.util.Date());
        RescueCampaign campaign = new RescueCampaign("campaign-2", "seller-1", 5D);
        InventoryService inventory = new InventoryService();
        OrderLifecycleService lifecycle = new OrderLifecycleService();

        inventory.reserveStock(batch, campaign, 2D);
        lifecycle.requireLegalTransition(FulfillmentType.ON_SITE_RESCUE, OrderStatus.PENDING,
                OrderStatus.CANCELLED, PaymentMethod.CASH_ON_SITE, PaymentStatus.UNPAID);
        PaymentStatus failed = lifecycle.paymentStatusForTerminal(PaymentMethod.CASH_ON_SITE,
                PaymentStatus.UNPAID, OrderStatus.CANCELLED);
        inventory.releaseReservedStock(batch, campaign, 2D);

        assertEquals(PaymentStatus.FAILED, failed);
        assertEquals(5D, batch.getAvailableQuantity(), 0.000001D);
        assertEquals(0D, batch.getReservedQuantity(), 0.000001D);
        assertEquals(0D, campaign.getReservedQuantity(), 0.000001D);
        assertEquals(0D, campaign.getRescuedQuantity(), 0.000001D);
    }
}
