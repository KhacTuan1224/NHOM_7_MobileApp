package com.rescuefarm.service.inventory;

import com.rescuefarm.domain.model.ProductBatch;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Seller-facing operational warnings derived from authoritative batch quantities and expiry. */
public final class InventoryAlertEvaluator {
    public static final double LOW_STOCK_RATIO = 0.10D;
    public static final int NEAR_EXPIRY_DAYS = 3;
    public enum Filter { ALL, LOW_STOCK, NEAR_EXPIRY }
    public boolean isLowStock(ProductBatch batch) {
        return batch != null && batch.getInitialQuantity() > 0D
                && batch.getAvailableQuantity() / batch.getInitialQuantity() <= LOW_STOCK_RATIO;
    }
    public boolean isNearExpiry(ProductBatch batch, Date now) {
        if (batch == null || batch.getExpiryDate() == null || now == null
                || batch.isExpired(now)) return false;
        long remaining = batch.getExpiryDate().getTime() - now.getTime();
        long days = (remaining + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1);
        return days <= NEAR_EXPIRY_DAYS;
    }
    public long daysUntilExpiry(ProductBatch batch, Date now) {
        if (batch == null || batch.getExpiryDate() == null || now == null) return Long.MAX_VALUE;
        long remaining = batch.getExpiryDate().getTime() - now.getTime();
        return remaining <= 0 ? 0 : (remaining + TimeUnit.DAYS.toMillis(1) - 1)
                / TimeUnit.DAYS.toMillis(1);
    }
    public boolean matches(ProductBatch batch, Filter filter, Date now) {
        if (batch != null && batch.isExpired(now)) return false;
        if (filter == Filter.LOW_STOCK) return isLowStock(batch);
        if (filter == Filter.NEAR_EXPIRY) return isNearExpiry(batch, now);
        return true;
    }
}
