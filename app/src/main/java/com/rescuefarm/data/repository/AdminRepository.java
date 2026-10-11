package com.rescuefarm.data.repository;

import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import com.rescuefarm.data.repository.admin.AdminListItem;
import java.util.List;

public interface AdminRepository {
    enum Section {
        SELLER_APPLICATIONS("sellerApplications"), USERS("users"), POSTS("posts"),
        PRODUCTS("products"), CAMPAIGNS("campaigns"), CATEGORIES("categories"),
        BANNERS("banners"), REVIEWS("reviews"), REPORTS("reports"), ORDERS("orders"),
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

    void loadDashboard(DashboardCallback callback);
    void loadSection(Section section, int limit, ListCallback callback);
    default androidx.lifecycle.LiveData<List<AdminListItem>> observeSection(Section section, int limit) {
        return new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList());
    }
    void transition(Section section, String resourceId, String targetStatus,
            String reason, ActionCallback callback);
    void setActive(Section section, String resourceId, boolean active,
            String reason, ActionCallback callback);
    default void setResourceImage(Section section, String resourceId, String imageUrl,
            ActionCallback callback) {
        callback.onError(ErrorCode.VALIDATION, "Không hỗ trợ cập nhật ảnh cho loại dữ liệu này.");
    }
}
