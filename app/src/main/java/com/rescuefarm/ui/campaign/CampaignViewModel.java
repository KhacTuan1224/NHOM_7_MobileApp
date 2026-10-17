package com.rescuefarm.ui.campaign;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.service.location.LocationProvider;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CampaignViewModel extends ViewModel {
    private final CampaignRepository repository; private final AuthRepository authRepository;
    private final ProductRepository productRepository;
    private final LocationProvider locationProvider;
    private final MutableLiveData<CampaignScreenState> state =
            new MutableLiveData<>(CampaignScreenState.idle());
    private final MediatorLiveData<List<CampaignBatchOption>> batchOptions = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> sellerCampaignsHaveMore = new MutableLiveData<>(false);
    private final Map<String, LiveData<List<ProductBatch>>> batchSources = new HashMap<>();
    private final Map<String, List<ProductBatch>> batchesByProduct = new HashMap<>();
    private List<Product> sellerProducts = new ArrayList<>();
    public CampaignViewModel(CampaignRepository repository, AuthRepository authRepository,
            ProductRepository productRepository, LocationProvider locationProvider) {
        this.repository = repository; this.authRepository = authRepository;
        this.productRepository = productRepository; this.locationProvider = locationProvider;
        batchOptions.setValue(new ArrayList<>());
        String sellerId = currentUserId();
        batchOptions.addSource(productRepository.observeSellerProducts(sellerId), values -> {
            sellerProducts = values == null ? new ArrayList<>() : new ArrayList<>(values);
            syncBatchSources(); publishBatchOptions();
        });
    }
    public LiveData<CampaignScreenState> getState() { return state; }
    public LiveData<List<RescueCampaign>> getActiveCampaigns() { return repository.observeActiveCampaigns(); }
    public LiveData<List<RescueCampaign>> getSellerCampaigns() {
        return repository.observeSellerCampaigns(currentUserId());
    }
    public LiveData<Boolean> getSellerCampaignsHaveMore() { return sellerCampaignsHaveMore; }
    public LiveData<RescueCampaign> observeCampaign(String campaignId) {
        return repository.observeCampaign(campaignId);
    }
    public LiveData<List<CampaignBatchOption>> getBatchOptions() { return batchOptions; }
    public void loadSellerBatches(String campaignId) {
        String sellerId = requireSeller(); if (sellerId == null) return;
        productRepository.refreshSellerProducts(sellerId, new ProductRepository.ActionCallback() {
            @Override public void onSuccess() {
                // Room LiveData updates asynchronously; batch refresh starts when each product source appears.
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(CampaignScreenState.error("Không tải được sản phẩm của Seller: " + message));
            }
        });
    }
    public void refreshActive() { repository.refreshActiveCampaigns(action(null)); }
    public void refreshSeller() {
        String id = requireSeller(); if (id != null) repository.refreshSellerCampaigns(id, action(null));
    }
    public void refreshSellerPage(boolean reset) {
        String id = requireSeller(); if (id == null) return;
        repository.refreshSellerCampaignPage(id, reset, new CampaignRepository.ActionCallback() {
            @Override public void onSuccess() { sellerCampaignsHaveMore.postValue(repository.hasMoreSellerCampaigns(id)); }
            @Override public void onError(CampaignRepository.ErrorCode error, String message) { state.postValue(CampaignScreenState.error(message)); }
        });
    }
    public void load(String campaignId) {
        state.setValue(CampaignScreenState.loading());
        repository.getCampaign(campaignId, callback(false, null));
    }
    public void save(String id, String title, String description, RescueReason reason,
            RescueMode mode, Map<String, Double> targets, String startDate, String endDate,
            String latitude, String longitude, String locationName, boolean submit) {
        save(id, title, description, reason, mode, targets, startDate, endDate, latitude,
                longitude, locationName, submit, new ArrayList<>(), new ArrayList<>());
    }
    public void save(String id, String title, String description, RescueReason reason,
            RescueMode mode, Map<String, Double> targets, String startDate, String endDate,
            String latitude, String longitude, String locationName, boolean submit,
            List<String> imageUrls, List<String> imagePublicIds) {
        if (submit && (description == null || description.trim().length() < 10
                || description.trim().length() > 3000)) {
            state.setValue(CampaignScreenState.error("Nội dung câu chuyện cần từ 10 đến 3000 ký tự."));
            return;
        }
        String validation = CampaignValidator.validate(title, targets, startDate, endDate,
                latitude, longitude);
        if (validation != null) { state.setValue(CampaignScreenState.error(validation)); return; }
        String sellerId = requireSeller(); if (sellerId == null) return;
        Map<String, String> units = new HashMap<>();
        if (batchOptions.getValue() != null) for (CampaignBatchOption option : batchOptions.getValue())
            units.put(option.getBatch().getId(), com.rescuefarm.service.campaign.QuantityUnitPolicy.normalize(option.getProduct().getUnit()));
        String quantityUnit = null;
        for (String batchId : targets.keySet()) {
            String unit = units.get(batchId);
            if (unit == null || unit.isEmpty()) {
                state.setValue(CampaignScreenState.error("Chưa tải đủ thông tin đơn vị của lô. Hãy tải lại trước khi lưu.")); return;
            }
            if (quantityUnit != null && !quantityUnit.equals(unit)) {
                state.setValue(CampaignScreenState.error("Các lô trong Campaign phải có cùng đơn vị tính. Không cộng bó/bao với kg.")); return;
            }
            quantityUnit = unit;
        }
        double target = 0D; for (double quantity : targets.values()) target += quantity;
        try {
            RescueCampaign campaign = new RescueCampaign(id, sellerId, target);
            campaign.setQuantityUnit(quantityUnit, "DECLARED");
            campaign.defineCampaign(title, description, reason, mode, targets);
            campaign.setStoryMedia(imageUrls, imagePublicIds);
            campaign.schedule(CampaignValidator.parseDate(startDate), CampaignValidator.parseDate(endDate));
            campaign.updateFixedLocation(CampaignValidator.parseNumber(latitude),
                    CampaignValidator.parseNumber(longitude), locationName);
            state.setValue(CampaignScreenState.loading());
            repository.saveCampaign(campaign, submit, callback(true,
                    submit ? "Đã gửi campaign chờ duyệt." : "Đã lưu bản nháp."));
        } catch (IllegalArgumentException | IllegalStateException error) {
            state.setValue(CampaignScreenState.error(error.getMessage()));
        }
    }
    public void updateCurrentLocation(String campaignId) {
        state.setValue(CampaignScreenState.loading());
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override public void onLocationAvailable(double latitude, double longitude) {
                repository.updateMobileLocation(campaignId, latitude, longitude, true,
                        new CampaignRepository.CampaignCallback() {
                            @Override public void onSuccess(RescueCampaign campaign) {
                                state.postValue(CampaignScreenState.location(campaign,
                                        "Đã cập nhật vị trí mobile rescue."));
                            }
                            @Override public void onError(CampaignRepository.ErrorCode error, String message) {
                                state.postValue(CampaignScreenState.error(message));
                            }
                        });
            }
            @Override public void onLocationUnavailable(String message) {
                state.postValue(CampaignScreenState.error(message));
            }
        });
    }
    public void stopLocationSharing(String campaignId) {
        repository.updateMobileLocation(campaignId, 0D, 0D, false,
                new CampaignRepository.CampaignCallback() {
                    @Override public void onSuccess(RescueCampaign campaign) {
                        state.postValue(CampaignScreenState.location(campaign, "Đã tắt chia sẻ vị trí."));
                    }
                    @Override public void onError(CampaignRepository.ErrorCode error, String message) {
                        state.postValue(CampaignScreenState.error(message));
                    }
                });
    }
    private CampaignRepository.CampaignCallback callback(boolean save, String message) {
        return new CampaignRepository.CampaignCallback() {
            @Override public void onSuccess(RescueCampaign campaign) {
                state.postValue(save ? CampaignScreenState.saved(campaign, message)
                        : CampaignScreenState.campaign(campaign));
            }
            @Override public void onError(CampaignRepository.ErrorCode error, String value) {
                state.postValue(CampaignScreenState.error(value));
            }
        };
    }
    private CampaignRepository.ActionCallback action(String message) {
        return new CampaignRepository.ActionCallback() {
            @Override public void onSuccess() { }
            @Override public void onError(CampaignRepository.ErrorCode error, String value) {
                state.postValue(CampaignScreenState.error(value));
            }
        };
    }
    private String currentUserId() {
        String id = authRepository.getCurrentUserId(); return id == null ? "" : id;
    }
    private void syncBatchSources() {
        Set<String> needed = new HashSet<>();
        for (Product product : sellerProducts) if (product != null && product.getId() != null
                && !product.getId().trim().isEmpty()) needed.add(product.getId());
        for (String id : new HashSet<>(batchSources.keySet())) if (!needed.contains(id)) {
            batchOptions.removeSource(batchSources.remove(id)); batchesByProduct.remove(id);
        }
        for (Product product : sellerProducts) if (product != null && product.getId() != null
                && !batchSources.containsKey(product.getId())) {
            String productId = product.getId();
            LiveData<List<ProductBatch>> source = productRepository.observeBatches(productId);
            batchSources.put(productId, source);
            batchOptions.addSource(source, values -> {
                batchesByProduct.put(productId, values == null ? new ArrayList<>() : new ArrayList<>(values));
                publishBatchOptions();
            });
            productRepository.refreshBatches(productId, new ProductRepository.ActionCallback() {
                @Override public void onSuccess() { }
                @Override public void onError(ProductRepository.ErrorCode error, String message) {
                    state.postValue(CampaignScreenState.error("Không tải được batch của "
                            + product.getName() + ": " + message));
                }
            });
        }
    }
    private void publishBatchOptions() {
        List<CampaignBatchOption> result = new ArrayList<>();
        for (Product product : sellerProducts) {
            List<ProductBatch> values = batchesByProduct.get(product.getId());
            if (values != null) for (ProductBatch batch : values)
                result.add(new CampaignBatchOption(product, batch));
        }
        batchOptions.setValue(result);
    }
    private String requireSeller() {
        String id = currentUserId();
        if (!authRepository.isAuthenticated() || id.isEmpty()) {
            state.setValue(CampaignScreenState.error("Vui lòng đăng nhập seller.")); return null;
        }
        return id;
    }
    @Override protected void onCleared() {
        repository.close(); productRepository.close(); locationProvider.close(); super.onCleared();
    }
}
