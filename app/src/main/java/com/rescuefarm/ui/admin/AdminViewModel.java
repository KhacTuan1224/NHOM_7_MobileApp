package com.rescuefarm.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AdminRepository;
import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import com.rescuefarm.data.repository.admin.AdminListItem;
import java.util.Collections;
import java.util.List;
import java.util.Date;

public final class AdminViewModel extends ViewModel {
    private static final int PAGE_SIZE = 40;
    private final AdminRepository repository;
    private final MutableLiveData<AdminScreenState> state = new MutableLiveData<>(
            AdminScreenState.dashboard(AdminScreenState.Status.IDLE, null, ""));
    private Section currentSection = Section.ANALYTICS;
    private String statusFilter = "";
    private String searchTerm = "";
    private Date orderFromDate;
    private Date orderThroughDate;
    private String cursor = "";
    private boolean hasMore;
    private int pageRequestVersion, dashboardRequestVersion;

    public AdminViewModel(AdminRepository repository) { this.repository = repository; }
    public LiveData<AdminScreenState> getState() { return state; }
    public LiveData<List<AdminListItem>> observeSection(Section section) {
        return repository.observeSection(section, PAGE_SIZE);
    }

    public void loadDetail(Section section, String id, AdminRepository.DetailCallback callback) {
        repository.loadDetail(section, id, callback);
    }

    public void resolveReport(String id, AdminRepository.ReportAction action, String reason,
            AdminRepository.ActionCallback callback) {
        repository.resolveReport(id, action, reason, new AdminRepository.ActionCallback() {
            @Override public void onSuccess() {
                callback.onSuccess();
                refreshSection(Section.REPORTS);
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                callback.onError(code, message);
            }
        });
    }

    public void refreshDashboard() {
        final int request = ++dashboardRequestVersion;
        currentSection = Section.ANALYTICS;
        state.setValue(AdminScreenState.dashboard(AdminScreenState.Status.LOADING,
                state.getValue() == null ? null : state.getValue().getDashboard(), "Đang tổng hợp dữ liệu quản trị…"));
        repository.loadDashboard(new AdminRepository.DashboardCallback() {
            @Override public void onSuccess(AdminDashboardSnapshot snapshot) {
                if (request != dashboardRequestVersion) return;
                state.postValue(AdminScreenState.dashboard(AdminScreenState.Status.READY, snapshot,
                        "Dữ liệu dashboard đã cập nhật."));
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                if (request != dashboardRequestVersion) return;
                state.postValue(AdminScreenState.dashboard(errorStatus(code), null, message));
            }
        });
    }

    public void refreshSection(Section section) {
        currentSection = section == null ? Section.SELLER_APPLICATIONS : section;
        if (currentSection == Section.ANALYTICS) { refreshDashboard(); return; }
        cursor = ""; hasMore = false;
        loadSectionPage(false);
    }

    public void setStatusFilter(String value) {
        String next = value == null || "Tất cả trạng thái".equals(value) ? "" : value.trim();
        if (next.equals(statusFilter)) return;
        statusFilter = next;
        refreshSection(currentSection);
    }

    public void setSearchTerm(String value) {
        String next = value == null ? "" : value.trim();
        if (next.equals(searchTerm)) return;
        searchTerm = next;
        refreshSection(currentSection);
    }

    public Date getOrderFromDate() { return orderFromDate == null ? null : new Date(orderFromDate.getTime()); }
    public Date getOrderThroughDate() { return orderThroughDate == null ? null : new Date(orderThroughDate.getTime()); }
    public void setOrderDateRange(Date from, Date through) {
        orderFromDate = from == null ? null : new Date(from.getTime());
        orderThroughDate = through == null ? null : new Date(through.getTime());
        refreshSection(currentSection);
    }

    public boolean hasMore() { return hasMore; }

    public void loadNextPage() {
        if (!hasMore || currentSection == Section.ANALYTICS) return;
        loadSectionPage(true);
    }

    private void loadSectionPage(boolean append) {
        Section requestedSection = currentSection;
        String requestedFilter = statusFilter;
        String requestedCursor = append ? cursor : "";
        int version = ++pageRequestVersion;
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                append ? currentItems() : Collections.emptyList(), "Đang tải dữ liệu quản trị…"));
        repository.loadSectionPage(requestedSection, PAGE_SIZE, requestedFilter, requestedCursor, searchTerm,
                requestedSection == Section.ORDERS ? orderFromDate : null,
                requestedSection == Section.ORDERS ? orderThroughDate : null,
                new AdminRepository.PageCallback() {
            @Override public void onSuccess(List<AdminListItem> items, String nextCursor, boolean more) {
                if (version != pageRequestVersion || requestedSection != currentSection) return;
                List<AdminListItem> merged = new java.util.ArrayList<>();
                if (append) merged.addAll(currentItems());
                if (items != null) merged.addAll(items);
                cursor = nextCursor; hasMore = more;
                boolean empty = merged.isEmpty();
                state.postValue(AdminScreenState.list(empty ? AdminScreenState.Status.EMPTY
                        : AdminScreenState.Status.READY, requestedSection, merged,
                        empty ? "Không có dữ liệu phù hợp." : "Đã tải " + merged.size() + " mục."));
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                if (version != pageRequestVersion) return;
                state.postValue(AdminScreenState.list(errorStatus(code), requestedSection,
                        append ? currentItems() : Collections.emptyList(), message));
            }
        });
    }

    public void transition(String id, String target, String reason) {
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                currentItems(), "Đang cập nhật và ghi audit…"));
        repository.transition(currentSection, id, target, reason, actionCallback());
    }

    public void transition(String id, String target, String reason, AdminRepository.ActionCallback callback) {
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                currentItems(), "Đang cập nhật và ghi audit…"));
        repository.transition(currentSection, id, target, reason, new AdminRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (callback != null) callback.onSuccess();
                refreshSection(currentSection);
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void transition(Section section, String id, String target, String reason,
            AdminRepository.ActionCallback callback) {
        repository.transition(section, id, target, reason, new AdminRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (callback != null) callback.onSuccess();
                refreshSection(section);
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void setActive(String id, boolean active, String reason) {
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                currentItems(), "Đang cập nhật và ghi audit…"));
        repository.setActive(currentSection, id, active, reason, actionCallback());
    }

    public void setResourceImage(String id, String imageUrl) {
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                currentItems(), "Đang lưu ảnh Cloudinary…"));
        repository.setResourceImage(currentSection, id, imageUrl, actionCallback());
    }

    public void saveCatalogResource(String id, java.util.Map<String, Object> values,
            AdminRepository.ActionCallback callback) {
        repository.saveCatalogResource(currentSection, id, values, new AdminRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (callback != null) callback.onSuccess();
                refreshSection(currentSection);
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void loadShippingConfig(AdminRepository.DetailCallback callback) {
        repository.loadShippingConfig(callback);
    }

    public void saveShippingConfig(double baseFee, double freeDistanceKm, double perKmFee,
            AdminRepository.ActionCallback callback) {
        repository.saveShippingConfig(baseFee, freeDistanceKm, perKmFee, callback);
    }

    private AdminRepository.ActionCallback actionCallback() {
        return new AdminRepository.ActionCallback() {
            @Override public void onSuccess() { refreshSection(currentSection); }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                state.postValue(AdminScreenState.list(errorStatus(code), currentSection,
                        currentItems(), message));
            }
        };
    }

    private List<AdminListItem> currentItems() {
        AdminScreenState value = state.getValue();
        return value == null ? Collections.emptyList() : value.getItems();
    }
    private static AdminScreenState.Status errorStatus(AdminRepository.ErrorCode code) {
        return code == AdminRepository.ErrorCode.FORBIDDEN
                || code == AdminRepository.ErrorCode.UNAUTHENTICATED
                ? AdminScreenState.Status.FORBIDDEN : AdminScreenState.Status.ERROR;
    }
}
