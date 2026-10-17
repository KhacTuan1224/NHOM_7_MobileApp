package com.rescuefarm.service;

import com.rescuefarm.domain.enums.*;
import com.rescuefarm.service.order.OrderLifecycleService;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrderLifecycleServiceTest {
    private final OrderLifecycleService service = new OrderLifecycleService();

    @Test public void sellerCannotCompleteReceiptAndCannotConfirmUnpaidBankOrder() {
        assertFalse(service.getSellerNextStatuses(FulfillmentType.DELIVERY, OrderStatus.SHIPPING,
                PaymentMethod.COD, PaymentStatus.UNPAID).contains(OrderStatus.DELIVERED));
        assertFalse(service.getSellerNextStatuses(FulfillmentType.PICKUP, OrderStatus.READY_FOR_PICKUP,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID).contains(OrderStatus.DELIVERED));
        assertFalse(service.getSellerNextStatuses(FulfillmentType.DELIVERY, OrderStatus.PENDING,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING).contains(OrderStatus.CONFIRMED));
    }

    @Test public void buyerCanReceiveOnlyWhenGoodsAreReadyAndTransferIsPaid() {
        assertTrue(service.canConfirmReceipt(FulfillmentType.DELIVERY, OrderStatus.SHIPPING,
                PaymentMethod.COD, PaymentStatus.UNPAID));
        assertFalse(service.canConfirmReceipt(FulfillmentType.DELIVERY, OrderStatus.PREPARING,
                PaymentMethod.COD, PaymentStatus.UNPAID));
        assertFalse(service.canConfirmReceipt(FulfillmentType.DELIVERY, OrderStatus.SHIPPING,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING));
        assertTrue(service.canConfirmReceipt(FulfillmentType.DELIVERY, OrderStatus.SHIPPING,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID));
        assertFalse(service.canConfirmReceipt(FulfillmentType.DELIVERY, OrderStatus.DELIVERED,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID));
    }

    @Test public void deliveryFollowsShippingPath() {
        legal(FulfillmentType.DELIVERY, OrderStatus.PENDING, OrderStatus.CONFIRMED);
        legal(FulfillmentType.DELIVERY, OrderStatus.CONFIRMED, OrderStatus.PREPARING);
        legal(FulfillmentType.DELIVERY, OrderStatus.PREPARING, OrderStatus.SHIPPING);
        legal(FulfillmentType.DELIVERY, OrderStatus.SHIPPING, OrderStatus.DELIVERED);
    }

    @Test public void pickupUsesReadyForPickup() {
        legal(FulfillmentType.PICKUP, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);
        legal(FulfillmentType.PICKUP, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERED);
        illegal(FulfillmentType.PICKUP, OrderStatus.PREPARING, OrderStatus.SHIPPING);
    }

    @Test public void onsiteCompletesWithoutShipment() {
        legal(FulfillmentType.ON_SITE_RESCUE, OrderStatus.PREPARING, OrderStatus.DELIVERED);
        assertFalse(service.requiresShipment(FulfillmentType.ON_SITE_RESCUE));
        assertTrue(service.requiresShipment(FulfillmentType.DELIVERY));
    }

    @Test public void terminalStateCannotMutateAndSameStateIsReplay() {
        assertTrue(service.isReplay(OrderStatus.DELIVERED, OrderStatus.DELIVERED));
        illegal(FulfillmentType.DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    }

    @Test public void bankTransferNeedsManualConfirmationBeforeDelivery() {
        try {
            service.requireLegalTransition(FulfillmentType.PICKUP, OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.DELIVERED, PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING);
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("BANK_TRANSFER_NOT_CONFIRMED", expected.getMessage());
        }
    }

    @Test public void cashMethodsBecomePaidOnlyAtDelivery() {
        assertEquals(PaymentStatus.PAID, service.paymentStatusForTerminal(PaymentMethod.COD,
                PaymentStatus.UNPAID, OrderStatus.DELIVERED));
        assertEquals(PaymentStatus.PAID, service.paymentStatusForTerminal(PaymentMethod.CASH_ON_SITE,
                PaymentStatus.UNPAID, OrderStatus.DELIVERED));
    }

    @Test public void cancellationFailsOnlyUnpaidPayment() {
        assertEquals(PaymentStatus.FAILED, service.paymentStatusForTerminal(PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING, OrderStatus.CANCELLED));
    }

    @Test public void fulfillmentPaymentMatrixIsEnforced() {
        service.requirePaymentCompatible(FulfillmentType.DELIVERY, PaymentMethod.COD);
        service.requirePaymentCompatible(FulfillmentType.DELIVERY, PaymentMethod.BANK_TRANSFER);
        service.requirePaymentCompatible(FulfillmentType.PICKUP, PaymentMethod.CASH_ON_SITE);
        service.requirePaymentCompatible(FulfillmentType.ON_SITE_RESCUE, PaymentMethod.CASH_ON_SITE);
        try { service.requirePaymentCompatible(FulfillmentType.DELIVERY, PaymentMethod.CASH_ON_SITE); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("PAYMENT_FULFILLMENT_MISMATCH", expected.getMessage()); }
        try { service.requirePaymentCompatible(FulfillmentType.PICKUP, PaymentMethod.COD); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("PAYMENT_FULFILLMENT_MISMATCH", expected.getMessage()); }
    }

    private void legal(FulfillmentType f, OrderStatus from, OrderStatus to) {
        service.requireLegalTransition(f, from, to, PaymentMethod.COD, PaymentStatus.UNPAID);
    }
    private void illegal(FulfillmentType f, OrderStatus from, OrderStatus to) {
        try { legal(f, from, to); fail(); } catch (IllegalStateException expected) { }
    }
}
