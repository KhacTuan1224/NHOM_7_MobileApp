package com.rescuefarm.ui.seller;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.rescuefarm.data.repository.SellerStatisticsRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class SellerStatisticsViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private static class Repository extends SellerStatisticsRepository {
        final List<Callback> callbacks = new ArrayList<>();
        @Override public void load(int days, Callback callback) { callbacks.add(callback); }
    }
    @Test public void stalePeriodResponseCannotOverwriteTheCurrentPeriod() {
        Repository repo = new Repository(); SellerStatisticsViewModel vm = new SellerStatisticsViewModel(repo);
        vm.load(7, false); vm.load(30, false);
        repo.callbacks.get(0).onError("old request");
        assertEquals(SellerStatisticsViewModel.Status.LOADING, vm.getState().getValue().status);
        assertEquals(30, vm.getDays());
        repo.callbacks.get(1).onError("current request");
        assertEquals("current request", vm.getState().getValue().message);
    }
    @Test public void refreshAndRetryWorkWithoutDuplicateLoadsForTheSamePeriod() {
        Repository repo = new Repository(); SellerStatisticsViewModel vm = new SellerStatisticsViewModel(repo);
        vm.load(7, false); vm.load(7, false); assertEquals(1, repo.callbacks.size());
        vm.load(7, true); assertEquals(2, repo.callbacks.size());
        repo.callbacks.get(1).onError("offline");
        vm.load(7, false); assertEquals(3, repo.callbacks.size());
        assertThrows(IllegalArgumentException.class, () -> vm.load(14, false));
    }
}
