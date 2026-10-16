package com.rescuefarm.data.repository;

import static org.junit.Assert.*;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.local.dao.CatalogCacheDao;
import com.rescuefarm.data.local.entity.CategoryCacheEntity;
import com.rescuefarm.data.local.entity.ProductBatchCacheEntity;
import com.rescuefarm.data.local.entity.ProductCacheEntity;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OfflineProductRepositoryTest {

    private ExecutorService executor;
    private FakeCatalogCacheDao dao;
    private OfflineProductRepository repository;

    @Before public void setUp() {
        executor = Executors.newSingleThreadExecutor();
        dao = new FakeCatalogCacheDao();
        repository = new OfflineProductRepository(dao, executor);
    }

    @After public void tearDown() {
        repository.close();
    }

    @Test public void getBatchesByIds_returnsCachedBatches() throws Exception {
        ProductBatchCacheEntity batch = new ProductBatchCacheEntity("batch-1");
        batch.productId = "prod-1";
        batch.initialQuantity = 100.0;
        batch.availableQuantity = 80.0;
        batch.reservedQuantity = 10.0;
        batch.soldQuantity = 10.0;
        batch.harvestAtEpochMillis = System.currentTimeMillis();
        batch.expiresAtEpochMillis = System.currentTimeMillis() + 86400000L;
        batch.status = "SELLABLE";
        dao.batches.put(batch.id, batch);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<ProductBatch>> resultRef = new AtomicReference<>();
        repository.getBatchesByIds(Collections.singletonList("batch-1"), new ProductRepository.BatchListCallback() {
            @Override public void onSuccess(List<ProductBatch> batches) {
                resultRef.set(batches);
                latch.countDown();
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertEquals(1, resultRef.get().size());
        assertEquals("batch-1", resultRef.get().get(0).getId());
    }

    @Test public void getCampaignProducts_resolvesProductsFromBatches() throws Exception {
        ProductBatchCacheEntity batch1 = new ProductBatchCacheEntity("batch-1");
        batch1.productId = "prod-1";
        batch1.initialQuantity = 50.0;
        batch1.availableQuantity = 50.0;
        batch1.harvestAtEpochMillis = System.currentTimeMillis();
        batch1.expiresAtEpochMillis = System.currentTimeMillis() + 86400000L;
        batch1.status = "SELLABLE";
        dao.batches.put(batch1.id, batch1);

        ProductCacheEntity prod1 = new ProductCacheEntity("prod-1");
        prod1.sellerId = "seller-1";
        prod1.categoryId = "cat-1";
        prod1.name = "Dưa hấu Quảng Ngãi";
        prod1.description = "Dưa ngọt mát";
        prod1.originalPrice = 25000.0;
        prod1.rescuePrice = 12000.0;
        prod1.unit = "kg";
        prod1.origin = "Quảng Ngãi";
        prod1.province = "Quảng Ngãi";
        prod1.status = "ACTIVE";
        dao.products.put(prod1.id, prod1);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Product>> resultRef = new AtomicReference<>();
        repository.getCampaignProducts(Collections.singletonList("batch-1"), new ProductRepository.ProductListCallback() {
            @Override public void onSuccess(List<Product> products) {
                resultRef.set(products);
                latch.countDown();
            }
            @Override public void onError(ProductRepository.ErrorCode error, String message) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertEquals(1, resultRef.get().size());
        assertEquals("Dưa hấu Quảng Ngãi", resultRef.get().get(0).getName());
        assertEquals(12000.0, resultRef.get().get(0).getRescuePrice(), 0.001);
    }

    private static final class FakeCatalogCacheDao implements CatalogCacheDao {
        final Map<String, ProductCacheEntity> products = new HashMap<>();
        final Map<String, ProductBatchCacheEntity> batches = new HashMap<>();

        @Override public LiveData<List<CategoryCacheEntity>> observeActiveCategories() { return new MutableLiveData<>(); }
        @Override public LiveData<List<ProductCacheEntity>> observeProducts() { return new MutableLiveData<>(); }
        @Override public LiveData<List<ProductCacheEntity>> observeActiveProducts() { return new MutableLiveData<>(); }
        @Override public LiveData<List<ProductCacheEntity>> observeSellerProducts(String sellerId) { return new MutableLiveData<>(); }
        @Override public LiveData<List<ProductBatchCacheEntity>> observeBatches(String productId) { return new MutableLiveData<>(); }
        @Override public ProductCacheEntity findProduct(String productId) { return products.get(productId); }
        @Override public ProductBatchCacheEntity findBatch(String batchId) { return batches.get(batchId); }
        @Override public List<ProductBatchCacheEntity> findBatches(String productId) { return new ArrayList<>(); }
        @Override public void replaceCategories(List<CategoryCacheEntity> categories) { }
        @Override public void replaceProducts(List<ProductCacheEntity> products) { }
        @Override public void replaceBatches(List<ProductBatchCacheEntity> batches) { }
        @Override public void replaceProduct(ProductCacheEntity product) { }
        @Override public void replaceBatch(ProductBatchCacheEntity batch) { }
        @Override public void deleteBatch(String batchId) { batches.remove(batchId); }
        @Override public void deleteProduct(String productId) { products.remove(productId); }
        @Override public void clearActiveProducts() { }
        @Override public void clearBatches(String productId) { }
        @Override public void clearCategories() { }
        @Override public void clearSellerProducts(String sellerId) { }
    }
}
