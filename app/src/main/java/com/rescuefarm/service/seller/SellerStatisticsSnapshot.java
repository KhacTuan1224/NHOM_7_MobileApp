package com.rescuefarm.service.seller;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Read-only response DTO, not an additional business/domain entity. */
public final class SellerStatisticsSnapshot {
    public final double revenue, rescueRate;
    public final int unverifiedCampaigns;
    public final int orders, deliveredOrders, pendingOrders, completedCampaigns, rescueRateCampaigns;
    public final boolean hasData, approximateDates;
    public final long calculatedAt;
    public final String timeBasis;
    public final List<Quantity> rescued = new ArrayList<>(), products = new ArrayList<>(), reasons = new ArrayList<>();
    public final List<Long> trendDates = new ArrayList<>();
    public final List<Double> trendRevenue = new ArrayList<>();
    public static final class Quantity {
        public final String label, unit;
        public final double value;
        Quantity(String label, String unit, double value) { this.label = label; this.unit = unit; this.value = value; }
    }
    public SellerStatisticsSnapshot(JSONObject json) {
        revenue = finite(json.optDouble("revenue", 0)); orders = json.optInt("orders");
        rescueRate = finite(json.optDouble("rescueRate", 0)); rescueRateCampaigns = json.optInt("rescueRateCampaigns");
        deliveredOrders = json.optInt("deliveredOrders"); pendingOrders = json.optInt("pendingOrders");
        completedCampaigns = json.optInt("completedCampaigns"); hasData = json.optBoolean("hasData");
        approximateDates = json.optBoolean("approximateDates"); calculatedAt = json.optLong("calculatedAt");
        timeBasis = json.optString("timeBasis"); unverifiedCampaigns = json.optInt("unverifiedCampaigns");
        JSONObject quantities = json.optJSONObject("rescuedByUnit");
        if (quantities != null) {
            java.util.Iterator<String> keys = quantities.keys();
            while (keys.hasNext()) { String unit = keys.next(); rescued.add(new Quantity("Đã giải cứu", unit, finite(quantities.optDouble(unit)))); }
        }
        parseQuantities(json.optJSONArray("topProducts"), products);
        parseQuantities(json.optJSONArray("topReasons"), reasons);
        JSONArray trend = json.optJSONArray("revenueTrend");
        if (trend != null) for (int i = 0; i < trend.length(); i++) {
            JSONObject point = trend.optJSONObject(i);
            if (point != null) { trendDates.add(point.optLong("at")); trendRevenue.add(finite(point.optDouble("revenue"))); }
        }
    }
    private static void parseQuantities(JSONArray values, List<Quantity> result) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null) result.add(new Quantity(value.optString("label"), value.optString("unit"), finite(value.optDouble("quantity"))));
        }
    }
    private static double finite(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0D; }
}
