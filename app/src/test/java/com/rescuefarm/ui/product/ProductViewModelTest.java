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

    @Test public void buyerPricingUsesOnlySavedPromotionAndRecalculatesAfterRemoval() {
        FakeProductRepository repository = new FakeProductRepository();
        ProductViewModel viewModel = new ProductViewModel(repository, new FakeAuthRepository());
        Product product = new Product("product", "seller", "vegetables", "Rau", "", 100, 80, "kg", "", "",
                new ArrayList<>(), com.rescuefarm.domain.enums.ProductStatus.ACTIVE);
        viewModel.calculatePrice(product, 2);
        assertEquals(80, viewModel.getPriceBreakdown().getValue().getFinalUnitPrice(), 0);
        repository.saved = true;
        viewModel.calculatePrice(product, 2);
        assertEquals(72, viewModel.getPriceBreakdown().getValue().getFinalUnitPrice(), 0);
        repository.saved = false;
        viewModel.calculatePrice(product, 2);
        assertEquals(80, viewModel.getPriceBreakdown().getValue().getFinalUnitPrice(), 0);
        assertFalse(repository.rawPromotionRead);
    }

    private static final class FakeProductRepository implements ProductRepository {
        private boolean saved, rawPromotionRead;
        @Override public void getPromotion(String id, PromotionCallback callback) { rawPromotionRead = true; }
        @Override public void getApplicablePromotion(String id, PromotionCallback callback) {
            long now = System.currentTimeMillis();
            callback.onSuccess(saved ? new com.rescuefarm.domain.model.Promotion(id, "seller", id,
                    com.rescuefarm.domain.enums.PromotionType.PERCENT, 10, new java.util.HashMap<>(),
                    new java.util.Date(now - 100000), new java.util.Date(now + 100000), true) : null);
        }
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
