package com.rescuefarm.ui.product;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.rescuefarm.data.repository.AuthRepository;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class ProductViewModelTest {
    @Rule public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    @Test public void publicListRequiresLiveBatchAndReappearsAfterNewBatchArrives() {
        FakeProductRepository repository = new FakeProductRepository();
        ProductViewModel viewModel = new ProductViewModel(repository, new FakeAuthRepository());
        viewModel.getPublicProducts().observeForever(products -> { });
        Product product = new Product("product", "seller", "vegetables", "Rau", "", 100, 80, "kg", "", "",
            new ArrayList<>(), com.rescuefarm.domain.enums.ProductStatus.ACTIVE);
        repository.products.setValue(java.util.Collections.singletonList(product));
        assertTrue(viewModel.getPublicProductsSnapshot().isEmpty());
        long now = System.currentTimeMillis();
        ProductBatch old = ProductBatch.restore("old", "product", null, new java.util.Date(1), new java.util.Date(now - 1000),
            10, 10, 0, 0, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE, 1);
        repository.batches.setValue(java.util.Collections.singletonList(old));
        assertTrue(viewModel.getPublicProductsSnapshot().isEmpty());
        ProductBatch fresh = ProductBatch.restore("fresh", "product", null, new java.util.Date(now), new java.util.Date(now + 3600000),
            10, 10, 0, 0, com.rescuefarm.domain.enums.BatchStatus.AVAILABLE, 1);
        repository.batches.setValue(java.util.Arrays.asList(old, fresh));
        assertEquals(1, viewModel.getPublicProducts().getValue().size());
        assertEquals(10, old.getAvailableQuantity(), 0);
    }

    @Test public void invalidPrice_isRejectedBeforeRepository() {
        FakeProductRepository repository = new FakeProductRepository();
        ProductViewModel viewModel = new ProductViewModel(repository, new FakeAuthRepository());
        viewModel.saveProduct("", "vegetables", "Rau", "", "100", "101", "kg", "", "", "", null);
        assertFalse(repository.saveCalled);
        assertEquals(ProductScreenState.Status.ERROR, viewModel.getState().getValue().getStatus());
    }

    @Test public void validProduct_isDelegatedToRepository() {
        FakeProductRepository repository = new FakeProductRepository();
        ProductViewModel viewModel = new ProductViewModel(repository, new FakeAuthRepository());
        viewModel.saveProduct("", "vegetables", "Rau", "", "100", "80", "kg", "", "", "", null);
        assertTrue(repository.saveCalled);
        assertEquals(ProductScreenState.Status.SAVED, viewModel.getState().getValue().getStatus());
    }


    private static final class FakeProductRepository implements ProductRepository {


        private boolean saveCalled;
        private final MutableLiveData<List<Product>> products = new MutableLiveData<>(new ArrayList<>());
        private final MutableLiveData<List<Category>> categories = new MutableLiveData<>(new ArrayList<>());
        private final MutableLiveData<List<ProductBatch>> batches = new MutableLiveData<>(new ArrayList<>());
        @Override public LiveData<List<Category>> observeCategories() { return categories; }
        @Override public LiveData<List<Product>> observeProducts() { return products; }
        @Override public LiveData<List<Product>> observeSellerProducts(String sellerId) { return products; }
        @Override public LiveData<List<ProductBatch>> observeBatches(String productId) { return batches; }
        @Override public void refreshCatalog(ActionCallback callback) { callback.onSuccess(); }
        @Override public void refreshSellerProducts(String sellerId, ActionCallback callback) { callback.onSuccess(); }
        @Override public void refreshBatches(String productId, ActionCallback callback) { callback.onSuccess(); }
        @Override public void getProduct(String productId, ProductCallback callback) { }
        @Override public void saveProduct(Product product, ProductCallback callback) {
            saveCalled = true; callback.onSuccess(product);
        }
        @Override public void hideProduct(String productId, ActionCallback callback) { callback.onSuccess(); }
        @Override public void saveBatch(ProductBatch batch, long expectedVersion, BatchCallback callback) { callback.onSuccess(batch); }
        @Override public void deleteBatch(String batchId, long expectedVersion, ActionCallback callback) { callback.onSuccess(); }
        @Override public void mutateStock(String batchId, long expectedVersion, StockMutation mutation,
                double quantity, BatchCallback callback) { }
    }

    private static final class FakeAuthRepository implements AuthRepository {
        @Override public boolean isAvailable() { return true; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public String getCurrentUserId() { return "seller-1"; }
        @Override public void restoreSession(AuthCallback callback) { }
        @Override public void signIn(String email, String password, AuthCallback callback) { }
        @Override public void register(String email, String password, String fullName, String phone,
                UserRole role, AuthCallback callback) { }
        @Override public void signInWithGoogleIdToken(String idToken, AuthCallback callback) { }
        @Override public void sendPasswordResetEmail(String email, ActionCallback callback) { }
        @Override public void signOut() { }
    }
}
