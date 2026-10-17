package com.rescuefarm.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "campaign_cache")
public class CampaignCacheEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String sellerId;
    public String title;
    public String description;
    public String imageUrlsSerialized;
    public String imagePublicIdsSerialized;
    public long viewCount;
    public String rescueReason;
    public String urgencyLevel;
    public String rescueMode;
    public String batchTargetsSerialized;
    public String reservedByBatchSerialized;
    public String rescuedByBatchSerialized;
    public String quantityUnit;
    public String unitStatus;
    @androidx.room.ColumnInfo(defaultValue = "0") public int allocationVersion;
    public double targetQuantity;
    public double reservedQuantity;
    public double rescuedQuantity;
    public long startAtEpochMillis;
    public long endAtEpochMillis;
    public double latitude;
    public double longitude;
    public double currentLatitude;
    public double currentLongitude;
    public long locationUpdatedAtEpochMillis;
    public boolean locationSharingEnabled;
    public String locationName;
    public String status;
    public long cachedAtEpochMillis;

    public CampaignCacheEntity(@NonNull String id) { this.id = id; }
}
