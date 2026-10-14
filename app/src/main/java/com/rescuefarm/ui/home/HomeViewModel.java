package com.rescuefarm.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.data.repository.UserRepository;
import com.rescuefarm.domain.model.Banner;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.service.location.LocationProvider;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import com.rescuefarm.data.repository.SellerStorefront;


public class HomeViewModel extends ViewModel {
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final LocationProvider locationProvider;
    private final HomeContentBuilder contentBuilder = new HomeContentBuilder();
    private final SavedStateHandle savedState;
    private final DiscoveryEngine discoveryEngine = new DiscoveryEngine();
    private final MediatorLiveData<HomeViewState> homeState = new MediatorLiveData<>();
    private final MutableLiveData<List<DiscoveryResult>> searchResults =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> greetingName = new MutableLiveData<>("bạn");
    private List<Product> products = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private List<RescueCampaign> campaigns = new ArrayList<>();
    private List<Banner> banners = new ArrayList<>();
    private final Map<String, List<ProductBatch>> productBatches = new HashMap<>();
    private final Map<String, LiveData<List<ProductBatch>>> batchSources = new HashMap<>();
    private final Map<String, LiveData<SellerStorefront>> sellerLocationSources = new HashMap<>();
    private final Map<String, double[]> sellerCoordinates = new HashMap<>();
    private DiscoveryQuery query = DiscoveryQuery.empty();
    private Double latitude;
    private Double longitude;
    private HomeViewState.LocationState locationState = HomeViewState.LocationState.PERMISSION_REQUIRED;
    private boolean refreshing;
    private int pendingRefreshes;
    private boolean refreshHadError;
    private String message = "Đang hiển thị dữ liệu đã lưu trên thiết bị.";
    private HomeViewState.DataFreshness freshness = HomeViewState.DataFreshness.STALE;

    public HomeViewModel(ProductRepository productRepository, CampaignRepository campaignRepository,
            AuthRepository authRepository, LocationProvider locationProvider) {
        this(productRepository, campaignRepository, authRepository, locationProvider,
                null, new SavedStateHandle());
    }

    public HomeViewModel(ProductRepository productRepository, CampaignRepository campaignRepository,
            AuthRepository authRepository, LocationProvider locationProvider,
            SavedStateHandle savedState) {
        this(productRepository, campaignRepository, authRepository, locationProvider,
                null, savedState);
    }

    public HomeViewModel(ProductRepository productRepository, CampaignRepository campaignRepository,
            AuthRepository authRepository, LocationProvider locationProvider,
            UserRepository userRepository, SavedStateHandle savedState) {
        this.productRepository = productRepository; this.campaignRepository = campaignRepository;
        this.authRepository = authRepository; this.locationProvider = locationProvider;
        this.userRepository = userRepository;
        this.savedState = savedState;
        this.query = DiscoveryQueryState.restore(savedState);
        loadGreetingName();
        homeState.addSource(productRepository.observeProducts(), values -> {
            products = safe(values); syncBatchSources(); syncSellerLocationSources(); publish();
        });
        homeState.addSource(productRepository.observeCategories(), values -> {
            categories = safe(values); publish();
        });
        homeState.addSource(campaignRepository.observeActiveCampaigns(), values -> {
            campaigns = safe(values); syncBatchSources(); publish();
        });
        homeState.addSource(campaignRepository.observeActiveBanners(), values -> {
            banners = safe(values); publish();
        });
        publish();
    }

    public LiveData<HomeViewState> getHomeState() { return homeState; }
    public LiveData<List<DiscoveryResult>> getSearchResults() { return searchResults; }
    public LiveData<String> getGreetingName() { return greetingName; }
    public List<Category> getCategoriesSnapshot() { return new ArrayList<>(categories); }
    public DiscoveryQuery getQuery() { return query; }
    public List<Product> getProductsSnapshot() { return new ArrayList<>(products); }
    public boolean isAuthenticated() { return authRepository.isAuthenticated(); }

    private void loadGreetingName() {
        String userId = authRepository.getCurrentUserId();
        if (!authRepository.isAuthenticated() || userId == null || userRepository == null) return;
        userRepository.getUser(userId, new UserRepository.UserCallback() {
            @Override public void onSuccess(com.rescuefarm.domain.model.User user) {
                String name = user == null ? null : user.getFullName();
                if (name != null && !name.trim().isEmpty()) greetingName.postValue(name.trim());
            }
            @Override public void onError(UserRepository.ProfileError error, String message) { }
        });
    }

    public void refresh() {
        if (refreshing) return;
        refreshing = true; pendingRefreshes = 3; refreshHadError = false;
        message = "Đang đồng bộ dữ liệu mới…"; publish();
        productRepository.refreshCatalog(new ProductRepository.ActionCallback() {
            @Override public void onSuccess() { finishRefresh(null); }
            @Override public void onError(ProductRepository.ErrorCode error, String value) {
                finishRefresh(value);
            }
        });
        campaignRepository.refreshActiveCampaigns(callback());
        campaignRepository.refreshBanners(callback());
    }

    public void requestNearbyLocation() {
        locationState = HomeViewState.LocationState.LOADING; message = "Đang lấy vị trí hiện tại…";
        publish();
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double valueLatitude, double valueLongitude) {
                latitude = valueLatitude; longitude = valueLongitude;
                syncSellerLocationSources();
                locationState = HomeViewState.LocationState.AVAILABLE;
                message = "Đã sắp xếp điểm giải cứu gần vị trí hiện tại."; publish();
            }
            @Override public void onLocationUnavailable(String value) {
                locationState = HomeViewState.LocationState.UNAVAILABLE; message = value; publish();
            }
        });
    }

    public void onLocationPermissionDenied() {
        locationState = HomeViewState.LocationState.DENIED;
        message = "Chưa có quyền vị trí; các mục gần bạn được để trống."; publish();
    }

    public void setQuery(DiscoveryQuery value) {
        query = value == null ? DiscoveryQuery.empty() : value;
        syncBatchSources(); syncSellerLocationSources(); publishSearch();
        DiscoveryQueryState.save(savedState, query);
    }

    private void syncBatchSources() {
        boolean neededForCampaigns = !campaigns.isEmpty();
        if (!query.needsBatchData() && !neededForCampaigns) {
            for (Map.Entry<String, LiveData<List<ProductBatch>>> entry : batchSources.entrySet()) {
                homeState.removeSource(entry.getValue());
            }
            batchSources.clear(); productBatches.clear();
            return;
        }
        Set<String> needed = new HashSet<>();
        for (Product product : products) if (product != null && product.getId() != null) {
            needed.add(product.getId());
        }
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, LiveData<List<ProductBatch>>> entry : batchSources.entrySet()) {
            if (!needed.contains(entry.getKey())) {
                homeState.removeSource(entry.getValue()); removed.add(entry.getKey());
            }
        }
        for (String productId : removed) { batchSources.remove(productId); productBatches.remove(productId); }
        for (String productId : needed) if (!batchSources.containsKey(productId)) {
            LiveData<List<ProductBatch>> source = productRepository.observeBatches(productId);
            batchSources.put(productId, source);
            homeState.addSource(source, values -> {
                productBatches.put(productId, safe(values)); publish();
            });
        }
    }

    private void syncSellerLocationSources() {
        boolean needs = latitude != null && longitude != null
                && (query.getRadiusKm() > 0D || query.getSort() == DiscoveryQuery.Sort.DISTANCE);
        Set<String> needed = new HashSet<>();
        if (needs) for (Product product : products) if (product != null && product.getSellerId() != null
                && !product.getSellerId().trim().isEmpty()) needed.add(product.getSellerId());
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, LiveData<SellerStorefront>> entry : sellerLocationSources.entrySet()) {
            if (!needed.contains(entry.getKey())) { homeState.removeSource(entry.getValue()); removed.add(entry.getKey()); }
        }
        for (String id : removed) { sellerLocationSources.remove(id); sellerCoordinates.remove(id); }
        for (String id : needed) if (!sellerLocationSources.containsKey(id)) {
            if (userRepository == null) continue;
            LiveData<SellerStorefront> source = userRepository.observePublicStorefront(id);
            sellerLocationSources.put(id, source);
            homeState.addSource(source, value -> {
                if (value == null || !value.configured || value.latitude == 0D && value.longitude == 0D)
                    sellerCoordinates.remove(id);
                else sellerCoordinates.put(id, new double[]{value.latitude, value.longitude});
                publishSearch();
            });
        }
    }

    private CampaignRepository.ActionCallback callback() {
        return new CampaignRepository.ActionCallback() {
            @Override public void onSuccess() { finishRefresh(null); }
            @Override public void onError(CampaignRepository.ErrorCode error, String value) {
                finishRefresh(value);
            }
        };
    }
    private synchronized void finishRefresh(String error) {
        if (error != null && !error.trim().isEmpty()) {
            refreshHadError = true; freshness = HomeViewState.DataFreshness.OFFLINE;
            message = error + " Dữ liệu cache vẫn được giữ và chỉ dùng để tham khảo.";
        }
        pendingRefreshes = Math.max(0, pendingRefreshes - 1);
        if (pendingRefreshes == 0) {
            refreshing = false;
            if (!refreshHadError) { freshness = HomeViewState.DataFreshness.FRESH;
                message = "Đã cập nhật Home."; }
        }
        publish();
    }
    private void publish() {
        homeState.postValue(contentBuilder.build(banners, products, campaigns, categories,
                productBatches, latitude, longitude, new Date(), locationState,
                refreshing, message, freshness));
        publishSearch();
    }
    private void publishSearch() {
        searchResults.postValue(discoveryEngine.search(products, campaigns, productBatches, sellerCoordinates, query,
                latitude, longitude, new Date()));
    }
    private static <T> List<T> safe(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
    @Override protected void onCleared() {
        for (LiveData<SellerStorefront> source : sellerLocationSources.values()) homeState.removeSource(source);
        sellerLocationSources.clear(); sellerCoordinates.clear();
        productRepository.close(); campaignRepository.close(); locationProvider.close();
    }
}
