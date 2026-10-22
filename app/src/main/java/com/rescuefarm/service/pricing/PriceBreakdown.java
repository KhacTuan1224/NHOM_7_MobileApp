package com.rescuefarm.service.pricing;

/** Immutable output shared by Product Detail and the future Cart. Amounts are VND. */
public final class PriceBreakdown {
    private final double originalPrice;
    private final double rescuePrice;
    private final double finalUnitPrice;
    private final double quantity;
    private final double lineSubtotal;

    public PriceBreakdown(double originalPrice, double rescuePrice, double finalUnitPrice, double quantity, double lineSubtotal) {
        this.originalPrice = originalPrice;
        this.rescuePrice = rescuePrice;
        this.finalUnitPrice = finalUnitPrice;
        this.quantity = quantity;
        this.lineSubtotal = lineSubtotal;
    }

    public double getOriginalPrice() { return originalPrice; }
    public double getRescuePrice() { return rescuePrice; }


    public double getFinalUnitPrice() { return finalUnitPrice; }
    public double getQuantity() { return quantity; }
    public double getLineSubtotal() { return lineSubtotal; }


}
