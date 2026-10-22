package com.rescuefarm.data.local.mapper;

import com.rescuefarm.data.local.entity.OrderCacheEntity;
import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderOwnerType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;
import com.rescuefarm.domain.model.Order;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class OrderCacheMapper {
    private OrderCacheMapper() { }

    public static OrderCacheEntity toEntity(Order value, long cachedAt) {
        OrderCacheEntity entity = new OrderCacheEntity(value.getId());
        entity.ownerType = value.getOwnerType().name(); entity.ownerId = value.getOwnerId();
        entity.orderCode = value.getOrderCode(); entity.sellerId = value.getSellerId();
        entity.campaignId = value.getCampaignId(); entity.fulfillmentType = value.getFulfillmentType().name();
        entity.receiverName = value.getReceiverName(); entity.receiverPhone = value.getReceiverPhone();
        entity.receiverAddress = value.getReceiverAddress(); entity.receiverLatitude = value.getReceiverLatitude();
        entity.receiverLongitude = value.getReceiverLongitude(); entity.subtotal = value.getSubtotal();
        entity.quantityDiscount = value.getQuantityDiscount(); entity.shippingFee = value.getShippingFee();
        entity.totalAmount = value.getTotalAmount(); entity.paymentMethod = value.getPaymentMethod().name();
        entity.paymentStatus = value.getPaymentStatus().name(); entity.status = value.getStatus().name();
        entity.note = value.getNote(); entity.createdAtEpochMillis = millis(value.getCreatedAt());
        entity.expiryReviewRequired = value.isExpiryReviewRequired(); entity.cancellationReason = value.getCancellationReason();
        entity.updatedAtEpochMillis = millis(value.getUpdatedAt()); entity.cachedAtEpochMillis = cachedAt;
        return entity;
    }

    public static Order toDomain(OrderCacheEntity entity) {
        return Order.restore(entity.id, value(OrderOwnerType.class, entity.ownerType, OrderOwnerType.GUEST),
                entity.ownerId, entity.orderCode, entity.sellerId, entity.campaignId,
                value(FulfillmentType.class, entity.fulfillmentType, FulfillmentType.PICKUP),
                entity.receiverName, entity.receiverPhone, entity.receiverAddress,
                entity.receiverLatitude, entity.receiverLongitude, entity.subtotal,
                entity.quantityDiscount, entity.shippingFee, entity.totalAmount,
                value(PaymentMethod.class, entity.paymentMethod, PaymentMethod.COD),
                value(PaymentStatus.class, entity.paymentStatus, PaymentStatus.UNPAID),
                value(OrderStatus.class, entity.status, OrderStatus.PENDING), entity.note,
                date(entity.createdAtEpochMillis), date(entity.updatedAtEpochMillis), new ArrayList<>())
                .withExpiryReview(entity.expiryReviewRequired, entity.cancellationReason);
    }

    public static List<Order> orders(List<OrderCacheEntity> entities) {
        List<Order> result = new ArrayList<>();
        if (entities != null) for (OrderCacheEntity entity : entities) {
            try { result.add(toDomain(entity)); } catch (RuntimeException ignored) { }
        }
        return result;
    }

    private static long millis(Date value) { return value == null ? 0L : value.getTime(); }
    private static Date date(long value) { return value <= 0L ? null : new Date(value); }
    private static <T extends Enum<T>> T value(Class<T> type, String raw, T fallback) {
        try { return Enum.valueOf(type, raw == null ? "" : raw); }
        catch (IllegalArgumentException error) { return fallback; }
    }
}
