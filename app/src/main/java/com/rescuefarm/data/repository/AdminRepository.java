package com.rescuefarm.data.repository;

import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import com.rescuefarm.data.repository.admin.AdminListItem;
import java.util.List;
import java.util.Map;
import java.util.Date;

public interface AdminRepository {
    enum Section {
        SELLER_APPLICATIONS("sellerApplications"), USERS("users"),
        PRODUCTS("products"), CAMPAIGNS("campaigns"), CATEGORIES("categories"),
        BANNERS("banners"), REVIEWS("reviews"), REPORTS("reports"), ORDERS("orders"),
        SHIPPING_SETTINGS(""),
        ANALYTICS("");

        private final String collection;
        Section(String collection) { this.collection = collection; }
        public String getCollection() { return collection; }
        public static Section from(String value) {
            if (value != null) for (Section section : values()) {
                if (section.name().equalsIgnoreCase(value)) return section;
            }
            return SELLER_APPLICATIONS;
        }
    }

    enum ErrorCode { UNAUTHENTICATED, FORBIDDEN, NETWORK, VALIDATION, NOT_FOUND, CONFLICT, UNKNOWN }
    enum ReportAction { MARK_IN_REVIEW, DISMISS, HIDE_TARGET, BLOCK_USER }

    interface DashboardCallback {
        void onSuccess(AdminDashboardSnapshot snapshot);
        void onError(ErrorCode code, String message);
    }

    interface ListCallback {
        void onSuccess(List<AdminListItem> items);
        void onError(ErrorCode code, String message);
    }

    interface ActionCallback {
        void onSuccess();
        void onError(ErrorCode code, String message);
    }
    interface DetailCallback {
        void onSuccess(Map<String, Object> detail);
        void onError(ErrorCode code, String message);
    }
    interface PageCallback {
        void onSuccess(List<AdminListItem> items, String nextCursor, boolean hasMore);
        void onError(ErrorCode code, String message);
    }

    void loadDashboard(DashboardCallback callback);
    void loadSection(Section section, int limit, ListCallback callback);
    default void loadSectionPage(Section section, int limit, String status, String cursor, PageCallback callback) {
        loadSection(section, limit, new ListCallback() {
            @Override public void onSuccess(List<AdminListItem> items) {
                String next = items == null || items.isEmpty() ? "" : items.get(items.size() - 1).getId();
                callback.onSuccess(items, next, items != null && items.size() >= limit);
            }
            @Override public void onError(ErrorCode code, String message) { callback.onError(code, message); }
        });
    }
    default void loadSectionPage(Section section, int limit, String status, String cursor,
            String searchTerm, PageCallback callback) {
        loadSectionPage(section, limit, status, cursor, callback);
    }
    default void loadSectionPage(Section section, int limit, String status, String cursor,
            String searchTerm, Date fromDate, Date throughDate, PageCallback callback) {
        loadSectionPage(section, limit, status, cursor, searchTerm, callback);
    }
    default androidx.lifecycle.LiveData<List<AdminListItem>> observeSection(Section section, int limit) {
        return new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList());
    }
    void transition(Section section, String resourceId, String targetStatus,
            String reason, ActionCallback callback);
    void setActive(Section section, String resourceId, boolean active,
            String reason, ActionCallback callback);
    void saveCatalogResource(Section section, String resourceId, Map<String, Object> values,
            ActionCallback callback);
    void loadShippingConfig(DetailCallback callback);
    void saveShippingConfig(double baseFeeVnd, double freeDistanceKm, double perKmFeeVnd,
            ActionCallback callback);
    void loadDetail(Section section, String resourceId, DetailCallback callback);
    void resolveReport(String reportId, ReportAction action, String reason, ActionCallback callback);
    default void setResourceImage(Section section, String resourceId, String imageUrl,
            ActionCallback callback) {
        callback.onError(ErrorCode.VALIDATION, "Không hỗ trợ cập nhật ảnh cho loại dữ liệu này.");
    }
}
