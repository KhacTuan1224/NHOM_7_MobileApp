package com.rescuefarm.data.local.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.rescuefarm.data.repository.GuestSessionRepository;
import com.rescuefarm.domain.model.RecentGuestOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SharedPreferencesGuestSessionRepository implements GuestSessionRepository {
    private static final String PREFERENCES_NAME = "rescuefarm_guest_session";
    private static final String KEY_GUEST_ID = "guest_id";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String GUEST_ID_PREFIX = "guest_";
    public static final String KEY_RECENT_GUEST_ORDERS = "recent_guest_orders";
    public static final int MAX_RECENT_ORDERS = 5;

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

    public synchronized List<RecentGuestOrder> getRecentGuestOrders() {
        String json = preferences.getString(KEY_RECENT_GUEST_ORDERS, "");
        List<RecentGuestOrder> list = RecentGuestOrder.listFromJson(json);
        if (list.isEmpty()) {
            String legacyCode = preferences.getString("last_guest_order_code", "");
            String legacyPhone = preferences.getString("last_guest_phone", "");
            String legacyId = preferences.getString("last_guest_order_id", "");
            if (!legacyCode.trim().isEmpty()) {
                RecentGuestOrder legacy = new RecentGuestOrder(
                        legacyId, legacyCode, legacyPhone, 0.0, "PENDING", "COD", System.currentTimeMillis()
                );
                list.add(legacy);
                preferences.edit().putString(KEY_RECENT_GUEST_ORDERS, RecentGuestOrder.listToJson(list)).apply();
            }
        }
        return list;
    }

    public synchronized void saveRecentGuestOrder(String orderId, String orderCode, String phone) {
        saveRecentGuestOrder(orderId, orderCode, phone, 0.0, "PENDING", "COD");
    }

    public synchronized void saveRecentGuestOrder(String orderId, String orderCode, String phone,
                                                  double totalAmount, String status, String paymentMethod) {
        if (orderCode == null || orderCode.trim().isEmpty()) return;

        String safeCode = orderCode.trim();
        String safeId = orderId == null ? "" : orderId.trim();
        String safePhone = phone == null ? "" : phone.trim();
        String safeStatus = status == null || status.trim().isEmpty() ? "PENDING" : status.trim();
        String safeMethod = paymentMethod == null || paymentMethod.trim().isEmpty() ? "COD" : paymentMethod.trim();

        // Cập nhật legacy keys để duy trì tương thích ngược
        preferences.edit()
                .putString("last_guest_order_id", safeId)
                .putString("last_guest_order_code", safeCode)
                .putString("last_guest_phone", safePhone)
                .apply();

        List<RecentGuestOrder> existing = getRecentGuestOrders();
        List<RecentGuestOrder> updated = new ArrayList<>();

        RecentGuestOrder current = new RecentGuestOrder(
                safeId, safeCode, safePhone, totalAmount, safeStatus, safeMethod, System.currentTimeMillis()
        );
        updated.add(current);

        for (RecentGuestOrder item : existing) {
            boolean matchCode = !safeCode.isEmpty() && safeCode.equalsIgnoreCase(item.getOrderCode());
            boolean matchId = !safeId.isEmpty() && safeId.equals(item.getOrderId());
            if (matchCode || matchId) {
                if (totalAmount <= 0.0 && item.getTotalAmount() > 0.0) {
                    current.setTotalAmount(item.getTotalAmount());
                }
                if ("PENDING".equals(safeStatus) && !"PENDING".equals(item.getStatus())) {
                    current.setStatus(item.getStatus());
                }
                if (safeId.isEmpty() && !item.getOrderId().isEmpty()) {
                    current.setOrderId(item.getOrderId());
                }
                continue;
            }
            updated.add(item);
            if (updated.size() >= MAX_RECENT_ORDERS) break;
        }

        preferences.edit()
                .putString(KEY_RECENT_GUEST_ORDERS, RecentGuestOrder.listToJson(updated))
                .apply();
    }

    public synchronized void updateRecentGuestOrderStatus(String orderId, String orderCode, String status, double totalAmount) {
        if ((orderId == null || orderId.trim().isEmpty()) && (orderCode == null || orderCode.trim().isEmpty())) return;
        List<RecentGuestOrder> list = getRecentGuestOrders();
        boolean changed = false;
        for (RecentGuestOrder item : list) {
            boolean matchId = orderId != null && !orderId.trim().isEmpty() && orderId.trim().equals(item.getOrderId());
            boolean matchCode = orderCode != null && !orderCode.trim().isEmpty() && orderCode.trim().equalsIgnoreCase(item.getOrderCode());
            if (matchId || matchCode) {
                if (orderId != null && !orderId.trim().isEmpty() && item.getOrderId().isEmpty()) {
                    item.setOrderId(orderId.trim());
                    changed = true;
                }
                if (status != null && !status.trim().isEmpty() && !status.equals(item.getStatus())) {
                    item.setStatus(status.trim());
                    changed = true;
                }
                if (totalAmount > 0.0 && item.getTotalAmount() != totalAmount) {
                    item.setTotalAmount(totalAmount);
                    changed = true;
                }
            }
        }
        if (changed) {
            preferences.edit().putString(KEY_RECENT_GUEST_ORDERS, RecentGuestOrder.listToJson(list)).apply();
        }
    }

    public String getLastGuestOrderId() {
        String val = preferences.getString("last_guest_order_id", "");
        if (val.isEmpty()) {
            List<RecentGuestOrder> recents = getRecentGuestOrders();
            if (!recents.isEmpty()) return recents.get(0).getOrderId();
        }
        return val;
    }

    public String getLastGuestOrderCode() {
        String val = preferences.getString("last_guest_order_code", "");
        if (val.isEmpty()) {
            List<RecentGuestOrder> recents = getRecentGuestOrders();
            if (!recents.isEmpty()) return recents.get(0).getOrderCode();
        }
        return val;
    }

    public String getLastGuestPhone() {
        String val = preferences.getString("last_guest_phone", "");
        if (val.isEmpty()) {
            List<RecentGuestOrder> recents = getRecentGuestOrders();
            if (!recents.isEmpty()) return recents.get(0).getPhone();
        }
        return val;
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
