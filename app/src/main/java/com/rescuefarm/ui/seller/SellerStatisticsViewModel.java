package com.rescuefarm.ui.seller;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.SellerStatisticsRepository;
import com.rescuefarm.service.seller.SellerStatisticsSnapshot;

public final class SellerStatisticsViewModel extends ViewModel {
    public enum Status { LOADING, READY, EMPTY, ERROR }
    public static final class State {
        public final Status status;
        public final SellerStatisticsSnapshot snapshot;
        public final String message;
        State(Status status, SellerStatisticsSnapshot snapshot, String message) {
            this.status = status; this.snapshot = snapshot; this.message = message;
        }
    }
    private final SellerStatisticsRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private int days = 30, generation;
    private boolean loaded;
    public SellerStatisticsViewModel() { this(new SellerStatisticsRepository()); }
    public SellerStatisticsViewModel(SellerStatisticsRepository repository) { this.repository = repository; }
    public LiveData<State> getState() { return state; }
    public int getDays() { return days; }
    public void load(int period, boolean force) {
        if (!force && loaded && days == period) return;
        if (period != 0 && period != 7 && period != 30 && period != 90) throw new IllegalArgumentException("Unsupported statistics period");
        SellerStatisticsSnapshot previous = days == period && state.getValue() != null ? state.getValue().snapshot : null;
        days = period; loaded = true; int request = ++generation;
        state.setValue(new State(Status.LOADING, previous, "Đang tổng hợp dữ liệu cửa hàng…"));
        repository.load(period, new SellerStatisticsRepository.Callback() {
            @Override public void onSuccess(SellerStatisticsSnapshot snapshot) {
                if (request == generation) state.setValue(new State(snapshot.hasData ? Status.READY : Status.EMPTY, snapshot,
                        snapshot.hasData ? "" : "Chưa có dữ liệu trong kỳ đã chọn."));
            }
            @Override public void onError(String message) {
                if (request == generation) { loaded = false; state.setValue(new State(Status.ERROR, null, message)); }
            }
        });
    }
    @Override protected void onCleared() { generation++; super.onCleared(); }
}
