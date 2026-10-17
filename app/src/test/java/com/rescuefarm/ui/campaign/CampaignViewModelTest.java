package com.rescuefarm.ui.campaign;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.service.location.LocationProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class CampaignViewModelTest {
    @Rule public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    @Test public void invalidTargets_doNotReachRepository() {
        FakeCampaignRepository repository = new FakeCampaignRepository();
        CampaignViewModel viewModel = new CampaignViewModel(repository, new FakeAuth(), new FakeProducts(), new FakeLocation());
        viewModel.save("", "Campaign", "", RescueReason.OVER_SUPPLY, RescueMode.FIXED_POINT,
                java.util.Collections.singletonMap("batch", -1D), "2027-01-01", "2027-01-03", "10", "106", "A", true);
        assertFalse(repository.saveCalled);
        assertEquals(CampaignScreenState.Status.ERROR, viewModel.getState().getValue().getStatus());
    }
    @Test public void validSubmit_reachesRepositoryAsPending() {
        FakeCampaignRepository repository = new FakeCampaignRepository();
        CampaignViewModel viewModel = new CampaignViewModel(repository, new FakeAuth(), new FakeProducts(), new FakeLocation());
        androidx.lifecycle.Observer<List<CampaignBatchOption>> observer = ignored -> { };
        viewModel.getBatchOptions().observeForever(observer);
        viewModel.save("", "Campaign", "Nội dung câu chuyện giải cứu chi tiết", RescueReason.OVER_SUPPLY, RescueMode.MOBILE_POINT,
                java.util.Collections.singletonMap("batch", 10D), "2027-01-01", "2027-01-03", "10", "106", "A", true);
        assertTrue(repository.saveCalled); assertTrue(repository.submitCalled);
        assertEquals(CampaignScreenState.Status.SAVED, viewModel.getState().getValue().getStatus());
        viewModel.getBatchOptions().removeObserver(observer);
    }
    private static final class FakeCampaignRepository implements CampaignRepository {
        private boolean saveCalled, submitCalled;
        private final MutableLiveData<List<RescueCampaign>> values = new MutableLiveData<>(new ArrayList<>());
        @Override public LiveData<List<RescueCampaign>> observeActiveCampaigns() { return values; }
        @Override public LiveData<List<RescueCampaign>> observeSellerCampaigns(String id) { return values; }
        @Override public void refreshActiveCampaigns(ActionCallback callback) { callback.onSuccess(); }
        @Override public void refreshSellerCampaigns(String id, ActionCallback callback) { callback.onSuccess(); }
        @Override public void getCampaign(String id, CampaignCallback callback) { }
        @Override public void saveCampaign(RescueCampaign campaign, boolean submit, CampaignCallback callback) {
            saveCalled = true; submitCalled = submit; callback.onSuccess(campaign);
        }
        @Override public void updateMobileLocation(String id, double lat, double lng, boolean sharing, CampaignCallback callback) { }
        @Override public void mutateReservation(String campaignId, String batchId, long version,
                ReservationMutation mutation, double quantity, CampaignCallback callback) { }
    }
    private static final class FakeAuth implements AuthRepository {
        @Override public boolean isAvailable() { return true; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public String getCurrentUserId() { return "seller"; }
        @Override public void restoreSession(AuthCallback callback) { }
        @Override public void signIn(String email, String password, AuthCallback callback) { }
        @Override public void register(String email, String password, String name, String phone, UserRole role, AuthCallback callback) { }
        @Override public void signInWithGoogleIdToken(String token, AuthCallback callback) { }
        @Override public void sendPasswordResetEmail(String email, ActionCallback callback) { }
        @Override public void signOut() { }
    }
    private static final class FakeLocation implements LocationProvider {
        @Override public void getCurrentLocation(LocationCallback callback) { callback.onLocationAvailable(10, 106); }
        @Override public void reverseGeocode(double lat, double lng, GeocodeCallback callback) { }
    }
    private static final class FakeProducts implements com.rescuefarm.data.repository.ProductRepository {
        public LiveData<List<com.rescuefarm.domain.model.Category>> observeCategories() { return new MutableLiveData<>(new ArrayList<>()); }
        public LiveData<List<com.rescuefarm.domain.model.Product>> observeProducts() { return new MutableLiveData<>(new ArrayList<>()); }
        public LiveData<List<com.rescuefarm.domain.model.Product>> observeSellerProducts(String id) {
            return new MutableLiveData<>(java.util.Collections.singletonList(com.rescuefarm.domain.model.Product.restore("p", "seller", "cat", "Product", "", 10, 5, "kg", "", "", new ArrayList<>(), 0, 0, com.rescuefarm.domain.enums.ProductStatus.ACTIVE)));
        }
        public LiveData<List<com.rescuefarm.domain.model.ProductBatch>> observeBatches(String id) {
            return new MutableLiveData<>(java.util.Collections.singletonList(com.rescuefarm.domain.model.ProductBatch.restore("batch", "p", "", new java.util.Date(1), new java.util.Date(System.currentTimeMillis()+86400000), 100, 100, 0, 0, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE, 1)));
        }
        public void refreshCatalog(ActionCallback callback) { callback.onSuccess(); }
        public void refreshSellerProducts(String id, ActionCallback callback) { callback.onSuccess(); }
        public void refreshBatches(String id, ActionCallback callback) { callback.onSuccess(); }
        public void getProduct(String id, ProductCallback callback) { }
        public void saveProduct(com.rescuefarm.domain.model.Product product, ProductCallback callback) { }
        public void hideProduct(String id, ActionCallback callback) { }
        public void saveBatch(com.rescuefarm.domain.model.ProductBatch batch, long version, BatchCallback callback) { }
        public void deleteBatch(String id, long version, ActionCallback callback) { }
        public void mutateStock(String id, long version, StockMutation mutation, double quantity, BatchCallback callback) { }
    }
}
