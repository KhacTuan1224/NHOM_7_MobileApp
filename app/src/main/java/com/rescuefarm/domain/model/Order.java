package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.FulfillmentType;
import com.rescuefarm.domain.enums.OrderOwnerType;
import com.rescuefarm.domain.enums.OrderStatus;
import com.rescuefarm.domain.enums.PaymentMethod;
import com.rescuefarm.domain.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Order {
    private boolean expiryReviewRequired;
    private String cancellationReason = "";
    public Order withExpiryReview(boolean required, String reason) {
        expiryReviewRequired = required; cancellationReason = reason == null ? "" : reason; return this;
    }
    public boolean isExpiryReviewRequired() { return expiryReviewRequired; }
    public String getCancellationReason() { return cancellationReason; }
    private double sellerVolumeDiscount, platformVolumeDiscount;
    public Order withVolumeDiscounts(double seller, double platform) {
        sellerVolumeDiscount = Math.max(0, seller); platformVolumeDiscount = Math.max(0, platform); return this;
    }
    public double getSellerVolumeDiscount() { return sellerVolumeDiscount; }
    public double getPlatformVolumeDiscount() { return platformVolumeDiscount; }
    private java.util.List<Order> checkoutOrders;
    public void setCheckoutOrders(java.util.List<Order> values) { checkoutOrders = new java.util.ArrayList<>(values); }
    public java.util.List<Order> getCheckoutOrders() {
        return checkoutOrders == null ? java.util.Collections.singletonList(this) : new java.util.ArrayList<>(checkoutOrders);
    }
    private String id;
    private OrderOwnerType ownerType;
    private String ownerId;
    private String orderCode;
    private String sellerId;
    private String campaignId;
    private FulfillmentType fulfillmentType;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private double receiverLatitude;
    private double receiverLongitude;
    private double subtotal;
    private double quantityDiscount;
    private double shippingFee;
    private double totalAmount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private OrderStatus status;
    private String note;
    private Date createdAt;
    private Date updatedAt;
    private List<OrderItem> items;

    public Order() {
        items = new ArrayList<>();
        status = OrderStatus.PENDING;
        paymentStatus = PaymentStatus.UNPAID;
    }

    public static Order create(String id, OrderOwnerType ownerType, String ownerId, String orderCode,
            String sellerId, String campaignId, FulfillmentType fulfillmentType,
            String receiverName, String receiverPhone, String receiverAddress,
            double receiverLatitude, double receiverLongitude, double subtotal,
            double quantityDiscount, double shippingFee, PaymentMethod paymentMethod,
            PaymentStatus paymentStatus, String note, List<OrderItem> items, Date now) {
        if (ownerType == null || fulfillmentType == null || paymentMethod == null) {
            throw new IllegalArgumentException("Order type, fulfillment and payment are required");
        }
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Order requires items");
        Order value = new Order(); value.id = required(id, "Order id"); value.ownerType = ownerType;
        value.ownerId = required(ownerId, "Owner id"); value.orderCode = required(orderCode, "Order code");
        value.sellerId = required(sellerId, "Seller id"); value.campaignId = clean(campaignId);
        value.fulfillmentType = fulfillmentType; value.receiverName = required(receiverName, "Receiver name");
        value.receiverPhone = required(receiverPhone, "Receiver phone");
        value.receiverAddress = clean(receiverAddress); value.receiverLatitude = receiverLatitude;
        value.receiverLongitude = receiverLongitude; value.subtotal = nonNegative(subtotal, "Subtotal");
        value.quantityDiscount = nonNegative(quantityDiscount, "Quantity discount");
        value.shippingFee = nonNegative(shippingFee, "Shipping fee"); value.totalAmount = value.calculateTotal();
        value.paymentMethod = paymentMethod; value.paymentStatus = paymentStatus == null
                ? PaymentStatus.UNPAID : paymentStatus; value.status = OrderStatus.PENDING;
        value.note = clean(note); value.items = new ArrayList<>(items);
        value.createdAt = copy(now == null ? new Date() : now); value.updatedAt = copy(value.createdAt); return value;
    }

    public static Order restore(String id, OrderOwnerType ownerType, String ownerId, String orderCode,
            String sellerId, String campaignId, FulfillmentType fulfillmentType, String receiverName,
            String receiverPhone, String receiverAddress, double receiverLatitude, double receiverLongitude,
            double subtotal, double quantityDiscount, double shippingFee, double totalAmount,
            PaymentMethod paymentMethod, PaymentStatus paymentStatus, OrderStatus status, String note,
            Date createdAt, Date updatedAt, List<OrderItem> items) {
        Order value = new Order(); value.id = required(id, "Order id"); value.ownerType = ownerType;
        value.ownerId = required(ownerId, "Owner id"); value.orderCode = required(orderCode, "Order code");
        value.sellerId = required(sellerId, "Seller id"); value.campaignId = clean(campaignId);
        value.fulfillmentType = fulfillmentType; value.receiverName = required(receiverName, "Receiver name");
        value.receiverPhone = required(receiverPhone, "Receiver phone"); value.receiverAddress = clean(receiverAddress);
        value.receiverLatitude = receiverLatitude; value.receiverLongitude = receiverLongitude;
        value.subtotal = subtotal; value.quantityDiscount = quantityDiscount; value.shippingFee = shippingFee;
        value.totalAmount = totalAmount; value.paymentMethod = paymentMethod; value.paymentStatus = paymentStatus;
        value.status = status == null ? OrderStatus.PENDING : status; value.note = clean(note);
        value.createdAt = copy(createdAt); value.updatedAt = copy(updatedAt);
        value.items = items == null ? new ArrayList<>() : new ArrayList<>(items); return value;
    }

    public double calculateTotal() { return Math.max(0.0, subtotal - quantityDiscount + shippingFee); }
    public boolean canCancel() { return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED || status == OrderStatus.PREPARING; }
    public boolean isDeliveryRequired() { return fulfillmentType == FulfillmentType.DELIVERY; }
    public String getId() { return id; }
    public OrderOwnerType getOwnerType() { return ownerType; }
    public String getOwnerId() { return ownerId; }
    public String getOrderCode() { return orderCode; }
    public String getSellerId() { return sellerId; }
    public String getCampaignId() { return campaignId; }
    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public String getReceiverAddress() { return receiverAddress; }
    public double getReceiverLatitude() { return receiverLatitude; }
    public double getReceiverLongitude() { return receiverLongitude; }
    public double getSubtotal() { return subtotal; }
    public double getQuantityDiscount() { return quantityDiscount; }
    public double getShippingFee() { return shippingFee; }
    public double getTotalAmount() { return totalAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public OrderStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return new ArrayList<>(items); }
    private static double nonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0D) throw new IllegalArgumentException(field + " is invalid"); return value;
    }
    private static String required(String value, String field) {
        String clean = clean(value); if (clean.isEmpty()) throw new IllegalArgumentException(field + " is required"); return clean;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
}
