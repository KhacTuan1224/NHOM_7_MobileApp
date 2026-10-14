package com.rescuefarm.data.remote.firebase;

import androidx.lifecycle.LiveData;
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
import com.rescuefarm.data.local.dao.CampaignCacheDao;
import com.rescuefarm.data.local.dao.BannerCacheDao;
import com.rescuefarm.data.local.dao.CatalogCacheDao;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.CampaignCacheEntity;
import com.rescuefarm.data.local.mapper.CampaignCacheMapper;
import com.rescuefarm.data.local.mapper.BannerCacheMapper;
import com.rescuefarm.data.local.mapper.CatalogCacheMapper;
import com.rescuefarm.data.repository.CampaignRepository;
import com.rescuefarm.domain.enums.BatchStatus;
import com.rescuefarm.domain.enums.CampaignStatus;
import com.rescuefarm.domain.enums.RescueMode;
import com.rescuefarm.domain.enums.RescueReason;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.ProductBatch;
import com.rescuefarm.domain.model.RescueCampaign;
import com.rescuefarm.domain.model.Banner;
import com.rescuefarm.service.inventory.InventoryService;
import com.rescuefarm.service.inventory.InventoryVersionPolicy;
import com.rescuefarm.service.network.NetworkStatusProvider;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class FirebaseCampaignRepository implements CampaignRepository {
    private static final String CAMPAIGNS = "campaigns";
    private static final String BANNERS = "banners";
    private static final String BATCHES = "productBatches";
    // Leave headroom under Firestore's 500-write transaction limit (campaign + two records/batch).
    private static final int MAX_BATCHES = 200;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final RescueFarmDatabase database;
    private final CampaignCacheDao campaignDao;
    private final BannerCacheDao bannerDao;
    private final CatalogCacheDao catalogDao;
    private final ExecutorService cacheExecutor;
    private final NetworkStatusProvider network;
    private final Map<String, DocumentSnapshot> sellerCampaignCursors = new HashMap<>();
    private final Set<String> sellersWithMoreCampaigns = new HashSet<>();
    private final InventoryService inventoryService = new InventoryService();

    public FirebaseCampaignRepository(RescueFarmDatabase database, ExecutorService cacheExecutor,
            NetworkStatusProvider network) {
        this.database = database; this.campaignDao = database.campaignCacheDao();
        this.bannerDao = database.bannerCacheDao();
        this.catalogDao = database.catalogCacheDao(); this.cacheExecutor = cacheExecutor;
        this.network = network;
    }

    @Override public LiveData<List<RescueCampaign>> observeActiveCampaigns() {
        return Transformations.map(campaignDao.observeActiveCampaigns(), CampaignCacheMapper::campaigns);
    }
    @Override public LiveData<List<RescueCampaign>> observeSellerCampaigns(String sellerId) {
        return Transformations.map(campaignDao.observeSellerCampaigns(sellerId), CampaignCacheMapper::campaigns);
    }
    @Override public LiveData<RescueCampaign> observeCampaign(String campaignId) {
        return new LiveData<RescueCampaign>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = firestore.collection(CAMPAIGNS).document(campaignId)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            RescueCampaign campaign = snapshot.exists() ? mapCampaign(snapshot) : null;
                            postValue(campaign);
                            if (campaign != null) cache(campaign);
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }
    @Override public LiveData<List<Banner>> observeActiveBanners() {
        return Transformations.map(bannerDao.observeActiveBanners(), BannerCacheMapper::banners);
    }

    @Override public void refreshActiveCampaigns(ActionCallback callback) {
        if (!online(callback, "Đang offline; campaign tiếp tục dùng Room cache.")) return;
        firestore.collection(CAMPAIGNS).whereEqualTo("status", CampaignStatus.ACTIVE.name()).get()
                .addOnSuccessListener(snapshot -> cacheCampaigns(snapshot.getDocuments(), true, null, callback))
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void refreshBanners(ActionCallback callback) {
        if (!online(callback, "Đang offline; banner tiếp tục dùng Room cache.")) return;
        firestore.collection(BANNERS).whereEqualTo("active", true).get()
                .addOnSuccessListener(snapshot -> {
                    List<com.rescuefarm.data.local.entity.BannerCacheEntity> entities =
                            new ArrayList<>();
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Banner banner = mapBanner(document);
                        if (banner != null) entities.add(BannerCacheMapper.toEntity(banner, now));
                    }
                    cacheExecutor.execute(() -> {
                        try {
                            database.runInTransaction(() -> {
                                bannerDao.clearBanners();
                                bannerDao.replaceBanners(entities);
                            });
                            callback.onSuccess();
                        } catch (RuntimeException error) {
                            callback.onError(ErrorCode.UNKNOWN, "Không thể cập nhật cache banner.");
                        }
                    });
                })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void refreshSellerCampaigns(String sellerId, ActionCallback callback) {
        if (!online(callback, "Đang offline; campaign seller tiếp tục dùng Room cache.")) return;
        firestore.collection(CAMPAIGNS).whereEqualTo("sellerId", sellerId).get()
                .addOnSuccessListener(snapshot -> cacheCampaigns(snapshot.getDocuments(), false, sellerId, callback))
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void refreshSellerCampaignPage(String sellerId, boolean reset, ActionCallback callback) {
        if (!online(callback, "Đang offline; campaign seller tiếp tục dùng Room cache.")) return;
        Query query = firestore.collection(CAMPAIGNS).whereEqualTo("sellerId", sellerId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50);
        DocumentSnapshot cursor = sellerCampaignCursors.get(sellerId);
        if (!reset && cursor != null) query = query.startAfter(cursor);
        query.get().addOnSuccessListener(snapshot -> {
            List<CampaignCacheEntity> entities = new ArrayList<>(); long now = System.currentTimeMillis();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                RescueCampaign campaign = mapCampaign(document);
                if (campaign != null) entities.add(CampaignCacheMapper.toEntity(campaign, now));
            }
            if (reset) sellerCampaignCursors.remove(sellerId);
            if (!snapshot.isEmpty()) sellerCampaignCursors.put(sellerId,
                    snapshot.getDocuments().get(snapshot.size() - 1));
            if (snapshot.size() == 50) sellersWithMoreCampaigns.add(sellerId);
            else sellersWithMoreCampaigns.remove(sellerId);
            cacheExecutor.execute(() -> {
                database.runInTransaction(() -> {
                    if (reset) campaignDao.clearSellerCampaigns(sellerId);
                    campaignDao.replaceCampaigns(entities);
                });
                callback.onSuccess();
            });
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public boolean hasMoreSellerCampaigns(String sellerId) {
        return sellersWithMoreCampaigns.contains(sellerId);
    }

    private void cacheCampaigns(List<DocumentSnapshot> documents, boolean activeOnly,
            String sellerId, ActionCallback callback) {
        List<CampaignCacheEntity> entities = new ArrayList<>(); long now = System.currentTimeMillis();
        for (DocumentSnapshot document : documents) {
            RescueCampaign campaign = mapCampaign(document);
            if (campaign != null) entities.add(CampaignCacheMapper.toEntity(campaign, now));
        }
        cacheExecutor.execute(() -> {
            database.runInTransaction(() -> {
                if (activeOnly) campaignDao.clearActiveCampaigns();
                else campaignDao.clearSellerCampaigns(sellerId);
                campaignDao.replaceCampaigns(entities);
            });
            callback.onSuccess();
        });
    }

    @Override public void getCampaign(String campaignId, CampaignCallback callback) {
        firestore.collection(CAMPAIGNS).document(campaignId).get().addOnSuccessListener(snapshot -> {
            RescueCampaign value = snapshot.exists() ? mapCampaign(snapshot) : null;
            if (value == null) { fallback(campaignId, callback); return; }
            cache(value); callback.onSuccess(value);
        }).addOnFailureListener(error -> cacheExecutor.execute(() -> {
            CampaignCacheEntity cached = campaignDao.findCampaign(campaignId);
            if (cached != null) try { callback.onSuccess(CampaignCacheMapper.toDomain(cached)); }
            catch (IllegalArgumentException | IllegalStateException invalidCache) {
                notifyFailure(error, callback::onError);
            } else notifyFailure(error, callback::onError);
        }));
    }

    @Override public void saveCampaign(RescueCampaign source, boolean submit,
            CampaignCallback callback) {
        if (!online(callback, "Không thể lưu campaign khi offline; thay đổi không được xếp hàng.")) return;
        String sellerId = currentUserId();
        if (sellerId == null) { callback.onError(ErrorCode.FORBIDDEN, "Vui lòng đăng nhập seller."); return; }
        DocumentReference reference = clean(source.getId()).isEmpty()
                ? firestore.collection(CAMPAIGNS).document()
                : firestore.collection(CAMPAIGNS).document(source.getId());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(reference);
            if (existing.exists() && !sellerId.equals(existing.getString("sellerId"))) {
                throw new IllegalStateException("FORBIDDEN");
            }
            CampaignStatus editableStatus = existing.exists()
                    ? enumValue(CampaignStatus.class, existing.getString("status"), CampaignStatus.DRAFT)
                    : CampaignStatus.DRAFT;
            if (editableStatus != CampaignStatus.DRAFT && editableStatus != CampaignStatus.REJECTED) {
                throw new IllegalStateException("CAMPAIGN_LOCKED");
            }
            RescueCampaign previous = existing.exists() ? mapCampaign(existing) : null;
            if (existing.exists() && previous == null) throw new IllegalStateException("NOT_FOUND");
            CampaignStatus savedStatus = submit ? editableStatus : CampaignStatus.DRAFT;
            RescueCampaign saved = copyCampaign(reference.getId(), sellerId, source,
                    savedStatus, previous);
            if (saved.getBatchTargets().size() > MAX_BATCHES) {
                throw new IllegalArgumentException("Mỗi Campaign hỗ trợ tối đa 200 lô để an toàn với giới hạn ghi Firestore.");
            }
            Map<String, Double> previousTargets = existing.exists()
                    ? targetMap(existing.get("batchTargets")) : new LinkedHashMap<>();
            Set<String> ids = new HashSet<>();
            if (submit) { ids.addAll(previousTargets.keySet()); ids.addAll(saved.getBatchTargets().keySet()); }
            else if (editableStatus == CampaignStatus.REJECTED) ids.addAll(previousTargets.keySet());
            Map<String, DocumentSnapshot> batchSnapshots = new LinkedHashMap<>();
            Date earliestExpiry = null;
            for (String batchId : ids) {
                DocumentSnapshot batch = transaction.get(firestore.collection(BATCHES).document(batchId));
                if (!batch.exists()) throw new IllegalStateException("BATCH_NOT_FOUND");
                batchSnapshots.put(batchId, batch);
                if (submit && saved.getBatchTargets().containsKey(batchId)) {
                    String linked = clean(batch.getString("activeCampaignId"));
                    if (!linked.isEmpty() && !reference.getId().equals(linked)) {
                        throw new IllegalStateException("BATCH_IN_USE");
                    }
                    Date expiry = date(batch, "expiryDate");
                    if (expiry == null || !expiry.after(new Date())) throw new IllegalStateException("BATCH_EXPIRED");
                    if (number(batch, "availableQuantity") + 0.000001
                            < saved.getBatchTargets().get(batchId)) {
                        throw new IllegalStateException("INSUFFICIENT_BATCH_TARGET");
                    }
                    if (earliestExpiry == null || expiry.before(earliestExpiry)) earliestExpiry = expiry;
                }
            }
            if (submit) {
                if (!saved.getEndDate().after(new Date())) {
                    throw new IllegalArgumentException("Campaign end date must be in the future");
                }
                saved.calculateUrgency(new Date(), earliestExpiry);
                saved.submitForApproval();
            }
            Map<String, Object> data = campaignMap(saved);
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!existing.exists()) data.put("createdAt", FieldValue.serverTimestamp());
            else if (existing.get("createdAt") != null) data.put("createdAt", existing.get("createdAt"));
            transaction.set(reference, data);
            if (submit || editableStatus == CampaignStatus.REJECTED)
                    for (Map.Entry<String, DocumentSnapshot> entry : batchSnapshots.entrySet()) {
                boolean linked = submit && saved.getBatchTargets().containsKey(entry.getKey());
                transaction.update(entry.getValue().getReference(),
                        "activeCampaignId", linked ? reference.getId() : null,
                        "inventoryVersion", longValue(entry.getValue(), "inventoryVersion") + 1L,
                        "updatedAt", FieldValue.serverTimestamp());
            }
            return saved;
        }).addOnSuccessListener(saved -> { cache(saved); callback.onSuccess(saved); })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void updateMobileLocation(String campaignId, double latitude, double longitude,
            boolean sharingEnabled, CampaignCallback callback) {
        if (!online(callback, "Cập nhật điểm mobile cần kết nối mạng và không được xếp hàng offline.")) return;
        DocumentReference reference = firestore.collection(CAMPAIGNS).document(campaignId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(reference);
            RescueCampaign campaign = requireOwnedCampaign(snapshot);
            if (campaign.getStatus() != CampaignStatus.ACTIVE
                    || campaign.getRescueMode() != RescueMode.MOBILE_POINT) {
                throw new IllegalStateException("MOBILE_NOT_ACTIVE");
            }
            campaign.setLocationSharingEnabled(sharingEnabled);
            if (sharingEnabled) campaign.updateCurrentLocation(latitude, longitude, new Date());
            else campaign.clearCurrentLocation();
            Map<String, Object> updates = new HashMap<>();
            updates.put("locationSharingEnabled", sharingEnabled);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            if (sharingEnabled) {
                updates.put("currentLatitude", latitude); updates.put("currentLongitude", longitude);
                updates.put("locationUpdatedAt", FieldValue.serverTimestamp());
            } else {
                updates.put("currentLatitude", 0D); updates.put("currentLongitude", 0D);
                updates.put("locationUpdatedAt", null);
            }
            transaction.update(reference, updates); return campaign;
        }).addOnSuccessListener(campaign -> { cache(campaign); callback.onSuccess(campaign); })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void mutateReservation(String campaignId, String batchId,
            long expectedInventoryVersion, ReservationMutation mutation, double quantity,
            CampaignCallback callback) {
        if (!online(callback, "Reservation campaign cần kết nối mạng và không được xếp hàng offline.")) return;
        DocumentReference campaignRef = firestore.collection(CAMPAIGNS).document(campaignId);
        DocumentReference batchRef = firestore.collection(BATCHES).document(batchId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot campaignSnapshot = transaction.get(campaignRef);
            DocumentSnapshot batchSnapshot = transaction.get(batchRef);
            RescueCampaign campaign = requireOwnedCampaign(campaignSnapshot);
            if (campaign.getStatus() != CampaignStatus.ACTIVE
                    || !campaign.getBatchTargets().containsKey(batchId)) {
                throw new IllegalStateException("CAMPAIGN_NOT_ACTIVE");
            }
            if (!campaignId.equals(batchSnapshot.getString("activeCampaignId"))) {
                throw new IllegalStateException("BATCH_NOT_LINKED");
            }
            InventoryVersionPolicy.requireExpected(
                    longValue(batchSnapshot, "inventoryVersion"), expectedInventoryVersion);
            ProductBatch batch = requireBatch(batchSnapshot);
            if (mutation == ReservationMutation.RESERVE) {
                inventoryService.reserveStock(batch, campaign, quantity);
            } else if (mutation == ReservationMutation.COMMIT) {
                inventoryService.commitReservedStock(batch, campaign, quantity);
            } else {
                inventoryService.releaseReservedStock(batch, campaign, quantity);
            }
            ProductBatch savedBatch = copyBatch(batch,
                    longValue(batchSnapshot, "inventoryVersion") + 1L);
            Map<String, Object> campaignData = campaignMap(campaign);
            campaignData.put("updatedAt", FieldValue.serverTimestamp());
            if (campaignSnapshot.get("createdAt") != null) {
                campaignData.put("createdAt", campaignSnapshot.get("createdAt"));
            }
            Map<String, Object> batchData = batchMap(savedBatch);
            batchData.put("updatedAt", FieldValue.serverTimestamp());
            if (batchSnapshot.get("createdAt") != null) batchData.put("createdAt", batchSnapshot.get("createdAt"));
            transaction.set(campaignRef, campaignData); transaction.set(batchRef, batchData);
            return new MutationResult(campaign, savedBatch);
        }).addOnSuccessListener(result -> {
            cache(result.campaign);
            cacheExecutor.execute(() -> catalogDao.replaceBatch(CatalogCacheMapper.toEntity(
                    result.batch, System.currentTimeMillis())));
            callback.onSuccess(result.campaign);
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    private RescueCampaign requireOwnedCampaign(DocumentSnapshot snapshot) {
        RescueCampaign value = snapshot.exists() ? mapCampaign(snapshot) : null;
        if (value == null) throw new IllegalStateException("NOT_FOUND");
        if (currentUserId() == null || !currentUserId().equals(value.getSellerId())) {
            throw new IllegalStateException("FORBIDDEN");
        }
        return value;
    }

    private RescueCampaign mapCampaign(DocumentSnapshot value) {
        try {
            return RescueCampaign.restore(value.getId(), value.getString("sellerId"),
                    value.getString("title"), value.getString("description"),
                    enumValue(RescueReason.class, value.getString("rescueReason"), RescueReason.OVER_SUPPLY),
                    enumValue(UrgencyLevel.class, value.getString("urgencyLevel"), UrgencyLevel.NORMAL),
                    enumValue(RescueMode.class, value.getString("rescueMode"), RescueMode.FIXED_POINT),
                    targetMap(value.get("batchTargets")), number(value, "targetQuantity"),
                    number(value, "reservedQuantity"), number(value, "rescuedQuantity"),
                    date(value, "startDate"), date(value, "endDate"),
                    number(value, "latitude"), number(value, "longitude"),
                    number(value, "currentLatitude"), number(value, "currentLongitude"),
                    date(value, "locationUpdatedAt"), Boolean.TRUE.equals(value.getBoolean("locationSharingEnabled")),
                    value.getString("locationName"),
                    enumValue(CampaignStatus.class, value.getString("status"), CampaignStatus.DRAFT));
        } catch (IllegalArgumentException | IllegalStateException error) { return null; }
    }

    private Banner mapBanner(DocumentSnapshot value) {
        try {
            Long order = value.getLong("displayOrder");
            return Banner.restore(value.getId(), value.getString("title"),
                    value.getString("imageUrl"), value.getString("campaignId"),
                    order == null ? 0 : order.intValue(), date(value, "startDate"),
                    date(value, "endDate"), Boolean.TRUE.equals(value.getBoolean("active")));
        } catch (RuntimeException error) { return null; }
    }

    private ProductBatch requireBatch(DocumentSnapshot value) {
        if (!value.exists()) throw new IllegalStateException("BATCH_NOT_FOUND");
        Date expiry = date(value, "expiryDate");
        BatchStatus status = enumValue(BatchStatus.class, value.getString("status"), BatchStatus.AVAILABLE);
        if (expiry != null && !expiry.after(new Date())) status = BatchStatus.EXPIRED;
        return ProductBatch.restore(value.getId(), value.getString("productId"),
                value.getString("activeCampaignId"), date(value, "harvestDate"), expiry,
                number(value, "initialQuantity"), number(value, "availableQuantity"),
                number(value, "reservedQuantity"), number(value, "soldQuantity"), status,
                longValue(value, "inventoryVersion"));
    }

    private RescueCampaign copyCampaign(String id, String sellerId, RescueCampaign source,
            CampaignStatus status, RescueCampaign previous) {
        return RescueCampaign.restore(id, sellerId, source.getTitle(), source.getDescription(),
                source.getRescueReason(), source.getUrgencyLevel(), source.getRescueMode(),
                source.getBatchTargets(), source.getTargetQuantity(),
                previous == null ? 0D : previous.getReservedQuantity(),
                previous == null ? 0D : previous.getRescuedQuantity(),
                source.getStartDate(), source.getEndDate(), source.getLatitude(), source.getLongitude(),
                previous == null ? 0D : previous.getCurrentLatitude(),
                previous == null ? 0D : previous.getCurrentLongitude(),
                previous == null ? null : previous.getLocationUpdatedAt(),
                previous != null && source.getRescueMode() == RescueMode.MOBILE_POINT
                        && previous.isLocationSharingEnabled(), source.getLocationName(), status);
    }

    private ProductBatch copyBatch(ProductBatch source, long version) {
        return ProductBatch.restore(source.getId(), source.getProductId(), source.getActiveCampaignId(),
                source.getHarvestDate(), source.getExpiryDate(), source.getInitialQuantity(),
                source.getAvailableQuantity(), source.getReservedQuantity(), source.getSoldQuantity(),
                source.getStatus(), version);
    }

    private Map<String, Object> campaignMap(RescueCampaign value) {
        Map<String, Object> data = new HashMap<>(); data.put("id", value.getId());
        data.put("sellerId", value.getSellerId()); data.put("title", value.getTitle());
        data.put("description", value.getDescription()); data.put("rescueReason", value.getRescueReason().name());
        data.put("urgencyLevel", value.getUrgencyLevel().name()); data.put("rescueMode", value.getRescueMode().name());
        data.put("batchTargets", value.getBatchTargets()); data.put("targetQuantity", value.getTargetQuantity());
        data.put("reservedQuantity", value.getReservedQuantity()); data.put("rescuedQuantity", value.getRescuedQuantity());
        data.put("startDate", value.getStartDate()); data.put("endDate", value.getEndDate());
        data.put("latitude", value.getLatitude()); data.put("longitude", value.getLongitude());
        data.put("currentLatitude", value.getCurrentLatitude()); data.put("currentLongitude", value.getCurrentLongitude());
        data.put("locationUpdatedAt", value.getLocationUpdatedAt());
        data.put("locationSharingEnabled", value.isLocationSharingEnabled());
        data.put("locationName", value.getLocationName()); data.put("status", value.getStatus().name()); return data;
    }

    private Map<String, Object> batchMap(ProductBatch value) {
        Map<String, Object> data = new HashMap<>(); data.put("id", value.getId());
        data.put("productId", value.getProductId()); data.put("activeCampaignId", value.getActiveCampaignId());
        data.put("harvestDate", value.getHarvestDate()); data.put("expiryDate", value.getExpiryDate());
        data.put("initialQuantity", value.getInitialQuantity()); data.put("availableQuantity", value.getAvailableQuantity());
        data.put("reservedQuantity", value.getReservedQuantity()); data.put("soldQuantity", value.getSoldQuantity());
        data.put("status", value.getStatus().name()); data.put("inventoryVersion", value.getInventoryVersion()); return data;
    }

    private void fallback(String id, CampaignCallback callback) {
        cacheExecutor.execute(() -> {
            CampaignCacheEntity cached = campaignDao.findCampaign(id);
            if (cached == null) callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy chiến dịch.");
            else try { callback.onSuccess(CampaignCacheMapper.toDomain(cached)); }
            catch (IllegalArgumentException | IllegalStateException error) {
                callback.onError(ErrorCode.NOT_FOUND, "Cache chiến dịch cũ không còn hợp lệ.");
            }
        });
    }
    private void cache(RescueCampaign value) {
        cacheExecutor.execute(() -> campaignDao.replaceCampaign(CampaignCacheMapper.toEntity(
                value, System.currentTimeMillis())));
    }
    private void notifyFailure(Exception error, ErrorConsumer consumer) {
        String code = error.getMessage();
        if ("STALE_VERSION".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Tồn kho đã thay đổi. Hãy tải lại."); return; }
        if ("BATCH_IN_USE".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Batch đang thuộc chiến dịch khác."); return; }
        if ("BATCH_EXPIRED".equals(code)) { consumer.accept(ErrorCode.VALIDATION, "Không thể dùng batch đã hết hạn."); return; }
        if ("INSUFFICIENT_BATCH_TARGET".equals(code)) { consumer.accept(ErrorCode.VALIDATION, "Target vượt tồn khả dụng của batch."); return; }
        if ("CAMPAIGN_LOCKED".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Chỉ campaign nháp hoặc bị từ chối mới được sửa."); return; }
        if ("CAMPAIGN_NOT_ACTIVE".equals(code) || "MOBILE_NOT_ACTIVE".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Campaign chưa ACTIVE hoặc không đúng chế độ."); return; }
        if ("NOT_FOUND".equals(code) || "BATCH_NOT_FOUND".equals(code)) { consumer.accept(ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu liên quan."); return; }
        if ("FORBIDDEN".equals(code)) { consumer.accept(ErrorCode.FORBIDDEN, "Bạn không sở hữu chiến dịch này."); return; }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            consumer.accept(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code value = ((FirebaseFirestoreException) error).getCode();
            if (value == FirebaseFirestoreException.Code.PERMISSION_DENIED) { consumer.accept(ErrorCode.FORBIDDEN, "Seller chưa được duyệt hoặc không có quyền."); return; }
            if (value == FirebaseFirestoreException.Code.UNAVAILABLE) { consumer.accept(ErrorCode.NETWORK, "Không thể kết nối Firestore; cache vẫn được giữ."); return; }
            if (value == FirebaseFirestoreException.Code.ABORTED) { consumer.accept(ErrorCode.CONFLICT, "Dữ liệu vừa thay đổi. Hãy tải lại."); return; }
        }
        consumer.accept(ErrorCode.UNKNOWN, "Không thể xử lý chiến dịch.");
    }

    private String currentUserId() { return auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid(); }
    private static Map<String, Double> targetMap(Object raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?>) for (Map.Entry<?, ?> item : ((Map<?, ?>) raw).entrySet()) {
            if (item.getKey() instanceof String && item.getValue() instanceof Number) {
                result.put((String) item.getKey(), ((Number) item.getValue()).doubleValue());
            }
        }
        return result;
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
    private static final class MutationResult {
        private final RescueCampaign campaign; private final ProductBatch batch;
        private MutationResult(RescueCampaign campaign, ProductBatch batch) {
            this.campaign = campaign; this.batch = batch;
        }
    }
    @Override public void close() { cacheExecutor.shutdownNow(); }

    private boolean online(Object callback, String message) {
        if (network.isOnline()) return true;
        if (callback instanceof ActionCallback) ((ActionCallback) callback).onError(ErrorCode.NETWORK, message);
        else if (callback instanceof CampaignCallback) ((CampaignCallback) callback).onError(ErrorCode.NETWORK, message);
        return false;
    }
}
