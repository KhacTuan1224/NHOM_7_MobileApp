package com.rescuefarm.data.local.mapper;

import com.rescuefarm.data.local.entity.CampaignCacheEntity;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.RescueCampaign;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CampaignCacheMapper {
    private CampaignCacheMapper() { }

    public static CampaignCacheEntity toEntity(RescueCampaign value, long cachedAt) {
        CampaignCacheEntity entity = new CampaignCacheEntity(value.getId());
        entity.sellerId = value.getSellerId(); entity.title = value.getTitle();
        entity.description = value.getDescription();
        entity.imageUrlsSerialized = serializeStrings(value.getImageUrls());
        entity.imagePublicIdsSerialized = serializeStrings(value.getImagePublicIds());
        entity.viewCount = value.getViewCount();
        entity.rescueReason = value.getRescueReason().name();
        entity.urgencyLevel = value.getUrgencyLevel().name();
        entity.rescueMode = value.getRescueMode().name();
        entity.batchTargetsSerialized = serializeTargets(value.getBatchTargets());
        entity.reservedByBatchSerialized = serializeTargets(value.getReservedByBatch());
        entity.rescuedByBatchSerialized = serializeTargets(value.getRescuedByBatch());
        entity.allocationVersion = value.getAllocationVersion();
        entity.quantityUnit = value.getQuantityUnit(); entity.unitStatus = value.getUnitStatus();
        entity.targetQuantity = value.getTargetQuantity();
        entity.reservedQuantity = value.getReservedQuantity();
        entity.rescuedQuantity = value.getRescuedQuantity();
        entity.startAtEpochMillis = epoch(value.getStartDate());
        entity.endAtEpochMillis = epoch(value.getEndDate());
        entity.latitude = value.getLatitude(); entity.longitude = value.getLongitude();
        entity.currentLatitude = value.getCurrentLatitude();
        entity.currentLongitude = value.getCurrentLongitude();
        entity.locationUpdatedAtEpochMillis = epoch(value.getLocationUpdatedAt());
        entity.locationSharingEnabled = value.isLocationSharingEnabled();
        entity.locationName = value.getLocationName(); entity.status = value.getStatus().name();
        entity.cachedAtEpochMillis = cachedAt; return entity;
    }

    public static RescueCampaign toDomain(CampaignCacheEntity value) {
        RescueCampaign campaign = RescueCampaign.restore(value.id, value.sellerId, value.title, value.description,
                enumValue(RescueReason.class, value.rescueReason, RescueReason.OVER_SUPPLY),
                enumValue(UrgencyLevel.class, value.urgencyLevel, UrgencyLevel.NORMAL),
                enumValue(RescueMode.class, value.rescueMode, RescueMode.FIXED_POINT),
                parseTargets(value.batchTargetsSerialized), value.targetQuantity,
                value.reservedQuantity, value.rescuedQuantity,
                date(value.startAtEpochMillis), date(value.endAtEpochMillis),
                value.latitude, value.longitude, value.currentLatitude, value.currentLongitude,
                date(value.locationUpdatedAtEpochMillis), value.locationSharingEnabled,
                value.locationName,
                enumValue(CampaignStatus.class, value.status, CampaignStatus.DRAFT));
        campaign.setStoryMedia(parseStrings(value.imageUrlsSerialized), parseStrings(value.imagePublicIdsSerialized));
        campaign.setViewCount(value.viewCount);
        campaign.setQuantityUnit(value.quantityUnit, value.unitStatus);
        campaign.setAllocationProgress(parseTargets(value.reservedByBatchSerialized),
                parseTargets(value.rescuedByBatchSerialized), value.allocationVersion);
        return campaign;
    }

    public static List<RescueCampaign> campaigns(List<CampaignCacheEntity> values) {
        List<RescueCampaign> result = new ArrayList<>();
        if (values != null) for (CampaignCacheEntity value : values) {
            try { result.add(toDomain(value)); }
            catch (IllegalArgumentException | IllegalStateException ignored) { }
        }
        return result;
    }

    static String serializeTargets(Map<String, Double> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Double> entry : new TreeMap<>(values).entrySet()) {
            if (result.length() > 0) result.append(';');
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    static Map<String, Double> parseTargets(String value) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (value == null || value.trim().isEmpty()) return result;
        for (String item : value.split(";")) {
            int separator = item.lastIndexOf('=');
            if (separator <= 0) continue;
            try { result.put(item.substring(0, separator),
                    Double.parseDouble(item.substring(separator + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static String serializeStrings(List<String> values) {
        StringBuilder result = new StringBuilder();
        if (values != null) for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(value.replace("\n", ""));
        }
        return result.toString();
    }
    private static List<String> parseStrings(String value) {
        List<String> result = new ArrayList<>();
        if (value != null) for (String item : value.split("\\n"))
            if (!item.trim().isEmpty()) result.add(item.trim());
        return result;
    }

    private static long epoch(Date value) { return value == null ? 0L : value.getTime(); }
    private static Date date(long value) { return value <= 0L ? null : new Date(value); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value == null ? "" : value); }
        catch (IllegalArgumentException error) { return fallback; }
    }
}
