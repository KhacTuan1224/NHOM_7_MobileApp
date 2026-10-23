package com.rescuefarm.service.seller;

public final class SellerRoutePolicy {
    public enum Destination { PRODUCTS, BATCHES, CAMPAIGNS, MOBILE, ORDERS, STATISTICS, PROFILE }
    public Destination destinationFor(String key) {
        if ("products".equals(key)) return Destination.PRODUCTS;
        if ("batches".equals(key)) return Destination.BATCHES;
        if ("campaigns".equals(key)) return Destination.CAMPAIGNS;
        if ("mobile".equals(key)) return Destination.MOBILE;
        if ("orders".equals(key) || "shipments".equals(key)) return Destination.ORDERS;
        if ("statistics".equals(key)) return Destination.STATISTICS;
        return Destination.PROFILE;
    }
}
