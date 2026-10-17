package com.rescuefarm.data.remote.firebase;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.rescuefarm.data.local.dao.CatalogCacheDao;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.CategoryCacheEntity;
import com.rescuefarm.data.local.entity.ProductBatchCacheEntity;
import com.rescuefarm.data.local.entity.ProductCacheEntity;
import com.rescuefarm.data.local.mapper.CatalogCacheMapper;
import com.rescuefarm.data.repository.ProductRepository;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.ProductStatus;
import com.rescuefarm.domain.enums.PromotionType;
import com.rescuefarm.domain.model.Category;
import com.rescuefarm.domain.model.Product;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.Promotion;
import com.rescuefarm.service.inventory.InventoryService;
import com.rescuefarm.service.inventory.InventoryVersionPolicy;
import com.rescuefarm.service.network.NetworkStatusProvider;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

public class FirebaseProductRepository implements ProductRepository {
    private static final String CATEGORIES = "categories";
    private static final String PRODUCTS = "products";
    private static final String BATCHES = "productBatches";
    private static final String PROMOTIONS = "promotions";
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final RescueFarmDatabase database;
    private final CatalogCacheDao dao;
    private final ExecutorService cacheExecutor;
    private final NetworkStatusProvider network;
    private final Map<String, DocumentSnapshot> sellerProductCursors = new HashMap<>();
    private final Set<String> sellersWithMoreProducts = new HashSet<>();
    private static final int SELLER_PAGE_SIZE = 50;
    private final InventoryService inventoryService = new InventoryService();

    public FirebaseProductRepository(RescueFarmDatabase database, ExecutorService cacheExecutor,
            NetworkStatusProvider network) {
        this.database = database; this.dao = database.catalogCacheDao(); this.cacheExecutor = cacheExecutor;
        this.network = network;
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
    @Override public LiveData<Product> observeProduct(String productId) {
        return new LiveData<Product>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = firestore.collection(PRODUCTS).document(productId)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            Product product = snapshot.exists() ? mapProduct(snapshot) : null;
                            postValue(product);
                            if (product != null) cacheExecutor.execute(() -> dao.replaceProduct(
                                    CatalogCacheMapper.toEntity(product, System.currentTimeMillis())));
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }
    @Override public LiveData<List<ProductBatch>> observeBatches(String productId) {
        MediatorLiveData<List<ProductBatch>> result = new MediatorLiveData<>();
        result.addSource(dao.observeBatches(productId), entities ->
                result.setValue(CatalogCacheMapper.batches(entities)));
        LiveData<List<ProductBatch>> remote = new LiveData<List<ProductBatch>>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = firestore.collection(BATCHES).whereEqualTo("productId", productId)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            List<ProductBatch> values = new ArrayList<>();
                            List<ProductBatchCacheEntity> entities = new ArrayList<>();
                            long now = System.currentTimeMillis();
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
                                ProductBatch batch = mapBatch(document);
                                if (batch != null) {
                                    values.add(batch);
                                    entities.add(CatalogCacheMapper.toEntity(batch, now));
                                }
                            }
                            postValue(values);
                            cacheExecutor.execute(() -> database.runInTransaction(() -> {
                                dao.clearBatches(productId);
                                dao.replaceBatches(entities);
                            }));
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
        result.addSource(remote, result::setValue);
        return result;
    }

    @Override public void refreshCatalog(ActionCallback callback) {
        if (!online(callback, "Đang offline; catalog tiếp tục dùng Room cache.")) return;
        firestore.collection(CATEGORIES).whereEqualTo("active", true).get()
                .addOnSuccessListener(categorySnapshot -> firestore.collection(PRODUCTS)
                        .whereEqualTo("status", ProductStatus.ACTIVE.name()).get()
                        .addOnSuccessListener(productSnapshot -> {
                            List<CategoryCacheEntity> categories = new ArrayList<>();
                            List<ProductCacheEntity> products = new ArrayList<>();
                            long now = System.currentTimeMillis();
                            for (DocumentSnapshot document : categorySnapshot.getDocuments()) {
                                Category value = mapCategory(document);
                                if (value != null) categories.add(CatalogCacheMapper.toEntity(value, now));
                            }
                            for (DocumentSnapshot document : productSnapshot.getDocuments()) {
                                Product value = mapProduct(document);
                                if (value != null) products.add(CatalogCacheMapper.toEntity(value, now));
                            }
                            cacheExecutor.execute(() -> {
                                database.runInTransaction(() -> {
                                    dao.clearCategories(); dao.replaceCategories(categories);
                                    dao.clearActiveProducts(); dao.replaceProducts(products);
                                });
                                callback.onSuccess();
                            });
                        }).addOnFailureListener(error -> notifyFailure(error, callback::onError)))
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void refreshSellerProducts(String sellerId, ActionCallback callback) {
        if (!online(callback, "Đang offline; sản phẩm seller tiếp tục dùng Room cache.")) return;
        firestore.collection(PRODUCTS).whereEqualTo("sellerId", sellerId).get()
                .addOnSuccessListener(snapshot -> {
                    List<ProductCacheEntity> products = new ArrayList<>(); long now = System.currentTimeMillis();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Product value = mapProduct(document);
                        if (value != null) products.add(CatalogCacheMapper.toEntity(value, now));
                    }
                    cacheExecutor.execute(() -> {
                        database.runInTransaction(() -> {
                            dao.clearSellerProducts(sellerId); dao.replaceProducts(products);
                        });
                        callback.onSuccess();
                    });
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void refreshSellerProductPage(String sellerId, boolean reset, ActionCallback callback) {
        if (!online(callback, "Đang offline; sản phẩm seller tiếp tục dùng Room cache.")) return;
        Query query = firestore.collection(PRODUCTS).whereEqualTo("sellerId", sellerId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(SELLER_PAGE_SIZE);
        DocumentSnapshot cursor;
        synchronized (sellerProductCursors) { cursor = sellerProductCursors.get(sellerId); }
        if (!reset && cursor != null) query = query.startAfter(cursor);
        query.get().addOnSuccessListener(snapshot -> {
            List<ProductCacheEntity> values = new ArrayList<>(); long now = System.currentTimeMillis();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Product product = mapProduct(document);
                if (product != null) values.add(CatalogCacheMapper.toEntity(product, now));
            }
            synchronized (sellerProductCursors) {
                if (reset) sellerProductCursors.remove(sellerId);
                if (!snapshot.isEmpty()) sellerProductCursors.put(sellerId,
                        snapshot.getDocuments().get(snapshot.size() - 1));
                if (snapshot.size() == SELLER_PAGE_SIZE) sellersWithMoreProducts.add(sellerId);
                else sellersWithMoreProducts.remove(sellerId);
            }
            cacheExecutor.execute(() -> {
                database.runInTransaction(() -> {
                    if (reset) dao.clearSellerProducts(sellerId);
                    dao.replaceProducts(values);
                });
                callback.onSuccess();
            });
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public boolean hasMoreSellerProducts(String sellerId) {
        synchronized (sellerProductCursors) { return sellersWithMoreProducts.contains(sellerId); }
    }

    @Override public void refreshBatches(String productId, ActionCallback callback) {
        if (!online(callback, "Đang offline; batch tiếp tục dùng Room cache.")) return;
        firestore.collection(BATCHES).whereEqualTo("productId", productId).get()
                .addOnSuccessListener(snapshot -> {
                    List<ProductBatchCacheEntity> batches = new ArrayList<>(); long now = System.currentTimeMillis();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        ProductBatch value = mapBatch(document);
                        if (value != null) batches.add(CatalogCacheMapper.toEntity(value, now));
                    }
                    cacheExecutor.execute(() -> {
                        database.runInTransaction(() -> { dao.clearBatches(productId); dao.replaceBatches(batches); });
                        callback.onSuccess();
                    });
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void getProduct(String productId, ProductCallback callback) {
        firestore.collection(PRODUCTS).document(productId).get().addOnSuccessListener(snapshot -> {
            Product value = snapshot.exists() ? mapProduct(snapshot) : null;
            if (value == null) { fallbackProduct(productId, callback); return; }
            cacheExecutor.execute(() -> dao.replaceProduct(CatalogCacheMapper.toEntity(
                    value, System.currentTimeMillis())));
            callback.onSuccess(value);
        }).addOnFailureListener(error -> cacheExecutor.execute(() -> {
            ProductCacheEntity cached = dao.findProduct(productId);
            if (cached != null) callback.onSuccess(CatalogCacheMapper.toDomain(cached));
            else notifyFailure(error, callback::onError);
        }));
    }

    @Override public void getCampaignProducts(List<String> batchIds, ProductListCallback callback) {
        if (batchIds == null || batchIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        List<Task<DocumentSnapshot>> batchReads = new ArrayList<>();
        for (String batchId : batchIds) if (batchId != null && !batchId.trim().isEmpty()) {
            batchReads.add(firestore.collection(BATCHES).document(batchId).get());
        }
        if (batchReads.isEmpty()) { callback.onSuccess(new ArrayList<>()); return; }
        Tasks.whenAllSuccess(batchReads).addOnSuccessListener(batchResults -> {
            Set<String> productIds = new LinkedHashSet<>();
            for (Object result : batchResults) if (result instanceof DocumentSnapshot) {
                String productId = ((DocumentSnapshot) result).getString("productId");
                if (productId != null && !productId.trim().isEmpty()) productIds.add(productId);
            }
            List<Task<DocumentSnapshot>> productReads = new ArrayList<>();
            for (String productId : productIds) productReads.add(firestore.collection(PRODUCTS)
                    .document(productId).get());
            if (productReads.isEmpty()) { callback.onSuccess(new ArrayList<>()); return; }
            Tasks.whenAllSuccess(productReads).addOnSuccessListener(productResults -> {
                List<Product> products = new ArrayList<>();
                for (Object result : productResults) if (result instanceof DocumentSnapshot) {
                    DocumentSnapshot snapshot = (DocumentSnapshot) result;
                    Product product = snapshot.exists() ? mapProduct(snapshot) : null;
                    if (product != null && product.isAvailable()) products.add(product);
                }
                callback.onSuccess(products);
            }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void getBatchesByIds(List<String> batchIds, BatchListCallback callback) {
        if (batchIds == null || batchIds.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String id : batchIds) if (id != null && !id.trim().isEmpty()) {
            reads.add(firestore.collection(BATCHES).document(id.trim()).get());
        }
        if (reads.isEmpty()) { callback.onSuccess(new ArrayList<>()); return; }
        Tasks.whenAllSuccess(reads).addOnSuccessListener(results -> {
            List<ProductBatch> batches = new ArrayList<>();
            for (Object result : results) if (result instanceof DocumentSnapshot) {
                DocumentSnapshot snapshot = (DocumentSnapshot) result;
                if (!snapshot.exists()) continue;
                try { batches.add(mapBatch(snapshot)); } catch (RuntimeException ignored) { }
            }
            callback.onSuccess(batches);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void saveProduct(Product product, ProductCallback callback) {
        if (!online(callback, "Không thể lưu sản phẩm khi offline; thay đổi không được xếp hàng.")) return;
        String sellerId = currentUserId();
        if (sellerId == null) { callback.onError(ErrorCode.FORBIDDEN, "Vui lòng đăng nhập seller."); return; }
        DocumentReference reference = clean(product.getId()).isEmpty()
                ? firestore.collection(PRODUCTS).document()
                : firestore.collection(PRODUCTS).document(product.getId());
        Product saved;
        try { saved = copyProduct(reference.getId(), sellerId, product); }
        catch (IllegalArgumentException error) {
            callback.onError(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        List<String> previousImageUrls = new ArrayList<>();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(reference);
            if (existing.exists() && !java.util.Objects.equals(existing.getString("unit"), saved.getUnit()))
                throw new IllegalArgumentException("Không đổi đơn vị của sản phẩm đã tạo; hãy tạo sản phẩm mới để giữ đúng số liệu lịch sử.");
            previousImageUrls.clear();
            if (existing.exists()) {
                Object oldImages = existing.get("imageUrls");
                if (oldImages instanceof List<?>) for (Object value : (List<?>) oldImages)
                    if (value instanceof String) previousImageUrls.add((String) value);
            }
            if (existing.exists() && !sellerId.equals(existing.getString("sellerId"))) {
                throw new IllegalStateException("FORBIDDEN");
            }
            double rating = existing.exists() ? number(existing, "averageRating") : 0D;
            int reviewCount = existing.exists() ? (int) longValue(existing, "reviewCount") : 0;
            Product result = Product.restore(saved.getId(), saved.getSellerId(), saved.getCategoryId(),
                    saved.getName(), saved.getDescription(), saved.getOriginalPrice(), saved.getRescuePrice(),
                    saved.getUnit(), saved.getOrigin(), saved.getProvince(), saved.getImageUrls(), rating,
                    reviewCount, saved.getStatus());
            Map<String, Object> data = productMap(result);
            if(existing.exists()) {
                if(existing.contains("ratingSum"))data.put("ratingSum",existing.get("ratingSum"));
                if(existing.contains("lastReviewId"))data.put("lastReviewId",existing.get("lastReviewId"));
            }
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!existing.exists()) data.put("createdAt", FieldValue.serverTimestamp());
            else if (existing.get("createdAt") != null) data.put("createdAt", existing.get("createdAt"));
            transaction.set(reference, data);
            return result;
        }).addOnSuccessListener(result -> {
            com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(
                    previousImageUrls, result.getImageUrls());
            cacheExecutor.execute(() -> dao.replaceProduct(CatalogCacheMapper.toEntity(
                    result, System.currentTimeMillis())));
            callback.onSuccess(result);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void createProduct(Product product, ProductCallback callback) {
        if (!online(callback, "Không thể lưu sản phẩm khi offline; thay đổi không được xếp hàng.")) return;
        String sellerId = currentUserId();
        if (sellerId == null) { callback.onError(ErrorCode.FORBIDDEN, "Vui lòng đăng nhập seller."); return; }
        DocumentReference reference = firestore.collection(PRODUCTS).document(clean(product.getId()));
        Product saved;
        try { saved = copyProduct(reference.getId(), sellerId, product); }
        catch (IllegalArgumentException error) {
            callback.onError(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        Map<String, Object> data = productMap(saved);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        reference.set(data).addOnSuccessListener(unused -> {
            cacheExecutor.execute(() -> dao.replaceProduct(CatalogCacheMapper.toEntity(
                    saved, System.currentTimeMillis())));
            callback.onSuccess(saved);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void hideProduct(String productId, ActionCallback callback) {
        if (!online(callback, "Không thể ẩn sản phẩm khi offline; thay đổi không được xếp hàng.")) return;
        DocumentReference reference = firestore.collection(PRODUCTS).document(productId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reference);
            requireOwner(snapshot);
            transaction.update(reference, "status", ProductStatus.HIDDEN.name(),
                    "updatedAt", FieldValue.serverTimestamp());
            return null;
        }).addOnSuccessListener(unused -> {
            cacheExecutor.execute(() -> dao.deleteProduct(productId)); callback.onSuccess();
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void saveBatch(ProductBatch batch, long expectedVersion, BatchCallback callback) {
        if (!online(callback, "Inventory mutation cần kết nối mạng; batch không được xếp hàng offline.")) return;
        DocumentReference productRef = firestore.collection(PRODUCTS).document(batch.getProductId());
        DocumentReference batchRef = clean(batch.getId()).isEmpty()
                ? firestore.collection(BATCHES).document()
                : firestore.collection(BATCHES).document(batch.getId());
        if (clean(batch.getId()).isEmpty()) {
            ProductBatch saved = ProductBatch.restore(batchRef.getId(), batch.getProductId(), null,
                    batch.getHarvestDate(), batch.getExpiryDate(), batch.getInitialQuantity(),
                    batch.getInitialQuantity(), 0D, 0D, batch.getStatus(), 1L);
            saved.updateDetails(batch.getHarvestDate(), batch.getExpiryDate(),
                    batch.getInitialQuantity(), new Date());
            Map<String, Object> data = batchMap(saved);
            data.put("updatedAt", FieldValue.serverTimestamp());
            data.put("createdAt", FieldValue.serverTimestamp());
            batchRef.set(data).addOnSuccessListener(unused -> {
                cacheExecutor.execute(() -> dao.replaceBatch(CatalogCacheMapper.toEntity(
                        saved, System.currentTimeMillis())));
                callback.onSuccess(saved);
            }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
            return;
        }
        firestore.runTransaction(transaction -> {
            DocumentSnapshot productSnapshot = transaction.get(productRef);
            DocumentSnapshot existing = transaction.get(batchRef);
            requireOwner(productSnapshot);
            ProductBatch saved;
            if (existing.exists()) {
                long version = longValue(existing, "inventoryVersion");
                InventoryVersionPolicy.requireExpected(version, expectedVersion);
                ProductBatch current = requireBatch(existing);
                current.updateDetails(batch.getHarvestDate(), batch.getExpiryDate(),
                        batch.getInitialQuantity(), new Date());
                saved = copyBatch(current, version + 1L);
            } else {
                InventoryVersionPolicy.requireExpected(0L, expectedVersion);
                ProductBatch candidate = ProductBatch.restore(batchRef.getId(), batch.getProductId(), null,
                        batch.getHarvestDate(), batch.getExpiryDate(), batch.getInitialQuantity(),
                        batch.getAvailableQuantity(), 0D, 0D, batch.getStatus(), 1L);
                candidate.updateDetails(candidate.getHarvestDate(), candidate.getExpiryDate(),
                        candidate.getInitialQuantity(), new Date());
                saved = copyBatch(candidate, 1L);
            }
            Map<String, Object> data = batchMap(saved);
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!existing.exists()) data.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(batchRef, data); return saved;
        }).addOnSuccessListener(saved -> {
            cacheExecutor.execute(() -> dao.replaceBatch(CatalogCacheMapper.toEntity(
                    saved, System.currentTimeMillis()))); callback.onSuccess(saved);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void deleteBatch(String batchId, long expectedVersion, ActionCallback callback) {
        if (!online(callback, "Inventory mutation cần kết nối mạng; batch không được xếp hàng offline.")) return;
        DocumentReference batchRef = firestore.collection(BATCHES).document(batchId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot batchSnapshot = transaction.get(batchRef);
            if (!batchSnapshot.exists()) throw new IllegalStateException("NOT_FOUND");
            DocumentSnapshot productSnapshot = transaction.get(firestore.collection(PRODUCTS)
                    .document(batchSnapshot.getString("productId")));
            requireOwner(productSnapshot);
            InventoryVersionPolicy.requireExpected(
                    longValue(batchSnapshot, "inventoryVersion"), expectedVersion);
            if (number(batchSnapshot, "reservedQuantity") > 0 || number(batchSnapshot, "soldQuantity") > 0
                    || !clean(batchSnapshot.getString("activeCampaignId")).isEmpty()) {
                throw new IllegalStateException("BATCH_IN_USE");
            }
            transaction.update(batchRef, "status", BatchStatus.ARCHIVED.name(),
                    "inventoryVersion", expectedVersion + 1L, "updatedAt", FieldValue.serverTimestamp());
            return null;
        }).addOnSuccessListener(unused -> {
            refreshBatchesForArchived(batchId, callback);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    private void refreshBatchesForArchived(String batchId, ActionCallback callback) {
        firestore.collection(BATCHES).document(batchId).get().addOnSuccessListener(doc -> {
            ProductBatch archived = mapBatch(doc);
            if (archived != null) cacheExecutor.execute(() -> {
                dao.replaceBatch(CatalogCacheMapper.toEntity(archived, System.currentTimeMillis()));
            });
            callback.onSuccess();
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void mutateStock(String batchId, long expectedVersion, StockMutation mutation,
            double quantity, BatchCallback callback) {
        if (!online(callback, "Inventory mutation cần kết nối mạng và không được xếp hàng offline.")) return;
        DocumentReference batchRef = firestore.collection(BATCHES).document(batchId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot batchSnapshot = transaction.get(batchRef);
            if (!batchSnapshot.exists()) throw new IllegalStateException("NOT_FOUND");
            DocumentSnapshot productSnapshot = transaction.get(firestore.collection(PRODUCTS)
                    .document(batchSnapshot.getString("productId")));
            requireOwner(productSnapshot);
            long version = longValue(batchSnapshot, "inventoryVersion");
            InventoryVersionPolicy.requireExpected(version, expectedVersion);
            ProductBatch current = requireBatch(batchSnapshot);
            if (mutation == StockMutation.RESERVE) inventoryService.reserveStock(current, quantity, new Date());
            else if (mutation == StockMutation.COMMIT) inventoryService.commitReservedStock(current, quantity);
            else inventoryService.releaseReservedStock(current, quantity);
            ProductBatch saved = copyBatch(current, version + 1L);
            Map<String, Object> values = batchMap(saved); values.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(batchRef, values); return saved;
        }).addOnSuccessListener(saved -> {
            cacheExecutor.execute(() -> dao.replaceBatch(CatalogCacheMapper.toEntity(
                    saved, System.currentTimeMillis()))); callback.onSuccess(saved);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void getPromotion(String productId, PromotionCallback callback) {
        firestore.collection(PROMOTIONS).document(productId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) { callback.onSuccess(null); return; }
                    Promotion value = mapPromotion(snapshot);
                    if (value == null) callback.onError(ErrorCode.VALIDATION, "Dữ liệu khuyến mãi không hợp lệ.");
                    else callback.onSuccess(value);
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public LiveData<Promotion> observePromotion(String productId) {
        return new LiveData<Promotion>() {
            private ListenerRegistration registration;
            private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            private final Runnable retry = this::listen;
            private void listen() {
                if (!hasActiveObservers()) return;
                if (registration != null) registration.remove();
                registration = firestore.collection(PROMOTIONS).document(productId).addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        postValue(null); handler.removeCallbacks(retry); handler.postDelayed(retry, 60000L); return;
                    }
                    handler.removeCallbacks(retry); postValue(snapshot.exists() ? mapPromotion(snapshot) : null);
                });
            }
            @Override protected void onActive() { listen(); }
            @Override protected void onInactive() { handler.removeCallbacks(retry); if (registration != null) { registration.remove(); registration = null; } }
        };
    }

    @Override public void getSellerPromotions(String sellerId, PromotionListCallback callback) {
        firestore.collection(PROMOTIONS).whereEqualTo("sellerId", sellerId).get()
                .addOnSuccessListener(snapshot -> {
                    List<Promotion> values = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Promotion value = mapPromotion(document);
                        if (value != null) values.add(value);
                    }
                    callback.onSuccess(values);
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void savePromotion(Promotion promotion, PromotionCallback callback) {
        if (!online(callback, "Không thể lưu promotion khi offline; thay đổi không được xếp hàng.")) return;
        String sellerId = currentUserId();
        if (sellerId == null) { callback.onError(ErrorCode.FORBIDDEN, "Vui lòng đăng nhập seller."); return; }
        Promotion candidate;
        try {
            candidate = new Promotion(promotion.getProductId(), sellerId, promotion.getProductId(),
                    promotion.getType(), promotion.getValue(), promotion.getQuantityDiscountTiers(),
                    promotion.getStartDate(), promotion.getEndDate(), promotion.isActive());
        } catch (IllegalArgumentException error) {
            callback.onError(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        DocumentReference productRef = firestore.collection(PRODUCTS).document(candidate.getProductId());
        DocumentReference promotionRef = firestore.collection(PROMOTIONS).document(candidate.getProductId());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot product = transaction.get(productRef);
            DocumentSnapshot existing = transaction.get(promotionRef);
            requireOwner(product);
            if (existing.exists() && !sellerId.equals(existing.getString("sellerId"))) {
                throw new IllegalStateException("FORBIDDEN");
            }
            Map<String, Object> data = promotionMap(candidate);
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!existing.exists()) data.put("createdAt", FieldValue.serverTimestamp());
            else if (existing.get("createdAt") != null) data.put("createdAt", existing.get("createdAt"));
            transaction.set(promotionRef, data);
            if (candidate.isActive()) com.rescuefarm.service.notification.NotificationOutbox.enqueue(transaction, firestore, "PROMOTION", "PROMOTION", candidate.getProductId());
            return candidate;
        }).addOnSuccessListener(saved -> { callback.onSuccess(saved); })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void setPromotionActive(String productId, boolean active, ActionCallback callback) {
        if (!online(callback, "Không thể đổi promotion khi offline; thay đổi không được xếp hàng.")) return;
        DocumentReference promotionRef = firestore.collection(PROMOTIONS).document(productId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot promotion = transaction.get(promotionRef);
            if (!promotion.exists()) throw new IllegalStateException("NOT_FOUND");
            DocumentSnapshot product = transaction.get(firestore.collection(PRODUCTS).document(productId));
            requireOwner(product);
            transaction.update(promotionRef, "active", active, "updatedAt", FieldValue.serverTimestamp());
            if (active) com.rescuefarm.service.notification.NotificationOutbox.enqueue(transaction, firestore, "PROMOTION", "PROMOTION", productId);
            return null;
        }).addOnSuccessListener(unused -> { callback.onSuccess(); })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void getCartQuote(String productId, String batchId, CartQuoteCallback callback) {
        if (!online(callback, "Cần kết nối mạng để revalidate giá và tồn kho trước checkout.")) return;
        firestore.collection(PRODUCTS).document(productId).get().addOnSuccessListener(productSnapshot -> {
            Product product = productSnapshot.exists() ? mapProduct(productSnapshot) : null;
            if (product == null) { callback.onError(ErrorCode.NOT_FOUND, "Sản phẩm không còn khả dụng."); return; }
            firestore.collection(BATCHES).document(batchId).get().addOnSuccessListener(batchSnapshot -> {
                ProductBatch batch = batchSnapshot.exists() ? mapBatch(batchSnapshot) : null;
                if (batch == null || !productId.equals(batch.getProductId())) {
                    callback.onError(ErrorCode.NOT_FOUND, "Batch không còn khả dụng."); return;
                }
                firestore.collection(PROMOTIONS).document(productId).get()
                        .addOnSuccessListener(promotionSnapshot -> callback.onSuccess(product, batch,
                                promotionSnapshot.exists() ? mapPromotion(promotionSnapshot) : null))
                        .addOnFailureListener(error -> {
                            if (error instanceof FirebaseFirestoreException
                                    && ((FirebaseFirestoreException) error).getCode()
                                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                callback.onSuccess(product, batch, null);
                            } else notifyFailure(error, callback::onError);
                        });
            }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    private void fallbackProduct(String productId, ProductCallback callback) {
        cacheExecutor.execute(() -> {
            ProductCacheEntity cached = dao.findProduct(productId);
            if (cached == null) callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm.");
            else callback.onSuccess(CatalogCacheMapper.toDomain(cached));
        });
    }

    private Category mapCategory(DocumentSnapshot value) {
        try { return new Category(value.getId(), value.getString("name"), value.getString("imageUrl"),
                Boolean.TRUE.equals(value.getBoolean("active")), (int) longValue(value, "displayOrder")); }
        catch (IllegalArgumentException error) { return null; }
    }

    private Product mapProduct(DocumentSnapshot value) {
        try {
            List<String> urls = new ArrayList<>(); Object rawUrls = value.get("imageUrls");
            if (rawUrls instanceof List<?>) for (Object item : (List<?>) rawUrls) if (item instanceof String) urls.add((String) item);
            return Product.restore(value.getId(), value.getString("sellerId"), value.getString("categoryId"),
                    value.getString("name"), value.getString("description"), number(value, "originalPrice"),
                    number(value, "rescuePrice"), value.getString("unit"), value.getString("origin"),
                    value.getString("province"), urls, number(value, "averageRating"),
                    (int) longValue(value, "reviewCount"), enumValue(ProductStatus.class,
                    value.getString("status"), ProductStatus.INACTIVE));
        } catch (IllegalArgumentException error) { return null; }
    }

    private ProductBatch mapBatch(DocumentSnapshot value) {
        try { return requireBatch(value); }
        catch (IllegalArgumentException | IllegalStateException error) { return null; }
    }

    private Promotion mapPromotion(DocumentSnapshot value) {
        try {
            Map<String, Double> tiers = new HashMap<>();
            Object raw = value.get("quantityDiscountTiers");
            if (raw instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                        tiers.put((String) entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }
            return Promotion.restore(value.getId(), value.getString("sellerId"),
                    value.getString("productId"), enumValue(PromotionType.class,
                    value.getString("type"), null), number(value, "value"), tiers,
                    date(value, "startDate"), date(value, "endDate"),
                    Boolean.TRUE.equals(value.getBoolean("active")));
        } catch (IllegalArgumentException error) { return null; }
    }

    private ProductBatch requireBatch(DocumentSnapshot value) {
        Date expiry = date(value, "expiryDate");
        BatchStatus status = enumValue(BatchStatus.class, value.getString("status"), BatchStatus.AVAILABLE);
        if (expiry != null && !expiry.after(new Date())) status = BatchStatus.EXPIRED;
        return ProductBatch.restore(value.getId(), value.getString("productId"),
                value.getString("activeCampaignId"), date(value, "harvestDate"), expiry,
                number(value, "initialQuantity"), number(value, "availableQuantity"),
                number(value, "reservedQuantity"), number(value, "soldQuantity"), status,
                longValue(value, "inventoryVersion"));
    }

    private Product copyProduct(String id, String sellerId, Product source) {
        return new Product(id, sellerId, source.getCategoryId(), source.getName(), source.getDescription(),
                source.getOriginalPrice(), source.getRescuePrice(), source.getUnit(), source.getOrigin(),
                source.getProvince(), source.getImageUrls(), source.getStatus());
    }
    private ProductBatch copyBatch(ProductBatch source, long version) {
        return ProductBatch.restore(source.getId(), source.getProductId(), source.getActiveCampaignId(),
                source.getHarvestDate(), source.getExpiryDate(), source.getInitialQuantity(),
                source.getAvailableQuantity(), source.getReservedQuantity(), source.getSoldQuantity(),
                source.getStatus(), version);
    }

    private Map<String, Object> productMap(Product value) {
        Map<String, Object> data = new HashMap<>(); data.put("id", value.getId());
        data.put("sellerId", value.getSellerId()); data.put("categoryId", value.getCategoryId());
        data.put("name", value.getName()); data.put("description", value.getDescription());
        data.put("originalPrice", value.getOriginalPrice()); data.put("rescuePrice", value.getRescuePrice());
        data.put("unit", value.getUnit()); data.put("origin", value.getOrigin()); data.put("province", value.getProvince());
        data.put("imageUrls", value.getImageUrls()); data.put("averageRating", value.getAverageRating());
        data.put("reviewCount", value.getReviewCount()); data.put("status", value.getStatus().name()); return data;
    }
    private Map<String, Object> batchMap(ProductBatch value) {
        Map<String, Object> data = new HashMap<>(); data.put("id", value.getId());
        data.put("productId", value.getProductId()); data.put("activeCampaignId", value.getActiveCampaignId());
        data.put("harvestDate", value.getHarvestDate()); data.put("expiryDate", value.getExpiryDate());
        data.put("initialQuantity", value.getInitialQuantity()); data.put("availableQuantity", value.getAvailableQuantity());
        data.put("reservedQuantity", value.getReservedQuantity()); data.put("soldQuantity", value.getSoldQuantity());
        data.put("status", value.getStatus().name()); data.put("inventoryVersion", value.getInventoryVersion()); return data;
    }
    private Map<String, Object> promotionMap(Promotion value) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", value.getProductId()); data.put("sellerId", value.getSellerId());
        data.put("productId", value.getProductId()); data.put("type", value.getType().name());
        data.put("value", value.getValue());
        data.put("quantityDiscountTiers", value.getQuantityDiscountTiers());
        data.put("startDate", value.getStartDate()); data.put("endDate", value.getEndDate());
        data.put("active", value.isActive()); return data;
    }

    private void requireOwner(DocumentSnapshot snapshot) {
        String userId = currentUserId();
        if (!snapshot.exists()) throw new IllegalStateException("NOT_FOUND");
        if (userId == null || !userId.equals(snapshot.getString("sellerId"))) {
            throw new IllegalStateException("FORBIDDEN");
        }
    }
    private String currentUserId() { return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid(); }

    private void notifyFailure(Exception error, ErrorConsumer consumer) {
        String code = error.getMessage();
        if ("STALE_VERSION".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Tồn kho đã thay đổi. Hãy tải lại."); return; }
        if ("BATCH_IN_USE".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Batch đã phát sinh giữ chỗ/bán hoặc campaign."); return; }
        if ("NOT_FOUND".equals(code)) { consumer.accept(ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu."); return; }
        if ("FORBIDDEN".equals(code)) { consumer.accept(ErrorCode.FORBIDDEN, "Seller chưa được duyệt hoặc không sở hữu dữ liệu."); return; }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            consumer.accept(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code firestoreCode = ((FirebaseFirestoreException) error).getCode();
            if (firestoreCode == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                consumer.accept(ErrorCode.FORBIDDEN, "Seller chưa được duyệt hoặc không có quyền."); return;
            }
            if (firestoreCode == FirebaseFirestoreException.Code.UNAVAILABLE) {
                consumer.accept(ErrorCode.NETWORK, "Không thể kết nối Firestore; dữ liệu cache vẫn được giữ."); return;
            }
            if (firestoreCode == FirebaseFirestoreException.Code.ABORTED) {
                consumer.accept(ErrorCode.CONFLICT, "Dữ liệu vừa thay đổi. Hãy tải lại."); return;
            }
        }
        consumer.accept(ErrorCode.UNKNOWN, "Không thể xử lý dữ liệu sản phẩm.");
    }
    private static double number(DocumentSnapshot value, String field) {
        Double result = value.getDouble(field); return result == null ? 0D : result;
    }
    private static long longValue(DocumentSnapshot value, String field) {
        Long result = value.getLong(field); return result == null ? 0L : result;
    }
    private static Date date(DocumentSnapshot value, String field) {
        Timestamp result = value.getTimestamp(field); return result == null ? null : result.toDate();
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value == null ? "" : value); }
        catch (IllegalArgumentException error) { return fallback; }
    }
    private interface ErrorConsumer { void accept(ErrorCode error, String message); }
    @Override public void close() { cacheExecutor.shutdownNow(); }

    private boolean online(Object callback, String message) {
        if (network.isOnline()) return true;
        if (callback instanceof ActionCallback) ((ActionCallback) callback).onError(ErrorCode.NETWORK, message);
        else if (callback instanceof ProductCallback) ((ProductCallback) callback).onError(ErrorCode.NETWORK, message);
        else if (callback instanceof BatchCallback) ((BatchCallback) callback).onError(ErrorCode.NETWORK, message);
        else if (callback instanceof PromotionCallback) ((PromotionCallback) callback).onError(ErrorCode.NETWORK, message);
        else if (callback instanceof CartQuoteCallback) ((CartQuoteCallback) callback).onError(ErrorCode.NETWORK, message);
        return false;
    }
}
