package com.rescuefarm.data.local.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.rescuefarm.data.repository.GuestSessionRepository;

import java.util.UUID;

public class SharedPreferencesGuestSessionRepository implements GuestSessionRepository {
    private static final String PREFERENCES_NAME = "rescuefarm_guest_session";
    private static final String KEY_GUEST_ID = "guest_id";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String GUEST_ID_PREFIX = "guest_";

    private final SharedPreferences preferences;

    public SharedPreferencesGuestSessionRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }

    @Override
    public synchronized String getOrCreateGuestId() {
        String existingGuestId = preferences.getString(KEY_GUEST_ID, null);
        if (existingGuestId != null && !existingGuestId.trim().isEmpty()) {
            return existingGuestId;
        }

        String newGuestId = GUEST_ID_PREFIX + UUID.randomUUID();
        preferences.edit().putString(KEY_GUEST_ID, newGuestId).apply();
        return newGuestId;
    }

    public synchronized String getOrCreateGuestToken() {
        String token = preferences.getString("guest_checkout_token", "");
        if (token != null && token.matches("[a-f0-9]{64}")) return token;
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder value = new StringBuilder();
        for (byte part : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", part));
        token = value.toString();
        if (!preferences.edit().putString("guest_checkout_token", token).commit())
            throw new IllegalStateException("Không lưu được phiên Guest trên thiết bị.");
        return token;
    }

    public void saveOrderAccessToken(String orderId, String token) {
        if (orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,128}")
                || token == null || !token.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("Thông tin truy cập đơn Guest không hợp lệ.");
        preferences.edit().putString("order_access_" + orderId, token).apply();
    }

    public String getOrderAccessToken(String orderId) {
        return preferences.getString("order_access_" + orderId, "");
    }

    public void saveRecentGuestOrder(String orderId, String orderCode, String phone) {
        if (orderCode != null && !orderCode.trim().isEmpty()) {
            preferences.edit()
                    .putString("last_guest_order_id", orderId == null ? "" : orderId.trim())
                    .putString("last_guest_order_code", orderCode.trim())
                    .putString("last_guest_phone", phone == null ? "" : phone.trim())
                    .apply();
        }
    }

    public String getLastGuestOrderId() {
        return preferences.getString("last_guest_order_id", "");
    }

    public String getLastGuestOrderCode() {
        return preferences.getString("last_guest_order_code", "");
    }

    public String getLastGuestPhone() {
        return preferences.getString("last_guest_phone", "");
    }

    @Override
    public boolean hasCompletedOnboarding() {
        return preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    @Override
    public void markOnboardingCompleted() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
    }
}
