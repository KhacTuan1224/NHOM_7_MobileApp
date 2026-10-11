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

public final class AdminViewModel extends ViewModel {
    private static final int PAGE_SIZE = 50;
    private final AdminRepository repository;
    private final MutableLiveData<AdminScreenState> state = new MutableLiveData<>(
            AdminScreenState.dashboard(AdminScreenState.Status.IDLE, null, ""));
    private Section currentSection = Section.ANALYTICS;

    public AdminViewModel(AdminRepository repository) { this.repository = repository; }
    public LiveData<AdminScreenState> getState() { return state; }
    public LiveData<List<AdminListItem>> observeSection(Section section) {
        return repository.observeSection(section, PAGE_SIZE);
    }

    public void refreshDashboard() {
        currentSection = Section.ANALYTICS;
        state.setValue(AdminScreenState.dashboard(AdminScreenState.Status.LOADING, null,
                "Đang tổng hợp dữ liệu quản trị…"));
        repository.loadDashboard(new AdminRepository.DashboardCallback() {
            @Override public void onSuccess(AdminDashboardSnapshot snapshot) {
                state.postValue(AdminScreenState.dashboard(AdminScreenState.Status.READY, snapshot,
                        "Dữ liệu dashboard đã cập nhật."));
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                state.postValue(AdminScreenState.dashboard(errorStatus(code), null, message));
            }
        });
    }

    public void refreshSection(Section section) {
        currentSection = section == null ? Section.SELLER_APPLICATIONS : section;
        if (currentSection == Section.ANALYTICS) { refreshDashboard(); return; }
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                Collections.emptyList(), "Đang tải dữ liệu quản trị…"));
        repository.loadSection(currentSection, PAGE_SIZE, new AdminRepository.ListCallback() {
            @Override public void onSuccess(List<AdminListItem> items) {
                boolean empty = items == null || items.isEmpty();
                state.postValue(AdminScreenState.list(empty ? AdminScreenState.Status.EMPTY
                        : AdminScreenState.Status.READY, currentSection, items,
                        empty ? "Không có dữ liệu." : "Hiển thị tối đa 50 mục."));
            }
            @Override public void onError(AdminRepository.ErrorCode code, String message) {
                state.postValue(AdminScreenState.list(errorStatus(code), currentSection,
                        Collections.emptyList(), message));
            }
        });
    }

    public void transition(String id, String target, String reason) {
        state.setValue(AdminScreenState.list(AdminScreenState.Status.LOADING, currentSection,
                currentItems(), "Đang cập nhật và ghi audit…"));
        repository.transition(currentSection, id, target, reason, actionCallback());
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
