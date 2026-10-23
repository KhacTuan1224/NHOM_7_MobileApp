package com.rescuefarm.ui.product;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.service.pricing.PriceBreakdown;
import com.rescuefarm.service.pricing.PricingService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ProductViewModel extends ViewModel {
    private final ProductRepository repository; private final AuthRepository authRepository;
    private final PricingService pricingService = new PricingService();
    private final MutableLiveData<ProductScreenState> state =
            new MutableLiveData<>(ProductScreenState.idle());
    private final MutableLiveData<PriceBreakdown> priceBreakdown = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sellerProductsHaveMore = new MutableLiveData<>(false);
    public ProductViewModel(ProductRepository repository, AuthRepository authRepository) {
        this.repository = repository; this.authRepository = authRepository;
    }

    public LiveData<ProductScreenState> getState() { return state; }
    public LiveData<PriceBreakdown> getPriceBreakdown() { return priceBreakdown; }


    public LiveData<List<Category>> getCategories() { return repository.observeCategories(); }
    public LiveData<List<Product>> getProducts() { return repository.observeProducts(); }
    private final androidx.lifecycle.MediatorLiveData<List<Product>> publicProducts = new androidx.lifecycle.MediatorLiveData<>();
    private boolean publicSourceBound;
    private boolean publicPageLoading;
    private List<Product> publicCandidates = new ArrayList<>();
    private final Map<String, LiveData<List<ProductBatch>>> publicBatchSources = new HashMap<>();
    private final Map<String, List<ProductBatch>> publicBatches = new HashMap<>();
    public LiveData<List<Product>> getPublicProducts() {
        if (!publicSourceBound) {
            publicSourceBound = true;
            publicProducts.addSource(repository.observePublicProductPage(), values -> {
                publicCandidates = values == null ? new ArrayList<>() : new ArrayList<>(values);
                java.util.Set<String> needed = new java.util.HashSet<>();
                for (Product product : publicCandidates) if (product != null && product.isAvailable()) needed.add(product.getId());
                for (String id : new ArrayList<>(publicBatchSources.keySet())) if (!needed.contains(id)) {
                    publicProducts.removeSource(publicBatchSources.remove(id)); publicBatches.remove(id);
                }
                for (String id : needed) if (!publicBatchSources.containsKey(id)) {
                    LiveData<List<ProductBatch>> source = repository.observeBatches(id);
                    publicBatchSources.put(id, source);
                    publicProducts.addSource(source, batches -> { publicBatches.put(id, batches); refreshPublicAvailability(); });
                }
                refreshPublicAvailability();
            });
        }
        return publicProducts;
    }
    public List<Product> getPublicProductsSnapshot() {
        return sellablePublicProducts();
    }
    private List<Product> sellablePublicProducts() {
        List<Product> result = new ArrayList<>(); Date now = new Date();
        for (Product product : publicCandidates) if (product != null && product.isAvailable()
                && ProductBatch.hasSellableBatch(product.getId(), publicBatches.get(product.getId()), now)) result.add(product);
        return result;
    }
    public void refreshPublicAvailability() { publicProducts.setValue(sellablePublicProducts()); }
    public boolean hasMorePublicProducts() { return repository.hasMorePublicProducts(); }
    public boolean isPublicPageLoading() { return publicPageLoading; }
    public void refreshPublicPage(boolean reset) {
        if (publicPageLoading) return;
        publicPageLoading = true; state.setValue(ProductScreenState.loading());
        repository.refreshPublicProductPage(reset, new ProductRepository.ActionCallback() {
            public void onSuccess() { publicPageLoading = false; state.postValue(ProductScreenState.idle()); }
            public void onError(ProductRepository.ErrorCode error, String message) {
                publicPageLoading = false; state.postValue(ProductScreenState.error(message));
            }
        });
    }
    public LiveData<List<Product>> getSellerProducts() {
        String id = currentUserId();
        return androidx.lifecycle.Transformations.map(repository.observeSellerProducts(id), products -> {
            sellerProductsHaveMore.setValue(repository.hasMoreSellerProducts(id)); return products;
        });
    }
    public LiveData<Boolean> getSellerProductsHaveMore() { return sellerProductsHaveMore; }
    public LiveData<Product> observeProduct(String productId) {
        return repository.observeProduct(productId);
    }


    public LiveData<List<ProductBatch>> getBatches(String productId) {
        return repository.observeBatches(productId);
    }
    public String currentUserId() {
        String value = authRepository.getCurrentUserId(); return value == null ? "" : value;
    }

    public void refreshCatalog() { repository.refreshCatalog(actionCallback(null)); }
    public void refreshSellerProducts() {
        String sellerId = requireUserId(); if (sellerId != null) repository.refreshSellerProducts(sellerId, actionCallback(null));
    }
    private boolean sellerPageLoading;
    public boolean isSellerPageLoading() { return sellerPageLoading; }
    public void refreshSellerProductPage(boolean reset) {
        if (sellerPageLoading) return;
        String sellerId = requireUserId(); if (sellerId == null) return;
        sellerPageLoading = true;
        state.setValue(ProductScreenState.loading());
        repository.refreshSellerProductPage(sellerId, reset, new ProductRepository.ActionCallback() {
            @Override public void onSuccess() { sellerPageLoading = false; sellerProductsHaveMore.postValue(repository.hasMoreSellerProducts(sellerId)); state.postValue(ProductScreenState.idle()); }
            @Override public void onError(ProductRepository.ErrorCode error, String message) { sellerPageLoading = false; state.postValue(ProductScreenState.error(message)); }
        });
    }
    public void refreshBatches(String productId) { repository.refreshBatches(productId, actionCallback(null)); }
    public void loadProduct(String productId) {
        state.setValue(ProductScreenState.loading());
        repository.getProduct(productId, new ProductRepository.ProductCallback() {
            @Override public void onSuccess(Product product) { state.postValue(ProductScreenState.product(product)); }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(ProductScreenState.error(message));
            }
        });
    }


    public void calculatePrice(Product product, double quantity) {
        if (product == null) return;
        try { priceBreakdown.setValue(pricingService.calculatePrice(product.getOriginalPrice(), product.getRescuePrice(), quantity)); }
        catch (IllegalArgumentException error) { state.setValue(ProductScreenState.error(error.getMessage())); }
    }

    public void saveProduct(String id, String categoryId, String name, String description,
            String originalPrice, String rescuePrice, String unit, String origin, String province,
            String imageUrls, ProductStatus status) {
        saveProduct(id, categoryId, name, description, originalPrice, rescuePrice, unit,
                origin, province, imageUrls, status, id == null || id.trim().isEmpty());
    }

    public void saveProduct(String id, String categoryId, String name, String description,
            String originalPrice, String rescuePrice, String unit, String origin, String province,
            String imageUrls, ProductStatus status, boolean creatingNewProduct) {
        String validation = ProductValidator.validateProduct(categoryId, name, originalPrice, rescuePrice, unit);
        if (validation != null) { state.setValue(ProductScreenState.error(validation)); return; }
        String sellerId = requireUserId(); if (sellerId == null) return;
        Product product = new Product(id, sellerId, categoryId, name, description,
                ProductValidator.parseNumber(originalPrice), ProductValidator.parseNumber(rescuePrice), unit,
                origin, province, parseUrls(imageUrls), status);
        state.setValue(ProductScreenState.loading());
        ProductRepository.ProductCallback callback = new ProductRepository.ProductCallback() {
            @Override public void onSuccess(Product value) {
                state.postValue(ProductScreenState.saved("Đã lưu sản phẩm."));
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(ProductScreenState.error(message));
            }
        };
        if (creatingNewProduct) repository.createProduct(product, callback);
        else repository.saveProduct(product, callback);
    }

    public void hideProduct(String productId) {
        state.setValue(ProductScreenState.loading());
        repository.hideProduct(productId, actionCallback("Đã ẩn sản phẩm."));
    }

    public void showProduct(String productId) {
        state.setValue(ProductScreenState.loading());
        repository.showProduct(productId, actionCallback("Đã hiện sản phẩm."));
    }

    public void saveBatch(String id, String productId, String harvestDate, String expiryDate,
            String initialQuantity, long expectedVersion) {
        Date now = new Date();
        String validation = ProductValidator.validateBatch(harvestDate, expiryDate, initialQuantity, now);
        if (validation != null) { state.setValue(ProductScreenState.error(validation)); return; }
        ProductBatch batch = new ProductBatch(id, productId, ProductValidator.parseNumber(initialQuantity));
        batch.updateDetails(ProductValidator.parseDate(harvestDate), ProductValidator.parseDate(expiryDate),
                batch.getInitialQuantity(), now);
        state.setValue(ProductScreenState.loading());
        repository.saveBatch(batch, expectedVersion, new ProductRepository.BatchCallback() {
            @Override public void onSuccess(ProductBatch value) {
                state.postValue(ProductScreenState.saved("Đã lưu batch."));
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(ProductScreenState.error(message));
            }
        });
    }

    public void deleteBatch(String batchId, long expectedVersion) {
        state.setValue(ProductScreenState.loading());
        repository.deleteBatch(batchId, expectedVersion, actionCallback("Đã lưu trữ lô hàng; lịch sử giao dịch được giữ nguyên."));
    }

    private ProductRepository.ActionCallback actionCallback(String successMessage) {
        return new ProductRepository.ActionCallback() {
            @Override public void onSuccess() {
                if (successMessage != null) state.postValue(ProductScreenState.saved(successMessage));
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(ProductScreenState.error(message));
            }
        };
    }
    private String requireUserId() {
        String value = currentUserId();
        if (!authRepository.isAuthenticated() || value.isEmpty()) {
            state.setValue(ProductScreenState.error("Vui lòng đăng nhập seller.")); return null;
        }
        return value;
    }
    private List<String> parseUrls(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String item : Arrays.asList(value.split("[,\\n]"))) {
            String clean = item.trim(); if (!clean.isEmpty()) result.add(clean);
        }
        return result;
    }


    private static String clean(String value) { return value == null ? "" : value.trim(); }
    @Override protected void onCleared() { repository.close(); super.onCleared(); }
}
