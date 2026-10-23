package com.rescuefarm.domain;

import static org.junit.Assert.assertEquals;

import com.rescuefarm.domain.model.ProductBatch;

import org.junit.Test;

public class ProductBatchTest {
    private static final double DELTA = 0.000001;
    @Test public void expiryRetainsPhysicalStockButDisallowsSaleAndEditing() {
        java.util.Date expiry = new java.util.Date(2000);
        ProductBatch batch = ProductBatch.restore("b", "p", null, new java.util.Date(1), expiry,
            100, 70, 20, 10, com.rescuefarm.domain.enums.BatchStatus.RESERVED_PARTIAL, 1);
        assertEquals(70, batch.getSellableQuantity(new java.util.Date(1999)), DELTA);
        assertEquals(0, batch.getSellableQuantity(expiry), DELTA);
        assertEquals(70, batch.getAvailableQuantity(), DELTA);
        batch.releaseReservedStock(20);
        assertEquals(90, batch.getAvailableQuantity(), DELTA);
        assertEquals(0, batch.getSellableQuantity(expiry), DELTA);
        batch.verifyInventoryInvariant();
        org.junit.Assert.assertThrows(IllegalStateException.class,
            () -> batch.updateDetails(new java.util.Date(1), new java.util.Date(9000), 100, expiry));
    }

    @Test public void freshStockMustUseAnotherBatchAndExpiryOrderIsStable() {
        ProductBatch later = ProductBatch.restore("later", "p", null, new java.util.Date(1), new java.util.Date(4000),
            10, 10, 0, 0, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE, 1);
        ProductBatch earlier = ProductBatch.restore("earlier", "p", null, new java.util.Date(1), new java.util.Date(3000),
            10, 10, 0, 0, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE, 1);
        java.util.List<ProductBatch> batches = java.util.Arrays.asList(later, earlier);
        batches.sort(ProductBatch.expiryOrder());
        assertEquals("earlier", batches.get(0).getId());
        assertEquals(true, ProductBatch.hasSellableBatch("p", batches, new java.util.Date(3000)));
        assertEquals(false, ProductBatch.hasSellableBatch("p", batches, new java.util.Date(4000)));
    }

    @Test
    public void reserveCommitAndRelease_preserveInventoryInvariant() {
        ProductBatch batch = new ProductBatch("batch-1", "product-1", 100.0);

        batch.reserveStock(30.0);
        batch.commitReservedStock(20.0);
        batch.releaseReservedStock(10.0);

        assertEquals(80.0, batch.getAvailableQuantity(), DELTA);
        assertEquals(0.0, batch.getReservedQuantity(), DELTA);
        assertEquals(20.0, batch.getSoldQuantity(), DELTA);
        batch.verifyInventoryInvariant();
    }

    @Test(expected = IllegalStateException.class)
    public void reserveMoreThanAvailable_isRejected() {
        ProductBatch batch = new ProductBatch("batch-1", "product-1", 10.0);
        batch.reserveStock(11.0);
    }
}
