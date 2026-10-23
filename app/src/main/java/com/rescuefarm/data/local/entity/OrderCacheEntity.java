package com.rescuefarm.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "order_cache", indices = {
        @Index(value = "ownerId"), @Index(value = "sellerId")
})
public class OrderCacheEntity {
    @PrimaryKey @NonNull public String id;
    public String ownerType;
    public String ownerId;
    public String orderCode;
    public String sellerId;
    public String campaignId;
    public String fulfillmentType;
    public String receiverName;
    public String receiverPhone;
    public String receiverAddress;
    public double receiverLatitude;
    public double receiverLongitude;
    public double subtotal;
    public double quantityDiscount;
    public double shippingFee;
    public double totalAmount;
    public String paymentMethod;
    public String paymentStatus;
    public String status;
    public String note;
    public boolean expiryReviewRequired;
    public String cancellationReason;
    public long createdAtEpochMillis;
    public long updatedAtEpochMillis;
    public long cachedAtEpochMillis;

    public OrderCacheEntity(@NonNull String id) { this.id = id; }
}
