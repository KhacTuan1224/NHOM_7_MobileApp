package com.rescuefarm.ui.home;

import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;

public final class DiscoveryQuery {
    public enum Sort { RELEVANCE, URGENCY, DISCOUNT_DESC, PRICE_ASC, DISTANCE, NEAREST_DEADLINE, BEST_SELLING }

    private final String text;
    private final String categoryId;
    private final String province;
    private final RescueReason reason;
    private final UrgencyLevel urgency;
    private final RescueMode mode;
    private final Sort sort;
    private final double minPrice, maxPrice, radiusKm, minStock;
    private final int expiryWithinDays;

    public DiscoveryQuery(String text, String categoryId, String province, RescueReason reason,
            UrgencyLevel urgency, RescueMode mode, Sort sort) {
        this(text, categoryId, province, reason, urgency, mode, sort,
                0D, Double.MAX_VALUE, 0D, 0, 0D);
    }

    public DiscoveryQuery(String text, String categoryId, String province, RescueReason reason,
            UrgencyLevel urgency, RescueMode mode, Sort sort, double minPrice,
            double maxPrice, double radiusKm, int expiryWithinDays, double minStock) {
        this.text = clean(text); this.categoryId = clean(categoryId);
        this.province = clean(province); this.reason = reason; this.urgency = urgency;
        this.mode = mode; this.sort = sort == null ? Sort.RELEVANCE : sort;
        this.minPrice = finiteNonNegative(minPrice, 0D);
        this.maxPrice = finiteNonNegative(maxPrice, Double.MAX_VALUE);
        this.radiusKm = finiteNonNegative(radiusKm, 0D);
        this.expiryWithinDays = Math.max(0, expiryWithinDays);
        this.minStock = finiteNonNegative(minStock, 0D);
    }

    public static DiscoveryQuery empty() {
        return new DiscoveryQuery("", "", "", null, null, null, Sort.RELEVANCE);
    }

    public DiscoveryQuery withText(String value) {
        return new DiscoveryQuery(value, categoryId, province, reason, urgency, mode, sort,
                minPrice, maxPrice, radiusKm, expiryWithinDays, minStock);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0D ? value : fallback;
    }
    public String getText() { return text; }
    public String getCategoryId() { return categoryId; }
    public String getProvince() { return province; }
    public RescueReason getReason() { return reason; }
    public UrgencyLevel getUrgency() { return urgency; }
    public RescueMode getMode() { return mode; }
    public Sort getSort() { return sort; }
    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }
    public double getRadiusKm() { return radiusKm; }
    public int getExpiryWithinDays() { return expiryWithinDays; }
    public double getMinStock() { return minStock; }
    public boolean needsBatchData() {
        return expiryWithinDays > 0 || minStock > 0 || sort == Sort.BEST_SELLING
                || sort == Sort.NEAREST_DEADLINE || reason != null || urgency != null || mode != null;
    }
}
