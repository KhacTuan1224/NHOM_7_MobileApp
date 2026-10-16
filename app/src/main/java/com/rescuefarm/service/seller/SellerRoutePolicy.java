package com.rescuefarm.service.seller;

public final class SellerRoutePolicy {
    public enum Destination { PRODUCTS, CAMPAIGNS, ORDERS, STATISTICS, PROFILE }
    public Destination destinationFor(String key) {
        if ("products".equals(key) || "batches".equals(key) || "promotions".equals(key)) return Destination.PRODUCTS;
        if ("campaigns".equals(key) || "mobile".equals(key)) return Destination.CAMPAIGNS;
        if ("orders".equals(key) || "shipments".equals(key)) return Destination.ORDERS;
        if ("statistics".equals(key)) return Destination.STATISTICS;
        return Destination.PROFILE;
    }
}
