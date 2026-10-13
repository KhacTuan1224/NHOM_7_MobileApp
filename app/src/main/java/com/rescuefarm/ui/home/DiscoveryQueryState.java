package com.rescuefarm.ui.home;

import androidx.lifecycle.SavedStateHandle;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;

final class DiscoveryQueryState {
    private DiscoveryQueryState() { }

    static DiscoveryQuery restore(SavedStateHandle state) {
        DiscoveryQuery.Sort sort = value(DiscoveryQuery.Sort.class, state.get("discovery.sort"));
        return new DiscoveryQuery(state.get("discovery.text"), state.get("discovery.category"),
                state.get("discovery.province"), value(RescueReason.class, state.get("discovery.reason")),
                value(UrgencyLevel.class, state.get("discovery.urgency")),
                value(RescueMode.class, state.get("discovery.mode")),
                sort == null ? DiscoveryQuery.Sort.RELEVANCE : sort,
                decimal(state.get("discovery.minPrice"), 0D),
                decimal(state.get("discovery.maxPrice"), Double.MAX_VALUE),
                decimal(state.get("discovery.radiusKm"), 0D),
                integer(state.get("discovery.expiryDays"), 0),
                decimal(state.get("discovery.minStock"), 0D));
    }

    static void save(SavedStateHandle state, DiscoveryQuery query) {
        state.set("discovery.text", query.getText());
        state.set("discovery.category", query.getCategoryId());
        state.set("discovery.province", query.getProvince());
        state.set("discovery.reason", query.getReason() == null ? "" : query.getReason().name());
        state.set("discovery.urgency", query.getUrgency() == null ? "" : query.getUrgency().name());
        state.set("discovery.mode", query.getMode() == null ? "" : query.getMode().name());
        state.set("discovery.sort", query.getSort().name());
        state.set("discovery.minPrice", query.getMinPrice());
        state.set("discovery.maxPrice", query.getMaxPrice());
        state.set("discovery.radiusKm", query.getRadiusKm());
        state.set("discovery.expiryDays", query.getExpiryWithinDays());
        state.set("discovery.minStock", query.getMinStock());
    }

    private static <T extends Enum<T>> T value(Class<T> type, String name) {
        if (name == null || name.isEmpty()) return null;
        try { return Enum.valueOf(type, name); } catch (IllegalArgumentException ignored) { return null; }
    }
    private static double decimal(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }
    private static int integer(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
