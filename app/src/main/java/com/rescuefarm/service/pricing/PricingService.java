package com.rescuefarm.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PricingService {

    public double calculateFinalUnitPrice(double rescuePrice, double quantity) {
        return calculatePrice(rescuePrice, rescuePrice, quantity).getFinalUnitPrice();
    }

    public PriceBreakdown calculatePrice(double originalPrice, double rescuePrice, double quantity) {
        validatePriceAndQuantity(originalPrice, quantity);
        validatePriceAndQuantity(rescuePrice, quantity);
        if (rescuePrice > originalPrice) throw new IllegalArgumentException("Rescue price cannot exceed original price");
        double finalUnitPrice = roundVnd(rescuePrice);
        return new PriceBreakdown(roundVnd(originalPrice), roundVnd(rescuePrice),
                finalUnitPrice, quantity, roundVnd(finalUnitPrice * quantity));
    }

    public double calculateLineSubtotal(double finalUnitPrice, double quantity) {
        validatePriceAndQuantity(finalUnitPrice, quantity);
        return roundVnd(finalUnitPrice * quantity);
    }

    private static void validatePriceAndQuantity(double price, double quantity) {
        if (!Double.isFinite(price) || price < 0.0) { throw new IllegalArgumentException("Price cannot be negative"); }
        if (!Double.isFinite(quantity) || quantity <= 0.0) { throw new IllegalArgumentException("Quantity must be positive"); }
    }

    private static double roundVnd(double value) {
        return BigDecimal.valueOf(Math.max(0D, value)).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
