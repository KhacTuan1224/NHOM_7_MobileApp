package com.rescuefarm.ui.seller;
import androidx.lifecycle.*;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuefarm.data.repository.*;
import com.rescuefarm.service.seller.SellerStatisticsSnapshot;

/** Dashboard statistics always come from the complete server dataset, never list pagination. */
public class SellerViewModel extends ViewModel {
    private final String sellerId;
    private final SellerStatisticsRepository statistics = new SellerStatisticsRepository();
    private final MutableLiveData<SellerDashboardState> state = new MutableLiveData<>();
    private int days = 30, generation;
    public SellerViewModel(String id) { sellerId = id; }
    public LiveData<SellerDashboardState> getState() { return state; }
    public void setStatisticsPeriodDays(int days) {
        int next = days == 7 || days == 90 || days == 0 ? days : 30;
        if (this.days != next) {
            this.days = next;
            state.setValue(SellerDashboardState.of(SellerDashboardState.Status.LOADING, null, "Đang đổi kỳ thống kê…"));
            refresh();
        }
    }
    private boolean current(int request) {
        return request == generation && FirebaseAuth.getInstance().getCurrentUser() != null
                && sellerId.equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }
    public void refresh() {
        final int request = ++generation;
        if (!current(request)) {
            state.setValue(SellerDashboardState.of(SellerDashboardState.Status.ERROR, null, "Cần đăng nhập đúng tài khoản người bán.")); return;
        }
        state.setValue(SellerDashboardState.of(SellerDashboardState.Status.LOADING,
                state.getValue() == null ? null : state.getValue().getSnapshot(), "Đang tổng hợp toàn bộ dữ liệu…"));
        statistics.load(days, new SellerStatisticsRepository.Callback() {
            @Override public void onSuccess(SellerStatisticsSnapshot value) {
                if (!current(request)) return;
                if (value.dashboard == null) {
                    state.setValue(SellerDashboardState.of(SellerDashboardState.Status.ERROR, null,
                            "Máy chủ chưa hỗ trợ dashboard đầy đủ. Cần cập nhật backend trước khi tải lại.")); return;
                }
                String note = value.timeBasis + " Hàng chờ xử lý tính trên mọi kỳ.";
                if (value.approximateDates) note += " Một số đơn cũ chưa có ngày giao, dùng ngày cập nhật gần nhất.";
                if (value.unverifiedCampaigns > 0) note += " Có chiến dịch chưa xác minh đơn vị; không cộng lượng giải cứu của các chiến dịch này.";
                state.setValue(SellerDashboardState.of(value.dashboard.isEmpty() ? SellerDashboardState.Status.EMPTY
                        : SellerDashboardState.Status.READY, value.dashboard, note));
            }
            @Override public void onError(String message) {
                if (current(request)) state.setValue(SellerDashboardState.of(SellerDashboardState.Status.ERROR, null, message));
            }
        });
    }
    @Override protected void onCleared() { generation++; }
}
