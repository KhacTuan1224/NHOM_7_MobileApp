package com.rescuefarm.service.order;

import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import com.rescuefarm.domain.enums.ShipmentStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure lifecycle policy shared by the repository, UI and unit tests. */
public final class OrderLifecycleService {
    public List<OrderStatus> getSellerNextStatuses(FulfillmentType fulfillment, OrderStatus current,
            PaymentMethod method, PaymentStatus payment) {
        List<OrderStatus> result = new ArrayList<>(getAllowedNextStatuses(fulfillment, current, method, payment));
        result.remove(OrderStatus.DELIVERED);
        return Collections.unmodifiableList(result);
    }

    public boolean canConfirmReceipt(FulfillmentType fulfillment, OrderStatus current,
            PaymentMethod method, PaymentStatus payment) {
        try {
            if (current == OrderStatus.DELIVERED) return false;
            requireLegalTransition(fulfillment, current, OrderStatus.DELIVERED, method, payment);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) { return false; }
    }
    /** Returns only transitions allowed by the same policy enforced during writes. */
    public List<OrderStatus> getAllowedNextStatuses(FulfillmentType fulfillment, OrderStatus current,
            PaymentMethod paymentMethod, PaymentStatus paymentStatus) {
        if (fulfillment == null || current == null) return Collections.emptyList();
        List<OrderStatus> allowed = new ArrayList<>();
        for (OrderStatus candidate : OrderStatus.values()) {
            if (candidate == current) continue;
            try {
                requireLegalTransition(fulfillment, current, candidate, paymentMethod, paymentStatus);
                allowed.add(candidate);
            } catch (IllegalArgumentException | IllegalStateException ignored) { }
        }
        return Collections.unmodifiableList(allowed);
    }

    public boolean isReplay(OrderStatus current, OrderStatus target) {
        return current != null && current == target;
    }

    public void requireLegalTransition(FulfillmentType fulfillment, OrderStatus current,
            OrderStatus target, PaymentMethod paymentMethod, PaymentStatus paymentStatus) {
        if (fulfillment == null || current == null || target == null) {
            throw new IllegalArgumentException("Order lifecycle data is required");
        }
        if (current == target) return;
        boolean legal;
        switch (current) {
            case PENDING: legal = target == OrderStatus.CONFIRMED || target == OrderStatus.CANCELLED; break;
            case CONFIRMED: legal = target == OrderStatus.PREPARING || target == OrderStatus.CANCELLED; break;
            case PREPARING:
                legal = target == OrderStatus.CANCELLED
                        || (fulfillment == FulfillmentType.DELIVERY && target == OrderStatus.SHIPPING)
                        || (fulfillment == FulfillmentType.PICKUP && target == OrderStatus.READY_FOR_PICKUP)
                        || (fulfillment == FulfillmentType.ON_SITE_RESCUE && target == OrderStatus.DELIVERED);
                break;
            case READY_FOR_PICKUP: legal = target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED; break;
            case SHIPPING: legal = target == OrderStatus.DELIVERED; break;
            default: legal = false;
        }
        if (!legal) throw new IllegalStateException("ILLEGAL_ORDER_TRANSITION");
        if (paymentMethod == PaymentMethod.BANK_TRANSFER && paymentStatus != PaymentStatus.PAID
                && target != OrderStatus.CANCELLED && target != OrderStatus.DELIVERED)
            throw new IllegalStateException("BANK_TRANSFER_NOT_CONFIRMED");
        if (target == OrderStatus.DELIVERED && paymentMethod == PaymentMethod.BANK_TRANSFER
                && paymentStatus != PaymentStatus.PAID) {
            throw new IllegalStateException("BANK_TRANSFER_NOT_CONFIRMED");
        }
        if (target == OrderStatus.CANCELLED && paymentStatus == PaymentStatus.PAID) {
            throw new IllegalStateException("PAID_ORDER_REQUIRES_REFUND");
        }
    }

    public boolean requiresShipment(FulfillmentType fulfillment) {
        return fulfillment == FulfillmentType.DELIVERY;
    }

    public void requirePaymentCompatible(FulfillmentType fulfillment, PaymentMethod method) {
        if (fulfillment == null || method == null) throw new IllegalArgumentException("Payment and fulfillment are required");
        boolean valid = method == PaymentMethod.BANK_TRANSFER
                || (fulfillment == FulfillmentType.DELIVERY && method == PaymentMethod.COD)
                || (fulfillment != FulfillmentType.DELIVERY && method == PaymentMethod.CASH_ON_SITE);
        if (!valid) throw new IllegalArgumentException("PAYMENT_FULFILLMENT_MISMATCH");
    }

    public ShipmentStatus shipmentStatusFor(OrderStatus orderStatus) {
        if (orderStatus == OrderStatus.PREPARING) return ShipmentStatus.READY;
        if (orderStatus == OrderStatus.SHIPPING) return ShipmentStatus.SHIPPING;
        if (orderStatus == OrderStatus.DELIVERED) return ShipmentStatus.DELIVERED;
        if (orderStatus == OrderStatus.CANCELLED) return ShipmentStatus.CANCELLED;
        return ShipmentStatus.PENDING;
    }

    public PaymentStatus paymentStatusForTerminal(PaymentMethod method, PaymentStatus current,
            OrderStatus target) {
        if (target == OrderStatus.DELIVERED
                && (method == PaymentMethod.COD || method == PaymentMethod.CASH_ON_SITE)) {
            return PaymentStatus.PAID;
        }
        if (target == OrderStatus.CANCELLED && current != PaymentStatus.PAID) {
            return PaymentStatus.FAILED;
        }
        return current;
    }
}
