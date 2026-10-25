package com.rescuefarm.service.location;

/** Throttle cloud writes, not sensor sampling. Times must use a monotonic clock. */
public final class LocationPublishPolicy {
    public static final long MIN_INTERVAL_MS = 10_000L, HEARTBEAT_MS = 60_000L;
    private long publishedAt = -1;
    private double latitude, longitude;
    public boolean shouldPublish(double lat, double lng, long elapsed) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat)>90 || Math.abs(lng)>180) return false;
        if (publishedAt < 0) return true;
        long age = elapsed - publishedAt;
        return age >= HEARTBEAT_MS || age >= MIN_INTERVAL_MS
                && new CampaignLocationService().distanceKilometers(latitude, longitude, lat, lng) >= .025;
    }
    public void published(double lat, double lng, long elapsed) { latitude=lat; longitude=lng; publishedAt=elapsed; }
}
