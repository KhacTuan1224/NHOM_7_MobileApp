package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
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
    interface ProductListCallback {
        void onSuccess(List<Product> products);
        void onError(ErrorCode error, String message);
    }
    interface BatchListCallback {
        void onSuccess(List<ProductBatch> batches);
        void onError(ErrorCode error, String message);
    }
    interface CartQuoteCallback {
        void onSuccess(Product product, ProductBatch batch);
        void onError(ErrorCode error, String message);
    }

    LiveData<List<Category>> observeCategories();
    LiveData<List<Product>> observeProducts();
    default LiveData<List<Product>> observePublicProductPage() { return observeProducts(); }
    default void refreshPublicProductPage(boolean reset, ActionCallback callback) { refreshCatalog(callback); }
    default boolean hasMorePublicProducts() { return false; }
    LiveData<List<Product>> observeSellerProducts(String sellerId);
    default LiveData<Product> observeProduct(String productId) {
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
    default void showProduct(String productId, ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Không thể hiện lại sản phẩm.");
    }
    void saveBatch(ProductBatch batch, long expectedVersion, BatchCallback callback);
    void deleteBatch(String batchId, long expectedVersion, ActionCallback callback);
    void mutateStock(String batchId, long expectedVersion, StockMutation mutation,
            double quantity, BatchCallback callback);


    default void getCartQuote(String productId, String batchId, CartQuoteCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Không thể revalidate giỏ khi offline.");
    }
    default void close() { }
}
