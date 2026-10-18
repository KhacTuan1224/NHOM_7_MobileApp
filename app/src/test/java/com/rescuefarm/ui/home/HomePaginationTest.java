package com.rescuefarm.ui.home;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.service.location.LocationProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomePaginationTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();
    private static final class Harness {
        List<Boolean> productRequests = new ArrayList<>(), campaignRequests = new ArrayList<>();
        ProductRepository.ActionCallback productCallback;
        CampaignRepository.ActionCallback campaignCallback;
        boolean moreProducts = true, moreCampaigns = true;
        int unboundedRequests;
        HomeViewModel model() {
            ProductRepository products = (ProductRepository) Proxy.newProxyInstance(
                    ProductRepository.class.getClassLoader(), new Class[]{ProductRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "observePublicProductPage": case "observeCategories": return new MutableLiveData<>(new ArrayList<>());
                    case "refreshPublicProductPage": productRequests.add((Boolean)args[0]); productCallback = (ProductRepository.ActionCallback)args[1]; return null;
                    case "hasMorePublicProducts": return moreProducts;
                    case "refreshCatalog": case "observeProducts": unboundedRequests++; throw new AssertionError("Unbounded catalog must not be used by Home");
                    case "close": return null;
                    default: throw new AssertionError(method.getName());
                }
            });
            CampaignRepository campaigns = (CampaignRepository) Proxy.newProxyInstance(
                    CampaignRepository.class.getClassLoader(), new Class[]{CampaignRepository.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "observePublicCampaignPage": case "observeActiveBanners": return new MutableLiveData<>(new ArrayList<>());
                    case "refreshPublicCampaignPage": campaignRequests.add((Boolean)args[0]); campaignCallback = (CampaignRepository.ActionCallback)args[1]; return null;
                    case "refreshBanners": ((CampaignRepository.ActionCallback)args[0]).onSuccess(); return null;
                    case "hasMorePublicCampaigns": return moreCampaigns;
                    case "refreshActiveCampaigns": case "observeActiveCampaigns": unboundedRequests++; throw new AssertionError("Unbounded campaigns must not be used by Home");
                    case "close": return null;
                    default: throw new AssertionError(method.getName());
                }
            });
            AuthRepository auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(),
                    new Class[]{AuthRepository.class}, (proxy, method, args) -> {
                        if (method.getName().equals("isAuthenticated")) return false;
                        if (method.getName().equals("getCurrentUserId")) return "";
                        return null;
                    });
            LocationProvider location = new LocationProvider() {
                public void getCurrentLocation(LocationCallback callback) { }
                public void reverseGeocode(double lat, double lng, GeocodeCallback callback) { }
            };
            return new HomeViewModel(products, campaigns, auth, location);
        }
        void completeInitial() { productCallback.onSuccess(); campaignCallback.onSuccess(); }
    }

    @Test public void initialLoadUsesBoundedPagesAndBlocksConcurrentRequests() {
        Harness h = new Harness(); HomeViewModel vm = h.model();
        vm.ensureLoaded(); vm.ensureLoaded(); vm.loadNextPage(true, true);
        assertEquals(java.util.Arrays.asList(true), h.productRequests);
        assertEquals(java.util.Arrays.asList(true), h.campaignRequests);
        assertTrue(vm.isPageLoading());
        h.completeInitial(); assertFalse(vm.isPageLoading());
        assertEquals(0, h.unboundedRequests);
    }
    @Test public void productSectionLoadsOnlyProductsAndAllowsRetryAfterFailure() {
        Harness h = new Harness(); HomeViewModel vm = h.model(); vm.ensureLoaded(); h.completeInitial();
        vm.loadNextPage(true, false);
        assertEquals(java.util.Arrays.asList(true, false), h.productRequests);
        assertEquals(1, h.campaignRequests.size());
        h.productCallback.onError(ProductRepository.ErrorCode.NETWORK, "Offline");
        assertFalse(vm.isPageLoading());
        vm.loadNextPage(true, false); assertEquals(3, h.productRequests.size());
        h.productCallback.onSuccess(); assertFalse(vm.isPageLoading());
    }
    @Test public void exhaustedSourcesDoNotLoadOrEraseExistingState() {
        Harness h = new Harness(); HomeViewModel vm = h.model(); vm.ensureLoaded(); h.completeInitial();
        h.moreProducts = false; h.moreCampaigns = false;
        vm.loadNextPage(true, true);
        assertFalse(vm.isPageLoading());
        assertEquals(1, h.productRequests.size()); assertEquals(1, h.campaignRequests.size());
    }
    @Test public void combinedLoadWaitsForBothPagesAndCanResetExplicitly() {
        Harness h = new Harness(); HomeViewModel vm = h.model(); vm.ensureLoaded(); h.completeInitial();
        vm.loadNextPage(true, true); h.productCallback.onSuccess(); assertTrue(vm.isPageLoading());
        h.campaignCallback.onSuccess(); assertFalse(vm.isPageLoading());
        vm.refresh(); assertEquals(java.util.Arrays.asList(true, false, true), h.productRequests);
        assertEquals(java.util.Arrays.asList(true, false, true), h.campaignRequests);
    }
}
