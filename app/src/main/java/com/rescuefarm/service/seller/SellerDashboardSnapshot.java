package com.rescuefarm.service.seller;

import com.rescuefarm.domain.model.Order;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SellerDashboardSnapshot {
    public static final class RankedMetric {
        private final String label;
        private final double quantity;
        public RankedMetric(String label, double quantity) { this.label = label; this.quantity = quantity; }
        public String getLabel() { return label; }
        public double getQuantity() { return quantity; }
    }
    private final int products, activeProducts, campaigns, activeCampaigns, completedCampaigns;
    private final int mobileCampaigns, posts, pendingPosts, orders, pendingOrders, deliveryOrders, promotions;
    private final double revenue, rescuedQuantity, rescueRate;
    private final List<RankedMetric> topProducts, topReasons, revenueTrend;
    private final List<Order> recentOrders;
    private int totalOrders;

    public SellerDashboardSnapshot(int products, int activeProducts, int campaigns, int activeCampaigns,
            int completedCampaigns, int mobileCampaigns, int posts, int pendingPosts, int orders,
            int pendingOrders, int deliveryOrders, int promotions, double revenue,
            double rescuedQuantity, double rescueRate, List<RankedMetric> topProducts,
            List<RankedMetric> topReasons, List<Order> recentOrders,
            List<RankedMetric> revenueTrend) {
        this.products = products; this.activeProducts = activeProducts; this.campaigns = campaigns;
        this.activeCampaigns = activeCampaigns; this.completedCampaigns = completedCampaigns;
        this.mobileCampaigns = mobileCampaigns; this.posts = posts; this.pendingPosts = pendingPosts;
        this.totalOrders = orders; this.orders = orders; this.pendingOrders = pendingOrders; this.deliveryOrders = deliveryOrders;
        this.promotions = promotions; this.revenue = revenue; this.rescuedQuantity = rescuedQuantity;
        this.rescueRate = rescueRate; this.topProducts = immutable(topProducts);
        this.topReasons = immutable(topReasons); this.revenueTrend = immutable(revenueTrend);
        this.recentOrders = Collections.unmodifiableList(
                new ArrayList<>(recentOrders));
    }
    private static List<RankedMetric> immutable(List<RankedMetric> value) {
        return Collections.unmodifiableList(new ArrayList<>(value == null
                ? Collections.emptyList() : value));
    }
    public int getProducts() { return products; }
    public int getActiveProducts() { return activeProducts; }
    public int getCampaigns() { return campaigns; }
    public int getActiveCampaigns() { return activeCampaigns; }
    public int getCompletedCampaigns() { return completedCampaigns; }
    public int getMobileCampaigns() { return mobileCampaigns; }
    public int getPosts() { return posts; }
    public int getPendingPosts() { return pendingPosts; }
    public int getOrders() { return orders; }
    public int getPendingOrders() { return pendingOrders; }
    public int getDeliveryOrders() { return deliveryOrders; }
    public int getPromotions() { return promotions; }
    public double getRevenue() { return revenue; }
    public double getRescuedQuantity() { return rescuedQuantity; }
    public double getRescueRate() { return rescueRate; }
    public List<RankedMetric> getTopProducts() { return topProducts; }
    public List<RankedMetric> getTopReasons() { return topReasons; }
    public List<RankedMetric> getRevenueTrend() { return revenueTrend; }
    public List<Order> getRecentOrders() { return recentOrders; }
    public boolean isEmpty() { return products + campaigns + posts + orders + pendingOrders == 0; }
    public SellerDashboardSnapshot withTotalOrders(int total) { totalOrders = total; return this; }
    public boolean hasMoreOrders() { return totalOrders > recentOrders.size(); }
}
