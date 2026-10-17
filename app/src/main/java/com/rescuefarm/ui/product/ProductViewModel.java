package com.rescuefarm.ui.product;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.enums.PromotionType;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Promotion;
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
    private final MutableLiveData<Promotion> promotion = new MutableLiveData<>();
    private final MutableLiveData<List<Promotion>> sellerPromotions = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> sellerProductsHaveMore = new MutableLiveData<>(false);
    public ProductViewModel(ProductRepository repository, AuthRepository authRepository) {
        this.repository = repository; this.authRepository = authRepository;
    }

    public LiveData<ProductScreenState> getState() { return state; }
    public LiveData<PriceBreakdown> getPriceBreakdown() { return priceBreakdown; }
    public LiveData<Promotion> getPromotion() { return promotion; }
    public LiveData<List<Promotion>> getSellerPromotionsState() { return sellerPromotions; }
    public LiveData<List<Category>> getCategories() { return repository.observeCategories(); }
    public LiveData<List<Product>> getProducts() { return repository.observeProducts(); }
    public LiveData<List<Product>> getSellerProducts() {
        return repository.observeSellerProducts(currentUserId());
    }
    public LiveData<Boolean> getSellerProductsHaveMore() { return sellerProductsHaveMore; }
    public LiveData<Product> observeProduct(String productId) {
        return repository.observeProduct(productId);
    }
    public LiveData<Promotion> observePromotion(String productId) {
        return repository.observePromotion(productId);
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
    public void refreshSellerProductPage(boolean reset) {
        String sellerId = requireUserId(); if (sellerId == null) return;
        repository.refreshSellerProductPage(sellerId, reset, new ProductRepository.ActionCallback() {
            @Override public void onSuccess() { sellerProductsHaveMore.postValue(repository.hasMoreSellerProducts(sellerId)); }
            @Override public void onError(ProductRepository.ErrorCode error, String message) { state.postValue(ProductScreenState.error(message)); }
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
        if (!Double.isFinite(quantity) || quantity <= 0D) {
            state.setValue(ProductScreenState.error("Số lượng phải là số dương.")); return;
        }
        repository.getPromotion(product.getId(), new ProductRepository.PromotionCallback() {
            @Override public void onSuccess(Promotion value) {
                promotion.postValue(value);
                try { priceBreakdown.postValue(pricingService.calculatePrice(product.getOriginalPrice(),
                        product.getRescuePrice(), quantity, value, new Date())); }
                catch (IllegalArgumentException error) { state.postValue(ProductScreenState.error(error.getMessage())); }
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                if (error == ProductRepository.ErrorCode.NOT_CONFIGURED
                        || error == ProductRepository.ErrorCode.NOT_FOUND
                        || error == ProductRepository.ErrorCode.NETWORK
                        || error == ProductRepository.ErrorCode.FORBIDDEN) {
                    promotion.postValue(null);
                    priceBreakdown.postValue(pricingService.calculatePrice(product.getOriginalPrice(),
                            product.getRescuePrice(), quantity, null, new Date()));
                } else state.postValue(ProductScreenState.error(message));
            }
        });
    }

    public void loadPromotion(String productId) {
        repository.getPromotion(productId, new ProductRepository.PromotionCallback() {
            @Override public void onSuccess(Promotion value) { promotion.postValue(value); }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(ProductScreenState.error(message));
            }
        });
    }

    public void loadSellerPromotions() {
        String sellerId = requireUserId(); if (sellerId == null) return;
        repository.getSellerPromotions(sellerId, new ProductRepository.PromotionListCallback() {
            @Override public void onSuccess(List<Promotion> values) { sellerPromotions.postValue(values); }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                state.postValue(ProductScreenState.error(message));
            }
        });
    }

    public void savePromotion(String productId, PromotionType type, String valueText, String tiersText,
            String startText, String endText, boolean active) {
        String sellerId = requireUserId(); if (sellerId == null) return;
        try {
            double value = type == PromotionType.VOLUME && clean(valueText).isEmpty()
                    ? 0D : ProductValidator.parseNumber(valueText);
            Promotion candidate = new Promotion(productId, sellerId, productId, type, value,
                    parseTiers(tiersText), ProductValidator.parseDate(startText),
                    ProductValidator.parseDate(endText), active);
            state.setValue(ProductScreenState.loading());
            repository.savePromotion(candidate, new ProductRepository.PromotionCallback() {
                @Override public void onSuccess(Promotion saved) {
                    promotion.postValue(saved); state.postValue(ProductScreenState.saved("Đã lưu khuyến mãi."));
                }
                @Override public void onError(ProductRepository.ErrorCode error, String message) {
                    state.postValue(ProductScreenState.error(message));
                }
            });
        } catch (IllegalArgumentException error) {
            state.setValue(ProductScreenState.error("Khuyến mãi không hợp lệ: " + error.getMessage()));
        }
    }

    public void setPromotionActive(String productId, boolean active) {
        repository.setPromotionActive(productId, active,
                actionCallback(active ? "Đã bật khuyến mãi." : "Đã tắt khuyến mãi."));
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
    private Map<String, Double> parseTiers(String value) {
        Map<String, Double> result = new HashMap<>();
        if (clean(value).isEmpty()) return result;
        for (String line : value.split("[,\\n]")) {
            String[] pair = line.trim().split("=", 2);
            if (pair.length != 2) throw new IllegalArgumentException("Tier phải có dạng số_lượng=phần_trăm");
            double threshold = ProductValidator.parseNumber(pair[0]);
            double discount = ProductValidator.parseNumber(pair[1]);
            result.put(Double.toString(threshold), discount);
        }
        return result;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    @Override protected void onCleared() { repository.close(); super.onCleared(); }
}
