package com.rescuefarm.service;
import static org.junit.Assert.assertEquals;
import com.rescuefarm.service.pricing.PriceBreakdown;
import com.rescuefarm.service.pricing.PricingService;
import org.junit.Test;
public class PricingServiceTest {
    private final PricingService service = new PricingService();
    @Test public void quantityNeverChangesRescueUnitPrice() {
        for (double quantity : new double[]{1, 3, 5, 10, 100}) {
            PriceBreakdown result = service.calculatePrice(40000, 30000, quantity);
            assertEquals(30000, result.getFinalUnitPrice(), 0);
            assertEquals(30000 * quantity, result.getLineSubtotal(), 0);
        }
    }
    @Test public void fractionalQuantityUsesRoundedUnitPriceThenRoundedSubtotal() {
        assertEquals(40502, service.calculatePrice(40000, 27001, 1.5).getLineSubtotal(), 0);
        assertEquals(51, service.calculateFinalUnitPrice(50.5, 2), 0);
    }
    @Test(expected = IllegalArgumentException.class) public void nonFinitePriceIsRejected() { service.calculatePrice(40000, Double.NaN, 1); }
    @Test(expected = IllegalArgumentException.class) public void invalidQuantityIsRejected() { service.calculatePrice(40000, 30000, 0); }
    @Test(expected = IllegalArgumentException.class) public void rescuePriceCannotExceedOriginalPrice() { service.calculatePrice(100, 101, 1); }
}
