package com.rescuefarm.ui.home;

import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;

public final class DiscoveryResult {
    public enum Type { PRODUCT, CAMPAIGN }
    private final Type type;
    private final Product product;
    private final RescueCampaign campaign;
    private final double distanceKilometers;

    private DiscoveryResult(Type type, Product product, RescueCampaign campaign, double distance) {
        this.type = type; this.product = product; this.campaign = campaign;
        this.distanceKilometers = distance;
    }
    public static DiscoveryResult product(Product value) {
        return new DiscoveryResult(Type.PRODUCT, value, null, Double.NaN);
    }
    public static DiscoveryResult product(Product value, double distance) {
        return new DiscoveryResult(Type.PRODUCT, value, null, distance);
    }
    public static DiscoveryResult campaign(RescueCampaign value, double distance) {
        return new DiscoveryResult(Type.CAMPAIGN, null, value, distance);
    }
    public Type getType() { return type; }
    public Product getProduct() { return product; }
    public RescueCampaign getCampaign() { return campaign; }
    public double getDistanceKilometers() { return distanceKilometers; }
}
