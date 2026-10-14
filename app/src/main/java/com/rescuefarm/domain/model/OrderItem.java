package com.rescuefarm.domain.model;

public class OrderItem {
    private String id;
    private String orderId;
    private String productId;
    private String batchId;
    private String productNameSnapshot;
    private String productImageSnapshot;
    private String unitSnapshot;
    private double originalPriceSnapshot;
    private double rescuePriceSnapshot;
    private double finalPriceSnapshot;
    private double quantity;
    private double subtotal;

    public OrderItem() { }

    public OrderItem(String productId, String batchId, String productNameSnapshot, String productImageSnapshot,
                     String unitSnapshot, double originalPriceSnapshot, double rescuePriceSnapshot,
                     double finalPriceSnapshot, double quantity) {
        if (quantity <= 0.0 || finalPriceSnapshot < 0.0) { throw new IllegalArgumentException("Order item quantity and final price are invalid"); }
        this.productId = productId;
        this.batchId = batchId;
        this.productNameSnapshot = productNameSnapshot;
        this.productImageSnapshot = productImageSnapshot;
        this.unitSnapshot = unitSnapshot;
        this.originalPriceSnapshot = originalPriceSnapshot;
        this.rescuePriceSnapshot = rescuePriceSnapshot;
        this.finalPriceSnapshot = finalPriceSnapshot;
        this.quantity = quantity;
        this.subtotal = calculateSubtotal();
    }

    public static OrderItem snapshot(String id, String orderId, String productId, String batchId,
            String productName, String productImage, String unit, double originalPrice,
            double rescuePrice, double finalPrice, double quantity) {
        OrderItem value = new OrderItem(productId, batchId, productName, productImage, unit,
                originalPrice, rescuePrice, finalPrice, quantity);
        value.id = required(id, "Order item id"); value.orderId = required(orderId, "Order id");
        return value;
    }

    public double calculateSubtotal() { return Math.round(finalPriceSnapshot * quantity); }
    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public String getBatchId() { return batchId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getProductImageSnapshot() { return productImageSnapshot; }
    public String getUnitSnapshot() { return unitSnapshot; }
    public double getOriginalPriceSnapshot() { return originalPriceSnapshot; }
    public double getRescuePriceSnapshot() { return rescuePriceSnapshot; }
    public double getFinalPriceSnapshot() { return finalPriceSnapshot; }
    public double getQuantity() { return quantity; }
    public double getSubtotal() { return subtotal; }
    private static String required(String value, String field) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException(field + " is required"); return clean;
    }
}
