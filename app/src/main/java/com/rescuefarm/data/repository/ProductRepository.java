package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Promotion;
import java.util.Date;
import java.util.List;

public interface ProductRepository {
    enum ErrorCode { NOT_CONFIGURED, NOT_FOUND, FORBIDDEN, VALIDATION, NETWORK, CONFLICT, UNKNOWN }
    enum StockMutation { RESERVE, COMMIT, RELEASE }

    interface ActionCallback {
        void onSuccess();
        void onError(ErrorCode error, String message);
    }
    interface ProductCallback {
        void onSuccess(Product product);
        void onError(ErrorCode error, String message);
    }
    interface BatchCallback {
        void onSuccess(ProductBatch batch);
        void onError(ErrorCode error, String message);
    }
    interface PromotionCallback {
        void onSuccess(Promotion promotion);
        void onError(ErrorCode error, String message);
    }
    interface PromotionListCallback {
        void onSuccess(List<Promotion> promotions);
        void onError(ErrorCode error, String message);
    }
    interface ProductListCallback {
        void onSuccess(List<Product> products);
        void onError(ErrorCode error, String message);
    }
    interface BatchListCallback {
        void onSuccess(List<ProductBatch> batches);
        void onError(ErrorCode error, String message);
    }
    interface CartQuoteCallback {
        void onSuccess(Product product, ProductBatch batch, Promotion promotion);
        void onError(ErrorCode error, String message);
    }

    LiveData<List<Category>> observeCategories();
    LiveData<List<Product>> observeProducts();
    LiveData<List<Product>> observeSellerProducts(String sellerId);
    default LiveData<Product> observeProduct(String productId) {
        return new androidx.lifecycle.MutableLiveData<>();
    }
    default LiveData<Promotion> observePromotion(String productId) {
        return new androidx.lifecycle.MutableLiveData<>();
    }
    LiveData<List<ProductBatch>> observeBatches(String productId);
    void refreshCatalog(ActionCallback callback);
    void refreshSellerProducts(String sellerId, ActionCallback callback);
    default void refreshSellerProductPage(String sellerId, boolean reset, ActionCallback callback) {
        refreshSellerProducts(sellerId, callback);
    }
    default boolean hasMoreSellerProducts(String sellerId) { return false; }
    void refreshBatches(String productId, ActionCallback callback);
    void getProduct(String productId, ProductCallback callback);
    default void getCampaignProducts(List<String> batchIds, ProductListCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Không thể tải sản phẩm của chiến dịch.");
    }
    default void getBatchesByIds(List<String> batchIds, BatchListCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Không thể tải các lô hàng của chiến dịch.");
    }
    void saveProduct(Product product, ProductCallback callback);
    default void createProduct(Product product, ProductCallback callback) {
        saveProduct(product, callback);
    }
    void hideProduct(String productId, ActionCallback callback);
    void saveBatch(ProductBatch batch, long expectedVersion, BatchCallback callback);
    void deleteBatch(String batchId, long expectedVersion, ActionCallback callback);
    void mutateStock(String batchId, long expectedVersion, StockMutation mutation,
            double quantity, BatchCallback callback);
    default void getPromotion(String productId, PromotionCallback callback) {
        callback.onSuccess(null);
    }
    default void getSellerPromotions(String sellerId, PromotionListCallback callback) {
        callback.onSuccess(new java.util.ArrayList<>());
    }
    default void savePromotion(Promotion promotion, PromotionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Promotion repository is not configured.");
    }
    default void setPromotionActive(String productId, boolean active, ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Promotion repository is not configured.");
    }
    default void getCartQuote(String productId, String batchId, CartQuoteCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Không thể revalidate giỏ khi offline.");
    }
    default void close() { }
}
