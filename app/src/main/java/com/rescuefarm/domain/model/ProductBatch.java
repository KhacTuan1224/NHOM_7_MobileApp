package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.BatchStatus;

import java.util.Date;

public class ProductBatch {
    private static final double QUANTITY_TOLERANCE = 0.000001;

    private String id;
    private String productId;
    private String activeCampaignId;
    private Date harvestDate;
    private Date expiryDate;
    private double initialQuantity;
    private double availableQuantity;
    private double reservedQuantity;
    private double soldQuantity;
    private BatchStatus status;
    private long inventoryVersion;

    public ProductBatch() { }

    public ProductBatch(String id, String productId, double initialQuantity) {
        requirePositive(initialQuantity, "Initial quantity");
        this.id = id;
        this.productId = productId;
        this.initialQuantity = initialQuantity;
        this.availableQuantity = initialQuantity;
        this.status = BatchStatus.AVAILABLE;
        this.inventoryVersion = 0L;
    }

    public static ProductBatch restore(String id, String productId, String activeCampaignId,
            Date harvestDate, Date expiryDate, double initialQuantity, double availableQuantity,
            double reservedQuantity, double soldQuantity, BatchStatus status, long inventoryVersion) {
        ProductBatch batch = new ProductBatch(id, productId, initialQuantity);
        batch.activeCampaignId = activeCampaignId;
        batch.harvestDate = copy(harvestDate);
        batch.expiryDate = copy(expiryDate);
        batch.availableQuantity = availableQuantity;
        batch.reservedQuantity = reservedQuantity;
        batch.soldQuantity = soldQuantity;
        batch.status = status == null ? BatchStatus.AVAILABLE : status;
        batch.inventoryVersion = Math.max(0L, inventoryVersion);
        batch.verifyDates();
        batch.verifyInventoryInvariant();
        return batch;
    }

    public void updateDetails(Date harvestDate, Date expiryDate, double newInitialQuantity, Date now) {
        if (status == BatchStatus.ARCHIVED || status == BatchStatus.EXPIRED || isExpired(now)) {
            throw new IllegalStateException("Lô đã hết hạn hoặc lưu trữ không được sửa. Hãy nhập một lô mới.");
        }
        requirePositive(newInitialQuantity, "Initial quantity");
        if (harvestDate == null || expiryDate == null || now == null) {
            throw new IllegalArgumentException("Harvest, expiry and current dates are required");
        }
        if (harvestDate.after(expiryDate)) {
            throw new IllegalArgumentException("Harvest date must not be after expiry date");
        }
        boolean hasMovements = reservedQuantity > QUANTITY_TOLERANCE || soldQuantity > QUANTITY_TOLERANCE;
        if (hasMovements && Math.abs(initialQuantity - newInitialQuantity) > QUANTITY_TOLERANCE) {
            throw new IllegalStateException("Initial quantity cannot change after stock movements");
        }
        if (!hasMovements) {
            initialQuantity = newInitialQuantity;
            availableQuantity = newInitialQuantity;
        }
        this.harvestDate = copy(harvestDate);
        this.expiryDate = copy(expiryDate);
        if (isExpired(now)) status = BatchStatus.EXPIRED;
        else refreshStatus();
        verifyInventoryInvariant();
    }

    public boolean isExpired(Date now) {
        return expiryDate != null && now != null && !expiryDate.after(now);
    }
    public boolean isSellable(Date now) {
        return now != null && expiryDate != null && !isExpired(now) && availableQuantity > QUANTITY_TOLERANCE
                && (status == BatchStatus.AVAILABLE || status == BatchStatus.RESERVED_PARTIAL);
    }
    /** Effective stock for sale; physical expired stock remains availableQuantity for reconciliation. */
    public double getSellableQuantity(Date now) { return isSellable(now) ? availableQuantity : 0D; }
    public static boolean hasSellableBatch(String productId, java.util.List<ProductBatch> batches, Date now) {
        if (batches != null) for (ProductBatch batch : batches)
            if (batch != null && productId.equals(batch.getProductId()) && batch.isSellable(now)) return true;
        return false;
    }
    public static java.util.Comparator<ProductBatch> expiryOrder() {
        return java.util.Comparator.comparing(ProductBatch::getExpiryDate,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(ProductBatch::getId);
    }
    public boolean hasAvailableStock(double requestedQuantity) {
        return requestedQuantity > 0.0 && availableQuantity + QUANTITY_TOLERANCE >= requestedQuantity;
    }

    public void reserveStock(double requestedQuantity) {
        requirePositive(requestedQuantity, "Requested quantity");
        if (!hasAvailableStock(requestedQuantity)) { throw new IllegalStateException("Insufficient available stock"); }
        availableQuantity -= requestedQuantity;
        reservedQuantity += requestedQuantity;
        refreshStatus();
        verifyInventoryInvariant();
    }

    public void reserveStock(double requestedQuantity, Date now) {
        if (!isSellable(now)) { throw new IllegalStateException("Expired or unavailable batch cannot be sold"); }
        reserveStock(requestedQuantity);
    }

    public void commitReservedStock(double quantity) {
        requirePositive(quantity, "Committed quantity");
        if (reservedQuantity + QUANTITY_TOLERANCE < quantity) { throw new IllegalStateException("Insufficient reserved stock"); }
        reservedQuantity -= quantity;
        soldQuantity += quantity;
        refreshStatus();
        verifyInventoryInvariant();
    }

    public void releaseReservedStock(double quantity) {
        requirePositive(quantity, "Released quantity");
        if (reservedQuantity + QUANTITY_TOLERANCE < quantity) { throw new IllegalStateException("Insufficient reserved stock"); }
        reservedQuantity -= quantity;
        availableQuantity += quantity;
        refreshStatus();
        verifyInventoryInvariant();
    }

    public void verifyInventoryInvariant() {
        double accountedQuantity = availableQuantity + reservedQuantity + soldQuantity;
        if (Math.abs(initialQuantity - accountedQuantity) > QUANTITY_TOLERANCE) {
            throw new IllegalStateException("Inventory quantities do not match initial quantity");
        }
    }

    private void refreshStatus() {
        if (status == BatchStatus.EXPIRED || status == BatchStatus.ARCHIVED) { return; }
        if (availableQuantity <= QUANTITY_TOLERANCE && reservedQuantity <= QUANTITY_TOLERANCE) {
            status = BatchStatus.SOLD_OUT;
        } else if (reservedQuantity > QUANTITY_TOLERANCE) {
            status = BatchStatus.RESERVED_PARTIAL;
        } else {
            status = BatchStatus.AVAILABLE;
        }
    }

    private void verifyDates() {
        if (harvestDate != null && expiryDate != null && harvestDate.after(expiryDate)) {
            throw new IllegalStateException("Harvest date must not be after expiry date");
        }
    }

    private static Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }

    private static void requirePositive(double quantity, String fieldName) {
        if (!Double.isFinite(quantity) || quantity <= 0.0) { throw new IllegalArgumentException(fieldName + " must be positive"); }
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getActiveCampaignId() { return activeCampaignId; }
    public Date getHarvestDate() { return harvestDate; }
    public Date getExpiryDate() { return expiryDate; }
    public double getInitialQuantity() { return initialQuantity; }
    public double getAvailableQuantity() { return availableQuantity; }
    public double getReservedQuantity() { return reservedQuantity; }
    public double getSoldQuantity() { return soldQuantity; }
    public BatchStatus getStatus() { return status; }
    public long getInventoryVersion() { return inventoryVersion; }
}
