package com.rescuefarm.service.seller;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Read-only response DTO, not an additional business/domain entity. */
public final class SellerStatisticsSnapshot {
    public final SellerDashboardSnapshot dashboard;
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
        dashboard = json.optJSONObject("inventory") == null ? null : dashboard(json);
    }
    private SellerDashboardSnapshot dashboard(org.json.JSONObject json) {
        org.json.JSONObject inventory = json.optJSONObject("inventory");
        if (inventory == null) inventory = new org.json.JSONObject();
        List<SellerDashboardSnapshot.RankedMetric> top = new ArrayList<>(), why = new ArrayList<>(), trend = new ArrayList<>();
        for (Quantity q : products) top.add(new SellerDashboardSnapshot.RankedMetric(q.label + " (" + q.unit + ")", q.value));
        for (Quantity q : reasons) why.add(new SellerDashboardSnapshot.RankedMetric(reasonLabel(q.label) + " (" + q.unit + ")", q.value));
        for (int i = 0; i < trendDates.size(); i++) trend.add(new SellerDashboardSnapshot.RankedMetric(
                new java.text.SimpleDateFormat("dd/MM", java.util.Locale.forLanguageTag("vi-VN")).format(new java.util.Date(trendDates.get(i))), trendRevenue.get(i)));
        double kg = 0; for (Quantity q : rescued) if (q.unit.equals("kg")) kg += q.value;
        List<com.rescuefarm.domain.model.Order> recent = new ArrayList<>();
        org.json.JSONArray rows = json.optJSONArray("recentOrders");
        if (rows != null) for (int i = 0; i < rows.length(); i++) {
            org.json.JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            recent.add(com.rescuefarm.domain.model.Order.restore(o.optString("id"),
                    com.rescuefarm.domain.enums.OrderOwnerType.valueOf(o.optString("ownerType", "CUSTOMER")),
                    o.optString("ownerId"), o.optString("orderCode", o.optString("id")), o.optString("sellerId"), o.optString("campaignId"),
                    com.rescuefarm.domain.enums.FulfillmentType.valueOf(o.optString("fulfillmentType", "PICKUP")),
                    o.optString("receiverName", "—"), o.optString("receiverPhone", "—"), o.optString("receiverAddress"), o.optDouble("receiverLatitude"), o.optDouble("receiverLongitude"),
                    o.optDouble("subtotal"), o.optDouble("quantityDiscount"), o.optDouble("shippingFee"), o.optDouble("totalAmount"),
                    com.rescuefarm.domain.enums.PaymentMethod.valueOf(o.optString("paymentMethod", "COD")),
                    com.rescuefarm.domain.enums.PaymentStatus.valueOf(o.optString("paymentStatus", "UNPAID")),
                    com.rescuefarm.domain.enums.OrderStatus.valueOf(o.optString("status", "PENDING")), o.optString("note"),
                    new java.util.Date(o.optLong("createdAt")), new java.util.Date(o.optLong("updatedAt")), java.util.Collections.emptyList()));
        }
        return new SellerDashboardSnapshot(inventory.optInt("products"), inventory.optInt("activeProducts"),
                inventory.optInt("campaigns"), inventory.optInt("activeCampaigns"), completedCampaigns,
                inventory.optInt("mobileCampaigns"), inventory.optInt("posts"), inventory.optInt("pendingPosts"), orders,
                pendingOrders, inventory.optInt("deliveryOrders"), inventory.optInt("promotions"), revenue, kg, rescueRate,
                top, why, recent, trend).withTotalOrders(json.optInt("totalOrders"));
    }
    private static String reasonLabel(String reason) {
        switch (reason) {
            case "OVER_SUPPLY": return "Dư cung"; case "NEAR_EXPIRY": return "Gần hết hạn";
            case "SEASONAL_HARVEST": return "Thu hoạch theo mùa"; case "WEATHER_IMPACT": return "Ảnh hưởng thời tiết";
            case "ORDER_CANCELLED": return "Đơn hàng bị hủy"; case "LOGISTICS_DISRUPTION": return "Gián đoạn vận chuyển";
            case "DEMAND_DROP": return "Nhu cầu giảm"; case "COSMETIC_GRADE": return "Nông sản loại B";
            default: return reason;
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
