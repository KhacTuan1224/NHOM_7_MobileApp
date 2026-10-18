package com.rescuefarm.service.sync;

import java.util.concurrent.TimeUnit;

public final class CacheSyncPolicy {
    public static final String CATALOG = "catalog";
    public static final String CAMPAIGNS = "campaigns";
    public static final String BANNERS = "banners";
    public static final String ORDERS = "orders";
    public static final long HARD_EXPIRY_MILLIS = TimeUnit.DAYS.toMillis(7);

    public long ttlMillis(String scope) {
        if (CAMPAIGNS.equals(scope) || ORDERS.equals(scope)
                || (scope != null && scope.startsWith(ORDERS + ":"))) {
            return TimeUnit.MINUTES.toMillis(10);
        }
        if (CATALOG.equals(scope)) return TimeUnit.MINUTES.toMillis(30);
        if (BANNERS.equals(scope)) return TimeUnit.HOURS.toMillis(1);
        return TimeUnit.MINUTES.toMillis(15);
    }

    public boolean shouldRefresh(long lastSuccessAt, long expiresAt, long now, boolean force) {
        if (force || lastSuccessAt <= 0L) return true;
        return now >= expiresAt;
    }

    public boolean isStale(long expiresAt, long now) { return expiresAt <= 0L || now >= expiresAt; }
    public boolean isHardExpired(long cachedAt, long now) {
        return cachedAt <= 0L || now - cachedAt > HARD_EXPIRY_MILLIS;
    }
}
