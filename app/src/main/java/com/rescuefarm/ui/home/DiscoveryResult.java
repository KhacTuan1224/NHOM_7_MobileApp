package com.rescuefarm.ui.home;

import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;

public final class DiscoveryResult {
    public enum Type { PRODUCT, CAMPAIGN }
    private double minimumPrice = Double.MAX_VALUE, maximumDiscount = -1, soldQuantity = -1;
    private final Type type;
    private final Product product;
    private final RescueCampaign campaign;
    private final double distanceKilometers;
    private final String batchId;
    private final String campaignId;

    private DiscoveryResult(Type type, Product product, RescueCampaign campaign, double distance,
            String batchId, String campaignId) {
        this.type = type; this.product = product; this.campaign = campaign;
        this.distanceKilometers = distance; this.batchId = batchId == null ? "" : batchId;
        this.campaignId = campaignId == null ? "" : campaignId;
    }
    public static DiscoveryResult product(Product value) {
        return new DiscoveryResult(Type.PRODUCT, value, null, Double.NaN, "", "");
    }
    public static DiscoveryResult product(Product value, double distance) {
        return new DiscoveryResult(Type.PRODUCT, value, null, distance, "", "");
    }
    public static DiscoveryResult product(Product value, double distance, String batchId,
            RescueCampaign campaign) {
        return product(value, distance, batchId, campaign, campaign == null ? "" : campaign.getId());
    }
    public static DiscoveryResult product(Product value, double distance, String batchId,
            RescueCampaign campaign, String navigationCampaignId) {
        return new DiscoveryResult(Type.PRODUCT, value, campaign, distance, batchId,
                navigationCampaignId);
    }
    public static DiscoveryResult campaign(RescueCampaign value, double distance) {
        return new DiscoveryResult(Type.CAMPAIGN, null, value, distance, "", "");
    }
    public DiscoveryResult withCampaignMetrics(double price, double discount, double sold) {
        minimumPrice = price; maximumDiscount = discount; soldQuantity = sold; return this;
    }
    public double getMinimumPrice() { return minimumPrice; }
    public double getMaximumDiscount() { return maximumDiscount; }
    public double getSoldQuantity() { return soldQuantity; }
    public Type getType() { return type; }
    public Product getProduct() { return product; }
    public RescueCampaign getCampaign() { return campaign; }
    public double getDistanceKilometers() { return distanceKilometers; }
    public String getBatchId() { return batchId; }
    public String getCampaignId() { return campaignId; }
}
