package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.rescuefarm.data.local.dao.CatalogCacheDao;
import com.rescuefarm.data.local.mapper.CatalogCacheMapper;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Promotion;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class OfflineProductRepository implements ProductRepository {
    private static final String MESSAGE = "Firebase chưa được cấu hình; đang hiển thị cache cục bộ.";
    private final CatalogCacheDao dao;
    private final ExecutorService executor;
    public OfflineProductRepository(CatalogCacheDao dao, ExecutorService executor) {
        this.dao = dao; this.executor = executor;
    }

    @Override public LiveData<List<Category>> observeCategories() {
        return Transformations.map(dao.observeActiveCategories(), CatalogCacheMapper::categories);
    }
    @Override public LiveData<List<Product>> observeProducts() {
        return Transformations.map(dao.observeActiveProducts(), CatalogCacheMapper::products);
    }
    @Override public LiveData<List<Product>> observeSellerProducts(String sellerId) {
        return Transformations.map(dao.observeSellerProducts(sellerId), CatalogCacheMapper::products);
    }
    @Override public LiveData<List<ProductBatch>> observeBatches(String productId) {
        return Transformations.map(dao.observeBatches(productId), CatalogCacheMapper::batches);
    }
    @Override public void refreshCatalog(ActionCallback callback) { unavailable(callback); }
    @Override public void refreshSellerProducts(String sellerId, ActionCallback callback) { unavailable(callback); }
    @Override public void refreshBatches(String productId, ActionCallback callback) { unavailable(callback); }
    @Override public void getProduct(String productId, ProductCallback callback) {
        executor.execute(() -> {
            com.rescuefarm.data.local.entity.ProductCacheEntity cached = dao.findProduct(productId);
            if (cached == null) callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm trong cache.");
            else callback.onSuccess(CatalogCacheMapper.toDomain(cached));
        });
    }
    @Override public void getBatchesByIds(List<String> batchIds, BatchListCallback callback) {
        executor.execute(() -> {
            java.util.ArrayList<ProductBatch> values = new java.util.ArrayList<>();
            if (batchIds != null) for (String id : batchIds) {
                if (id == null || id.trim().isEmpty()) continue;
                com.rescuefarm.data.local.entity.ProductBatchCacheEntity cached = dao.findBatch(id.trim());
                if (cached != null) values.add(CatalogCacheMapper.toDomain(cached));
            }
            callback.onSuccess(values);
        });
    }
    @Override public void saveProduct(Product product, ProductCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, MESSAGE);
    }
    @Override public void hideProduct(String productId, ActionCallback callback) { unavailable(callback); }
    @Override public void saveBatch(ProductBatch batch, long expectedVersion, BatchCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, MESSAGE);
    }
    @Override public void deleteBatch(String batchId, long expectedVersion, ActionCallback callback) {
        unavailable(callback);
    }
    @Override public void mutateStock(String batchId, long expectedVersion, StockMutation mutation,
            double quantity, BatchCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, MESSAGE);
    }
    @Override public void getPromotion(String productId, PromotionCallback callback) {
        callback.onSuccess(null);
    }
    @Override public void getSellerPromotions(String sellerId, PromotionListCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, MESSAGE);
    }
    @Override public void savePromotion(Promotion promotion, PromotionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, MESSAGE);
    }
    @Override public void setPromotionActive(String productId, boolean active, ActionCallback callback) {
        unavailable(callback);
    }
    @Override public void getCartQuote(String productId, String batchId, CartQuoteCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Đang offline; giá và tồn kho chưa được revalidate.");
    }
    private void unavailable(ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, MESSAGE);
    }
    @Override public void close() { executor.shutdownNow(); }
}
