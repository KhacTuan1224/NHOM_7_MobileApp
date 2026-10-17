package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RescueCampaign {
    public static final long MOBILE_LOCATION_FRESHNESS_MINUTES = 10L;
    private static final double QUANTITY_TOLERANCE = 0.000001;

    private String id;
    private String sellerId;
    private String title;
    private String description;
    private List<String> imageUrls;
    private List<String> imagePublicIds;
    private long viewCount;
    private RescueReason rescueReason;
    private UrgencyLevel urgencyLevel;
    private RescueMode rescueMode;
    private Map<String, Double> batchTargets;
    private Map<String, Double> reservedByBatch = new LinkedHashMap<>();
    private Map<String, Double> rescuedByBatch = new LinkedHashMap<>();
    private int allocationVersion;
    private String quantityUnit = "", unitStatus = "UNVERIFIED";
    public void setQuantityUnit(String unit, String verification) {
        quantityUnit = com.rescuefarm.service.campaign.QuantityUnitPolicy.normalize(unit);
        if (quantityUnit.length() > 32) throw new IllegalArgumentException("Đơn vị quá dài");
        unitStatus = verification == null ? "UNVERIFIED" : verification;
    }
    public String getQuantityUnit() { return quantityUnit; }
    public String getUnitStatus() { return unitStatus; }
    public String getQuantityUnitLabel() { return quantityUnit.isEmpty() || "REVIEW_REQUIRED".equals(unitStatus) ? "đơn vị chưa xác minh" : quantityUnit; }
    public boolean isVerifiedKilograms() { return "VERIFIED".equals(unitStatus) && "kg".equals(quantityUnit); }
    private double targetQuantity;
    private double reservedQuantity;
    private double rescuedQuantity;
    private Date startDate;
    private Date endDate;
    private double latitude;
    private double longitude;
    private double currentLatitude;
    private double currentLongitude;
    private Date locationUpdatedAt;
    private boolean locationSharingEnabled;
    private String locationName;
    private CampaignStatus status;

    public RescueCampaign() {
        batchTargets = new HashMap<>();
        imageUrls = new ArrayList<>(); imagePublicIds = new ArrayList<>();
        status = CampaignStatus.DRAFT;
        urgencyLevel = UrgencyLevel.NORMAL;
    }

    public RescueCampaign(String id, String sellerId, double targetQuantity) {
        this();
        requirePositive(targetQuantity, "Target quantity");
        this.id = id;
        this.sellerId = sellerId;
        this.targetQuantity = targetQuantity;
    }

    public static RescueCampaign restore(String id, String sellerId, String title,
            String description, RescueReason rescueReason, UrgencyLevel urgencyLevel,
            RescueMode rescueMode, Map<String, Double> batchTargets, double targetQuantity,
            double reservedQuantity, double rescuedQuantity, Date startDate, Date endDate,
            double latitude, double longitude, double currentLatitude, double currentLongitude,
            Date locationUpdatedAt, boolean locationSharingEnabled, String locationName,
            CampaignStatus status) {
        RescueCampaign campaign = new RescueCampaign(id, sellerId, targetQuantity);
        campaign.defineCampaign(title, description, rescueReason, rescueMode, batchTargets);
        campaign.schedule(startDate, endDate);
        if (reservedQuantity < 0.0 || rescuedQuantity < 0.0
                || reservedQuantity + rescuedQuantity > targetQuantity + QUANTITY_TOLERANCE) {
            throw new IllegalStateException("Campaign progress quantities are invalid");
        }
        validateCoordinates(latitude, longitude);
        validateCoordinates(currentLatitude, currentLongitude);
        campaign.reservedQuantity = reservedQuantity;
        campaign.rescuedQuantity = rescuedQuantity;
        campaign.latitude = latitude;
        campaign.longitude = longitude;
        campaign.currentLatitude = currentLatitude;
        campaign.currentLongitude = currentLongitude;
        campaign.locationUpdatedAt = copy(locationUpdatedAt);
        if (locationSharingEnabled && rescueMode != RescueMode.MOBILE_POINT) {
            throw new IllegalStateException("Only mobile campaigns can share a location");
        }
        campaign.locationSharingEnabled = locationSharingEnabled;
        campaign.locationName = clean(locationName);
        campaign.urgencyLevel = urgencyLevel == null ? UrgencyLevel.NORMAL : urgencyLevel;
        campaign.status = status == null ? CampaignStatus.DRAFT : status;
        return campaign;
    }

    public void setStoryMedia(List<String> urls, List<String> publicIds) {
        imageUrls = sanitized(urls, 6); imagePublicIds = sanitized(publicIds, 6);
    }

    public void setViewCount(long value) { viewCount = Math.max(0L, value); }
    public void setAllocationProgress(Map<String, Double> reserved, Map<String, Double> rescued, int version) {
        Map<String, Double> nextReserved = new LinkedHashMap<>(), nextRescued = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : (reserved == null ? java.util.Collections.<String, Double>emptyMap() : reserved).entrySet()) {
            requireAllocation(entry.getKey(), entry.getValue()); nextReserved.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Double> entry : (rescued == null ? java.util.Collections.<String, Double>emptyMap() : rescued).entrySet()) {
            requireAllocation(entry.getKey(), entry.getValue()); nextRescued.put(entry.getKey(), entry.getValue());
        }
        if (version == 1) {
            double reservedTotal = 0D, rescuedTotal = 0D;
            for (String id : batchTargets.keySet()) {
                double held = nextReserved.containsKey(id) ? nextReserved.get(id) : 0D;
                double delivered = nextRescued.containsKey(id) ? nextRescued.get(id) : 0D;
                if (held + delivered > batchTargets.get(id) + QUANTITY_TOLERANCE)
                    throw new IllegalStateException("Batch allocation exceeds its target");
                reservedTotal += held; rescuedTotal += delivered;
            }
            if (Math.abs(reservedTotal - reservedQuantity) > QUANTITY_TOLERANCE
                    || Math.abs(rescuedTotal - rescuedQuantity) > QUANTITY_TOLERANCE)
                throw new IllegalStateException("Campaign allocation totals do not match");
        }
        reservedByBatch = nextReserved; rescuedByBatch = nextRescued; allocationVersion = version;
    }

    private void requireAllocation(String id, Double quantity) {
        if (!batchTargets.containsKey(id) || quantity == null || !Double.isFinite(quantity) || quantity < 0D)
            throw new IllegalArgumentException("Invalid batch allocation");
    }

    public Map<String, Double> getReservedByBatch() { return java.util.Collections.unmodifiableMap(reservedByBatch); }
    public Map<String, Double> getRescuedByBatch() { return java.util.Collections.unmodifiableMap(rescuedByBatch); }
    public int getAllocationVersion() { return allocationVersion; }

    public double calculateRemainingForBatch(String batchId) {
        Double target = batchTargets.get(batchId);
        if (target == null) return 0D;
        return Math.max(0D, Math.min(calculateRemainingQuantity(), target
                - (reservedByBatch.containsKey(batchId) ? reservedByBatch.get(batchId) : 0D)
                - (rescuedByBatch.containsKey(batchId) ? rescuedByBatch.get(batchId) : 0D)));
    }
    public void increaseViewCount() { viewCount++; }

    public void defineCampaign(
            String title,
            String description,
            RescueReason rescueReason,
            RescueMode rescueMode,
            Map<String, Double> batchTargets
    ) {
        if (rescueReason == null || rescueMode == null || batchTargets == null || batchTargets.isEmpty()) {
            throw new IllegalArgumentException("Campaign reason, mode and batch targets are required");
        }
        double totalBatchTarget = 0.0;
        for (double batchTarget : batchTargets.values()) {
            requirePositive(batchTarget, "Batch target");
            totalBatchTarget += batchTarget;
        }
        if (Math.abs(targetQuantity - totalBatchTarget) > QUANTITY_TOLERANCE) {
            throw new IllegalArgumentException("Batch targets must equal the campaign target quantity");
        }
        if (clean(title).length() < 3) {
            throw new IllegalArgumentException("Campaign title must have at least 3 characters");
        }
        this.title = clean(title);
        this.description = clean(description);
        this.rescueReason = rescueReason;
        this.rescueMode = rescueMode;
        this.batchTargets = new HashMap<>(batchTargets);
    }

    public double calculateProgress() {
        return targetQuantity <= 0.0 ? 0.0
                : Math.min(100.0, rescuedQuantity / targetQuantity * 100.0);
    }

    public double calculateRemainingQuantity() {
        return Math.max(0.0, targetQuantity - reservedQuantity - rescuedQuantity);
    }

    public UrgencyLevel calculateUrgency(Date now, Date earliestBatchExpiryDate) {
        long campaignDaysRemaining = daysBetween(now, endDate);
        long batchDaysRemaining = daysBetween(now, earliestBatchExpiryDate);
        double remainingRatio = targetQuantity <= 0.0 ? 0.0 : calculateRemainingQuantity() / targetQuantity;

        if (batchDaysRemaining <= 2L || (campaignDaysRemaining <= 1L && remainingRatio > 0.30)) {
            urgencyLevel = UrgencyLevel.CRITICAL;
        } else if (batchDaysRemaining <= 5L || campaignDaysRemaining <= 3L) {
            urgencyLevel = UrgencyLevel.HIGH;
        } else {
            urgencyLevel = UrgencyLevel.NORMAL;
        }
        return urgencyLevel;
    }

    public void schedule(Date startDate, Date endDate) {
        if (startDate == null || endDate == null || !endDate.after(startDate)) {
            throw new IllegalArgumentException("Campaign end date must be after its start date");
        }
        this.startDate = copy(startDate);
        this.endDate = copy(endDate);
    }

    public String getHighlightLabel() {
        if (status != CampaignStatus.ACTIVE) { return ""; }
        if (rescueMode == RescueMode.MOBILE_POINT) { return "ĐIỂM GIẢI CỨU DI ĐỘNG"; }
        if (urgencyLevel == UrgencyLevel.CRITICAL) { return "CẦN GIẢI CỨU GẤP"; }
        if (urgencyLevel == UrgencyLevel.HIGH) { return "CẦN GIẢI CỨU SỚM"; }
        return "ĐANG GIẢI CỨU";
    }

    public void reserveQuantity(double quantity) {
        requirePositive(quantity, "Reserved quantity");
        if (calculateRemainingQuantity() + QUANTITY_TOLERANCE < quantity) { throw new IllegalStateException("Campaign target does not have enough remaining quantity"); }
        reservedQuantity += quantity;
    }

    public void commitRescue(double quantity) {
        requirePositive(quantity, "Rescued quantity");
        if (reservedQuantity + QUANTITY_TOLERANCE < quantity) { throw new IllegalStateException("Campaign does not have enough reserved quantity"); }
        reservedQuantity -= quantity;
        rescuedQuantity += quantity;
        if (status == CampaignStatus.ACTIVE
                && rescuedQuantity + QUANTITY_TOLERANCE >= targetQuantity) {
            status = CampaignStatus.COMPLETED;
        }
    }

    public void releaseReservation(double quantity) {
        requirePositive(quantity, "Released quantity");
        if (reservedQuantity + QUANTITY_TOLERANCE < quantity) { throw new IllegalStateException("Campaign does not have enough reserved quantity"); }
        reservedQuantity -= quantity;
    }

    public void updateCurrentLocation(double latitude, double longitude, Date updatedAt) {
        if (rescueMode != RescueMode.MOBILE_POINT) { throw new IllegalStateException("Only a mobile rescue campaign can update its current location"); }
        if (updatedAt == null) { throw new IllegalArgumentException("Location update time is required"); }
        validateCoordinates(latitude, longitude);
        currentLatitude = latitude;
        currentLongitude = longitude;
        locationUpdatedAt = copy(updatedAt);
    }

    public void clearCurrentLocation() {
        if (rescueMode != RescueMode.MOBILE_POINT) {
            throw new IllegalStateException("Only a mobile rescue campaign has a current location");
        }
        currentLatitude = 0D;
        currentLongitude = 0D;
        locationUpdatedAt = null;
    }

    public void updateFixedLocation(double latitude, double longitude, String locationName) {
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = clean(locationName);
    }

    public boolean isLocationFresh(Date now) {
        if (rescueMode != RescueMode.MOBILE_POINT || !locationSharingEnabled || locationUpdatedAt == null || now.before(locationUpdatedAt)) { return false; }
        long ageMillis = now.getTime() - locationUpdatedAt.getTime();
        return ageMillis <= TimeUnit.MINUTES.toMillis(MOBILE_LOCATION_FRESHNESS_MINUTES);
    }

    public void setLocationSharingEnabled(boolean enabled) {
        if (enabled && rescueMode != RescueMode.MOBILE_POINT) {
            throw new IllegalStateException("Location sharing is only available for mobile rescue campaigns");
        }
        locationSharingEnabled = enabled;
    }

    public void submitForApproval() {
        if (status != CampaignStatus.DRAFT && status != CampaignStatus.REJECTED) {
            throw new IllegalStateException("Only draft or rejected campaigns can be submitted");
        }
        if (rescueReason == null || batchTargets == null || batchTargets.isEmpty()
                || startDate == null || endDate == null) {
            throw new IllegalStateException("Campaign requires batches, reason and schedule");
        }
        status = CampaignStatus.PENDING_APPROVAL;
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0 || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Invalid latitude or longitude");
        }
    }

    private static void requirePositive(double quantity, String fieldName) {
        if (!Double.isFinite(quantity) || quantity <= 0.0) { throw new IllegalArgumentException(fieldName + " must be positive"); }
    }

    private static List<String> sanitized(List<String> values, int maximum) {
        List<String> result = new ArrayList<>();
        if (values != null) for (String value : values) {
            String clean = clean(value);
            if (!clean.isEmpty() && !result.contains(clean)) result.add(clean);
            if (result.size() == maximum) break;
        }
        return result;
    }

    private static long daysBetween(Date start, Date end) {
        if (start == null || end == null) { return Long.MAX_VALUE; }
        return TimeUnit.MILLISECONDS.toDays(end.getTime() - start.getTime());
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getImageUrls() { return new ArrayList<>(imageUrls); }
    public List<String> getImagePublicIds() { return new ArrayList<>(imagePublicIds); }
    public long getViewCount() { return viewCount; }
    public RescueReason getRescueReason() { return rescueReason; }
    public UrgencyLevel getUrgencyLevel() { return urgencyLevel; }
    public RescueMode getRescueMode() { return rescueMode; }
    public Map<String, Double> getBatchTargets() { return new HashMap<>(batchTargets); }
    public double getTargetQuantity() { return targetQuantity; }
    public double getReservedQuantity() { return reservedQuantity; }
    public double getRescuedQuantity() { return rescuedQuantity; }
    public Date getStartDate() { return copy(startDate); }
    public Date getEndDate() { return copy(endDate); }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getCurrentLatitude() { return currentLatitude; }
    public double getCurrentLongitude() { return currentLongitude; }
    public Date getLocationUpdatedAt() { return copy(locationUpdatedAt); }
    public boolean isLocationSharingEnabled() { return locationSharingEnabled; }
    public String getLocationName() { return locationName; }
    public CampaignStatus getStatus() { return status; }
}
