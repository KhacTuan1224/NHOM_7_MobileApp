package com.rescuefarm.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.model.CartItem;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.service.cart.CartRevalidationService;
import com.rescuefarm.service.pricing.PricingService;
import java.util.ArrayList;
import java.util.Date;
import org.junit.Test;

public class CartRevalidationServiceTest {
    private final CartRevalidationService service = new CartRevalidationService(new PricingService());

    @Test public void currentStockAndPrice_areAccepted() {
        CartRevalidationService.Result result = service.revalidate(
                new CartItem("p::b", "p", "b", "s", 2D, 80D), product(), batch(5D, 2000),
                new Date(1000));
        assertTrue(result.isValid()); assertFalse(result.isPriceChanged());
        assertEquals(160D, result.getQuote().getLineSubtotal(), 0D);
    }

    @Test public void insufficientStock_isRejectedWithoutReservation() {
        ProductBatch batch = batch(1D, 2000);
        CartRevalidationService.Result result = service.revalidate(
                new CartItem("p::b", "p", "b", "s", 2D, 80D), product(), batch,
                new Date(1000));
        assertFalse(result.isValid());
        assertEquals(1D, batch.getAvailableQuantity(), 0D);
        assertEquals(0D, batch.getReservedQuantity(), 0D);
    }

    @Test public void expiredBatch_isRejected() {
        assertFalse(service.revalidate(new CartItem("p::b", "p", "b", "s", 1D, 80D),
                product(), batch(5D, 500), new Date(1000)).isValid());
    }

    private Product product() {
        return new Product("p", "s", "category", "Rau", "", 100D, 80D, "kg", "", "",
                new ArrayList<>(), ProductStatus.ACTIVE);
    }
    private ProductBatch batch(double available, long expiry) {
        return ProductBatch.restore("b", "p", null, new Date(0), new Date(expiry), available,
                available, 0D, 0D, BatchStatus.AVAILABLE, 1L);
    }
}
