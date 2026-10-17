package com.rescuefarm.data.remote.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import androidx.lifecycle.LiveData;
import com.rescuefarm.data.repository.AdminRepository;
import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import com.rescuefarm.data.repository.admin.AdminListItem;
import com.rescuefarm.service.admin.AdminTransitionPolicy;
import com.rescuefarm.service.backend.RenderBackendClient;
import org.json.JSONObject;
import com.rescuefarm.service.network.NetworkStatusProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;

public final class FirebaseAdminRepository implements AdminRepository {
    private static final String USERS = "users";
    private static final String AUDIT = "adminAuditLogs";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final NetworkStatusProvider network;
    private final AdminTransitionPolicy policy = new AdminTransitionPolicy();

    public FirebaseAdminRepository(NetworkStatusProvider network) { this.network = network; }

    @Override public void loadDashboard(DashboardCallback callback) {
        if (!online(callback)) return;
        org.json.JSONObject request = new org.json.JSONObject();
        RenderBackendClient.post("/admin/dashboard", request, new RenderBackendClient.Callback() {
            @Override public void onSuccess(org.json.JSONObject response) {
                try {
                    EnumMap<Section, Integer> totals = new EnumMap<>(Section.class);
                    org.json.JSONObject rawTotals = response.optJSONObject("totals");
                    for (Section section : dashboardSections())
                        totals.put(section, rawTotals == null ? 0 : rawTotals.optInt(section.name(), 0));
                    callback.onSuccess(new AdminDashboardSnapshot(totals,
                            response.optInt("pendingSellers"), response.optInt("pendingPosts"),
                            response.optInt("pendingCampaigns"), response.optInt("pendingReviews"),
                            response.optInt("openReports"), response.optDouble("deliveredRevenue"),
                            response.optInt("activeCampaigns"), response.optInt("criticalCampaigns"),
                            response.optInt("liveMobilePoints"), response.optDouble("totalRescuedQuantity"),
                            response.optDouble("completionRate"), response.optString("calculatedAt")));
                } catch (Exception error) {
                    callback.onError(ErrorCode.UNKNOWN, "Không đọc được dữ liệu analytics từ backend.");
                }
            }
            @Override public void onError(String message) {
                callback.onError(ErrorCode.NETWORK, "Không tải được dashboard Admin từ Render: " + message);
            }
        });
    }

    @Override public void loadSection(Section section, int limit, ListCallback callback) {
        if (!online(callback)) return;
        if (section == null || section == Section.ANALYTICS || section == Section.SHIPPING_SETTINGS
                || section.getCollection().isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Khu vực quản trị không hợp lệ."); return;
        }
        ensureAdmin(() -> db.collection(section.getCollection()).limit(Math.max(1, Math.min(100, limit)))
                .get().addOnSuccessListener(snapshot -> {
                    List<AdminListItem> result = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) result.add(mapItem(section, document));
                    callback.onSuccess(result);
                }).addOnFailureListener(error -> fail(error, callback)), callback);
    }

    @Override public void loadSectionPage(Section section, int limit, String status, String cursor,
            PageCallback callback) {
        loadSectionPage(section, limit, status, cursor, "", callback);
    }

    @Override public void loadSectionPage(Section section, int limit, String status, String cursor,
            String searchTerm, PageCallback callback) {
        loadSectionPage(section, limit, status, cursor, searchTerm, null, null, callback);
    }

    @Override public void loadSectionPage(Section section, int limit, String status, String cursor,
            String searchTerm, Date fromDate, Date throughDate, PageCallback callback) {
        if (!online(callback)) return;
        if (section == null || section == Section.ANALYTICS || section.getCollection().isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Khu vực quản trị không hợp lệ."); return;
        }
        ensureAdmin(() -> {
            String search = trim(searchTerm);
            String requestedFilter = clean(status);
            if (section == Section.SELLER_APPLICATIONS) {
                loadSellerAccountQueue(search, requestedFilter, trim(cursor),
                        Math.max(1, Math.min(100, limit)), callback);
                return;
            }
            if (search.toLowerCase(java.util.Locale.ROOT).startsWith("id:") && search.length() > 3) {
                String exactId = search.substring(3).trim();
                db.collection(section.getCollection()).document(exactId).get()
                        .addOnSuccessListener(document -> {
                            if (!document.exists()) { callback.onSuccess(java.util.Collections.emptyList(), "", false); return; }
                            AdminListItem item = mapItem(section, document);
                            if (!matchesSectionFilter(section, requestedFilter, document, item))
                                callback.onSuccess(java.util.Collections.emptyList(), "", false);
                            else callback.onSuccess(java.util.Collections.singletonList(item), "", false);
                        }).addOnFailureListener(error -> fail(error, callback));
                return;
            }
            com.google.firebase.firestore.Query query = db.collection(section.getCollection());
            String field = search.isEmpty() ? "" : searchField(section, search);
            String value = search.isEmpty() ? "" : searchValue(section, search);
            if (!search.isEmpty() && field.isEmpty()) {
                callback.onError(ErrorCode.VALIDATION, "Không hỗ trợ tìm kiếm theo khóa này."); return;
            }
            if (!search.isEmpty()) {
                query = query.whereGreaterThanOrEqualTo(field, value)
                        .whereLessThanOrEqualTo(field, value + "\uf8ff")
                        .orderBy(field).orderBy(com.google.firebase.firestore.FieldPath.documentId());
            } else if (!requestedFilter.isEmpty()) {
                if ((section == Section.CATEGORIES || section == Section.BANNERS)
                        && (requestedFilter.equals("ACTIVE") || requestedFilter.equals("INACTIVE")))
                    query = query.whereEqualTo("active", "ACTIVE".equals(requestedFilter));
                else if (section == Section.CATEGORIES || section == Section.BANNERS) { /* irrelevant status: leave unfiltered */ }
                else if (section == Section.ORDERS && requestedFilter.startsWith("FULFILLMENT:"))
                    query = query.whereEqualTo("fulfillmentType", requestedFilter.substring("FULFILLMENT:".length()));
                else if (section == Section.ORDERS && requestedFilter.startsWith("PAYMENT:"))
                    query = query.whereEqualTo("paymentMethod", requestedFilter.substring("PAYMENT:".length()));
                else if (section == Section.ORDERS && requestedFilter.startsWith("PAYMENT_STATUS:"))
                    query = query.whereEqualTo("paymentStatus", requestedFilter.substring("PAYMENT_STATUS:".length()));
                else query = query.whereEqualTo("status", section == Section.ORDERS
                        && requestedFilter.startsWith("STATUS:") ? requestedFilter.substring("STATUS:".length()) : requestedFilter);
            }
            if (search.isEmpty()) query = query.orderBy(com.google.firebase.firestore.FieldPath.documentId());
            final com.google.firebase.firestore.Query baseQuery = query;
            int pageLimit = Math.max(1, Math.min(100, limit));
            if (trim(cursor).isEmpty()) fetchSectionPage(section, requestedFilter, search, baseQuery,
                    null, "", pageLimit, fromDate, throughDate, callback);
            else db.collection(section.getCollection()).document(trim(cursor)).get()
                    .addOnSuccessListener(last -> fetchSectionPage(section, requestedFilter, search,
                            baseQuery, search.isEmpty() ? null : last,
                            search.isEmpty() ? trim(cursor) : "", pageLimit, fromDate, throughDate, callback))
                    .addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    private void fetchSectionPage(Section section, String filter, String search,
            com.google.firebase.firestore.Query baseQuery, DocumentSnapshot last,
            String documentIdCursor, int limit, Date fromDate, Date throughDate, PageCallback callback) {
        com.google.firebase.firestore.Query page = last != null ? baseQuery.startAfter(last)
                : !documentIdCursor.isEmpty() ? baseQuery.startAfter(documentIdCursor) : baseQuery;
        page.limit(limit).get().addOnSuccessListener(snapshot -> {
            List<AdminListItem> result = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                AdminListItem item = mapItem(section, document);
                Date createdAt = document.getDate("createdAt");
                boolean inDateRange = (fromDate == null || (createdAt != null && !createdAt.before(fromDate)))
                        && (throughDate == null || (createdAt != null && createdAt.before(throughDate)));
                if (inDateRange && (search.isEmpty() || matchesSectionFilter(section, filter, document, item)))
                    result.add(item);
            }
            String next = snapshot.isEmpty() ? "" : snapshot.getDocuments().get(snapshot.size() - 1).getId();
            callback.onSuccess(result, next, snapshot.size() >= limit);
        }).addOnFailureListener(error -> fail(error, callback));
    }

    private static boolean matchesSectionFilter(Section section, String filter,
            DocumentSnapshot document, AdminListItem item) {
        if (filter.isEmpty()) return true;
        if ((section == Section.CATEGORIES || section == Section.BANNERS)
                && (filter.equals("ACTIVE") || filter.equals("INACTIVE")))
            return Boolean.TRUE.equals(document.getBoolean("active")) == "ACTIVE".equals(filter);
        if (section == Section.ORDERS && filter.startsWith("FULFILLMENT:"))
            return filter.substring("FULFILLMENT:".length()).equals(clean(document.getString("fulfillmentType")));
        if (section == Section.ORDERS && filter.startsWith("PAYMENT:"))
            return filter.substring("PAYMENT:".length()).equals(clean(document.getString("paymentMethod")));
        if (section == Section.ORDERS && filter.startsWith("PAYMENT_STATUS:"))
            return filter.substring("PAYMENT_STATUS:".length()).equals(clean(document.getString("paymentStatus")));
        String expected = section == Section.ORDERS && filter.startsWith("STATUS:")
                ? filter.substring("STATUS:".length()) : filter;
        return expected.equals(item.getStatus());
    }

    private static String searchField(Section section, String search) {
        String lower = search.toLowerCase(java.util.Locale.ROOT);
        if (section == Section.ORDERS && lower.startsWith("seller:")) return "sellerId";
        if (section == Section.ORDERS && lower.startsWith("customer:")) return "ownerId";
        if (section == Section.ORDERS && lower.startsWith("code:")) return "orderCode";
        if (section == Section.REPORTS && lower.startsWith("target:")) return "targetId";
        switch (section) {
            case SELLER_APPLICATIONS: return "shopName";
            case USERS: return "email";
            case POSTS: case CAMPAIGNS: return "title";
            case PRODUCTS: return "name";
            case CATEGORIES: return "name";
            case BANNERS: return "title";
            case REVIEWS: return "productId";
            case REPORTS: return "targetId";
            case ORDERS: return "orderCode";
            default: return "";
        }
    }

    private static String searchValue(Section section, String search) {
        int separator = search.indexOf(':');
        if ((section == Section.ORDERS || section == Section.REPORTS) && separator >= 0)
            return search.substring(separator + 1).trim();
        return search;
    }

    @Override public LiveData<List<AdminListItem>> observeSection(Section section, int limit) {
        return new LiveData<List<AdminListItem>>() {
            private ListenerRegistration registration;
            private boolean active;
            @Override protected void onActive() {
                active = true;
                if (section == null || section == Section.ANALYTICS || section.getCollection().isEmpty()) return;
                ensureAdmin(() -> {
                    if (!active) return;
                    String collection = section == Section.SELLER_APPLICATIONS
                            ? USERS : section.getCollection();
                    registration = db.collection(collection)
                            .limit(Math.max(1, Math.min(100, limit)))
                            .addSnapshotListener((snapshot, error) -> {
                                if (error != null || snapshot == null) return;
                                List<AdminListItem> result = new ArrayList<>();
                                for (DocumentSnapshot document : snapshot.getDocuments()) {
                                    if (section == Section.SELLER_APPLICATIONS) {
                                        if ("SELLER".equals(clean(document.getString("role"))))
                                            result.add(mapSellerAccount(document));
                                    } else result.add(mapItem(section, document));
                                }
                                postValue(result);
                            });
                }, new ListCallback() {
                    @Override public void onSuccess(List<AdminListItem> items) { }
                    @Override public void onError(ErrorCode code, String message) { }
                });
            }
            @Override protected void onInactive() {
                active = false;
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }

    @Override public void transition(Section section, String resourceId, String targetStatus,
            String reason, ActionCallback callback) {
        if (!online(callback)) return;
        String id = trim(resourceId), target = clean(targetStatus), note = reason == null ? "" : reason.trim();
        if (section == null || id.isEmpty() || target.isEmpty() || section.getCollection().isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Thiếu dữ liệu chuyển trạng thái."); return;
        }
        ensureAdmin(() -> {
            if (section == Section.CAMPAIGNS) {
                try {
                    JSONObject request = new JSONObject().put("campaignId", id)
                            .put("targetStatus", target).put("reason", note);
                    RenderBackendClient.post("/campaigns/moderate", request, new RenderBackendClient.Callback() {
                        @Override public void onSuccess(JSONObject response) {
                            callback.onSuccess();

                        }
                        @Override public void onError(String message) { callback.onError(ErrorCode.CONFLICT, message); }
                    });
                } catch (org.json.JSONException error) { callback.onError(ErrorCode.VALIDATION, "Yêu cầu duyệt không hợp lệ."); }
                return;
            }
            if (section == Section.SELLER_APPLICATIONS) {
                db.collection(section.getCollection()).document(id).get()
                        .addOnSuccessListener(application -> {
                            if (application.exists())
                                runTransition(section, id, target, note, java.util.Collections.emptyList(), callback);
                            else runSellerAccountTransition(id, target, note, callback);
                        }).addOnFailureListener(error -> fail(error, callback));
                return;
            }
            if (section == Section.PRODUCTS && "ACTIVE".equals(target)) {
                db.collection("productBatches").whereEqualTo("productId", id).get()
                        .addOnSuccessListener(snapshot -> {
                            List<String> candidateIds = new ArrayList<>();
                            for (DocumentSnapshot batch : snapshot.getDocuments()) candidateIds.add(batch.getId());
                            runTransition(section, id, target, note, candidateIds, callback);
                        }).addOnFailureListener(error -> fail(error, callback));
            } else runTransition(section, id, target, note, java.util.Collections.emptyList(), callback);
        }, callback);
    }

    @Override public void setActive(Section section, String resourceId, boolean active,
            String reason, ActionCallback callback) {
        if (!online(callback)) return;
        if (section != Section.CATEGORIES && section != Section.BANNERS) {
            callback.onError(ErrorCode.VALIDATION, "Chỉ category và banner hỗ trợ bật/tắt."); return;
        }
        String id = trim(resourceId), note = reason == null ? "" : reason.trim();
        if (id.isEmpty() || (!active && note.length() < 3)) {
            callback.onError(ErrorCode.VALIDATION, "Cần nhập lý do khi tắt nội dung."); return;
        }
        ensureAdmin(() -> {
            String adminId = auth.getCurrentUser().getUid();
            DocumentReference ref = db.collection(section.getCollection()).document(id);
            DocumentReference audit = db.collection(AUDIT).document();
            db.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(ref);
                if (!current.exists()) throw new IllegalStateException("RESOURCE_NOT_FOUND");
                if (active && section == Section.BANNERS && text(current, "imageUrl").isEmpty())
                    throw new IllegalArgumentException("Banner cần có ảnh trước khi bật hiển thị.");
                boolean before = Boolean.TRUE.equals(current.getBoolean("active"));
                if (before == active) return false;
                Map<String, Object> changes = moderation(adminId, note);
                changes.put("active", active);
                tx.update(ref, changes);
                tx.set(audit, auditMap(adminId, section, id, String.valueOf(before),
                        String.valueOf(active), note));
                return true;
            }).addOnSuccessListener(value -> callback.onSuccess())
                    .addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    @Override public void setResourceImage(Section section, String resourceId, String imageUrl,
            ActionCallback callback) {
        if (!online(callback)) return;
        if (section != Section.CATEGORIES && section != Section.BANNERS) {
            callback.onError(ErrorCode.VALIDATION, "Chỉ category và banner hỗ trợ cập nhật ảnh."); return;
        }
        String id = trim(resourceId), url = trim(imageUrl);
        if (id.isEmpty() || !url.startsWith("https://res.cloudinary.com/")) {
            callback.onError(ErrorCode.VALIDATION, "URL ảnh phải là secure_url từ Cloudinary."); return;
        }
        ensureAdmin(() -> {
            String adminId = auth.getCurrentUser().getUid();
            DocumentReference ref = db.collection(section.getCollection()).document(id);
            DocumentReference audit = db.collection(AUDIT).document();
            db.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(ref);
                if (!current.exists()) throw new IllegalStateException("RESOURCE_NOT_FOUND");
                String previous = text(current, "imageUrl");
                Map<String, Object> changes = moderation(adminId, "Updated Cloudinary image");
                changes.put("imageUrl", url);
                changes.put("imagePublicId", com.rescuefarm.service.media.CloudinaryAssetIds.fromSecureUrl(url));
                tx.update(ref, changes);
                tx.set(audit, auditMap(adminId, section, id,
                        previous.isEmpty() ? "NO_IMAGE" : "IMAGE_SET", "IMAGE_SET", "Updated Cloudinary image"));
                return true;
            }).addOnSuccessListener(value -> callback.onSuccess())
                    .addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    @Override public void saveCatalogResource(Section section, String resourceId,
            Map<String, Object> values, ActionCallback callback) {
        if (!online(callback)) return;
        if (section != Section.CATEGORIES && section != Section.BANNERS) {
            callback.onError(ErrorCode.VALIDATION, "Chỉ Category và Banner hỗ trợ biểu mẫu này."); return;
        }
        Map<String, Object> input = values == null ? java.util.Collections.emptyMap() : values;
        String id = trim(resourceId), name = trim(String.valueOf(input.get(
                section == Section.CATEGORIES ? "name" : "title")));
        String description = trim(String.valueOf(input.get("description")));
        String note = trim(String.valueOf(input.get("moderationReason")));
        String imageUrl = trim(String.valueOf(input.get("imageUrl")));
        String campaignId = trim(String.valueOf(input.get("campaignId")));
        Object orderValue = input.get("displayOrder");
        int displayOrder = orderValue instanceof Number ? ((Number) orderValue).intValue() : -1;
        boolean active = Boolean.TRUE.equals(input.get("active"));
        if ((section == Section.CATEGORIES && (id.isEmpty() || !id.matches("[A-Za-z0-9_-]{2,128}")))
                || (section == Section.BANNERS && !id.isEmpty() && !id.matches("[A-Za-z0-9_-]{2,128}"))
                || name.length() < (section == Section.CATEGORIES ? 2 : 3) || name.length() > 120
                || (section == Section.CATEGORIES && description.length() > 500)
                || displayOrder < 0 || displayOrder > 10000
                || (!imageUrl.isEmpty() && !imageUrl.startsWith("https://res.cloudinary.com/"))
                || (active && imageUrl.isEmpty())
                || (!active && note.length() < 3)) {
            callback.onError(ErrorCode.VALIDATION, "Kiểm tra ID, tên, thứ tự, ảnh và lý do tắt hiển thị."); return;
        }
        Date startDate = input.get("startDate") instanceof Date ? (Date) input.get("startDate") : null;
        Date endDate = input.get("endDate") instanceof Date ? (Date) input.get("endDate") : null;
        if (section == Section.BANNERS && (startDate == null || endDate == null || !endDate.after(startDate))) {
            callback.onError(ErrorCode.VALIDATION, "Thời gian kết thúc Banner phải sau thời gian bắt đầu."); return;
        }
        ensureAdmin(() -> {
            String adminId = auth.getCurrentUser().getUid();
            com.google.firebase.firestore.CollectionReference collection = db.collection(section.getCollection());
            DocumentReference ref = id.isEmpty() ? collection.document() : collection.document(id);
            DocumentReference audit = db.collection(AUDIT).document();
            db.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(ref);
                if (section == Section.BANNERS && !campaignId.isEmpty()) {
                    DocumentSnapshot campaign = tx.get(db.collection("campaigns").document(campaignId));
                    if (!campaign.exists() || !"ACTIVE".equals(clean(campaign.getString("status"))))
                        throw new IllegalArgumentException("Banner chỉ được liên kết Campaign đang hoạt động.");
                }
                if (!current.exists() && section == Section.BANNERS && id.isEmpty()) {
                    // Auto-generated Banner ID is stable once this transaction creates it.
                } else if (id.isEmpty()) {
                    throw new IllegalArgumentException("Category cần ID ổn định.");
                }
                Map<String, Object> changes = new HashMap<>();
                changes.put("id", ref.getId());
                changes.put(section == Section.CATEGORIES ? "name" : "title", name);
                if (section == Section.CATEGORIES) changes.put("description", description);
                changes.put("imageUrl", imageUrl.isEmpty() && current.exists()
                        ? text(current, "imageUrl") : imageUrl);
                String savedImageUrl = imageUrl.isEmpty() && current.exists()
                        ? text(current, "imageUrl") : imageUrl;
                changes.put("imagePublicId", com.rescuefarm.service.media.CloudinaryAssetIds.fromSecureUrl(savedImageUrl));
                changes.put("active", active);
                changes.put("displayOrder", displayOrder);
                if (section == Section.BANNERS) {
                    changes.put("campaignId", campaignId);
                    changes.put("startDate", startDate);
                    changes.put("endDate", endDate);
                }
                changes.put("moderatedBy", adminId);
                changes.put("moderatedAt", FieldValue.serverTimestamp());
                changes.put("moderationReason", note);
                changes.put("updatedAt", FieldValue.serverTimestamp());
                if (current.exists()) {
                    tx.update(ref, changes);
                } else {
                    changes.put("createdAt", FieldValue.serverTimestamp());
                    tx.set(ref, changes);
                }
                tx.set(audit, auditMap(adminId, section, ref.getId(),
                        current.exists() ? "UPDATED" : "CREATED", active ? "ACTIVE" : "INACTIVE", note));
                return null;
            }).addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    @Override public void loadShippingConfig(DetailCallback callback) {
        if (!online(callback)) return;
        ensureAdmin(() -> db.document("appConfig/shipping").get().addOnSuccessListener(snapshot -> {
            Map<String, Object> values = new HashMap<>();
            if (snapshot.exists() && snapshot.getData() != null) values.putAll(snapshot.getData());
            callback.onSuccess(values);
        }).addOnFailureListener(error -> fail(error, callback)), callback);
    }

    @Override public void saveShippingConfig(double baseFeeVnd, double freeDistanceKm,
            double perKmFeeVnd, ActionCallback callback) {
        if (!online(callback)) return;
        if (!Double.isFinite(baseFeeVnd) || !Double.isFinite(freeDistanceKm) || !Double.isFinite(perKmFeeVnd)
                || baseFeeVnd < 0 || baseFeeVnd > 1_000_000 || perKmFeeVnd < 0 || perKmFeeVnd > 1_000_000
                || freeDistanceKm < 0 || freeDistanceKm > 20040) {
            callback.onError(ErrorCode.VALIDATION, "Phí phải từ 0 đến 1.000.000 ₫; khoảng cách miễn phí từ 0 đến 20.040 km.");
            return;
        }
        ensureAdmin(() -> {
            String adminId = auth.getCurrentUser().getUid();
            DocumentReference config = db.document("appConfig/shipping");
            DocumentReference audit = db.collection(AUDIT).document();
            db.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(config);
                Map<String, Object> data = new HashMap<>();
                data.put("baseFeeVnd", baseFeeVnd); data.put("freeDistanceKm", freeDistanceKm);
                data.put("perKmFeeVnd", perKmFeeVnd); data.put("updatedBy", adminId);
                data.put("updatedAt", FieldValue.serverTimestamp()); tx.set(config, data);
                tx.set(audit, auditMap(adminId, "SHIPPING_SETTINGS", "shipping",
                        current.exists() ? "UPDATED" : "UNCONFIGURED", "UPDATED", "Updated global shipping pricing"));
                return null;
            }).addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    @Override public void loadDetail(Section section, String resourceId, DetailCallback callback) {
        if (!online(callback)) return;
        String id = trim(resourceId);
        if (section == Section.SHIPPING_SETTINGS) { loadShippingConfig(callback); return; }
        if (section == null || id.isEmpty() || section.getCollection().isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Thiếu dữ liệu chi tiết cần xem."); return;
        }
        ensureAdmin(() -> {
            if (section == Section.SELLER_APPLICATIONS) {
                loadSellerDetail(id, callback);
                return;
            }
            db.collection(section.getCollection()).document(id).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) { callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu."); return; }
                    Map<String, Object> detail = new HashMap<>(document.getData() == null
                            ? java.util.Collections.emptyMap() : document.getData());
                    detail.put("id", document.getId());
                    if (section == Section.SELLER_APPLICATIONS) {
                        db.collection(USERS).document(first(document, "sellerId", "id")).get()
                                .addOnSuccessListener(user -> {
                                    if (user.exists()) detail.put("sellerProfile", user.getData());
                                    callback.onSuccess(detail);
                                }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.ORDERS) {
                        Tasks.whenAllSuccess(db.collection("orders").document(id).collection("items").get(),
                                db.collection("payments").document(id).get(),
                                db.collection("shipments").document(id).get(),
                                db.collection("users").document(text(document, "ownerId")).get(),
                                db.collection("sellerPublicProfiles").document(text(document, "sellerId")).get(),
                                db.collection("notifications").whereEqualTo("referenceId", id).get())
                                .addOnSuccessListener(results -> {
                                    QuerySnapshot items = (QuerySnapshot) results.get(0);
                                    List<Map<String, Object>> rows = new ArrayList<>();
                                    for (DocumentSnapshot item : items.getDocuments()) {
                                        Map<String, Object> row = new HashMap<>(item.getData() == null
                                                ? java.util.Collections.emptyMap() : item.getData());
                                        row.put("id", item.getId()); rows.add(row);
                                    }
                                    detail.put("items", rows);
                                    DocumentSnapshot payment = (DocumentSnapshot) results.get(1);
                                    DocumentSnapshot shipment = (DocumentSnapshot) results.get(2);
                                    DocumentSnapshot customer = (DocumentSnapshot) results.get(3);
                                    DocumentSnapshot seller = (DocumentSnapshot) results.get(4);
                                    QuerySnapshot events = (QuerySnapshot) results.get(5);
                                    if (payment.exists()) detail.put("payment", payment.getData());
                                    if (shipment.exists()) detail.put("shipment", shipment.getData());
                                    if (customer.exists()) detail.put("customerProfile", customer.getData());
                                    if (seller.exists()) detail.put("sellerProfile", seller.getData());
                                    List<Map<String, Object>> timeline = snapshotMaps(events);
                                    timeline.sort((left, right) -> {
                                        Date a = left.get("createdAt") instanceof com.google.firebase.Timestamp
                                                ? ((com.google.firebase.Timestamp) left.get("createdAt")).toDate() : new Date(0);
                                        Date b = right.get("createdAt") instanceof com.google.firebase.Timestamp
                                                ? ((com.google.firebase.Timestamp) right.get("createdAt")).toDate() : new Date(0);
                                        return a.compareTo(b);
                                    });
                                    detail.put("orderTimeline", timeline);
                                    callback.onSuccess(detail);
                                }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.PRODUCTS) {
                        db.collection("productBatches").whereEqualTo("productId", id).get()
                                .addOnSuccessListener(snapshot -> {
                                    detail.put("batches", snapshotMaps(snapshot));
                                    loadModerationContext(section, detail, callback);
                                }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.CAMPAIGNS) {
                        Object rawTargets = detail.get("batchTargets");
                        List<Task<DocumentSnapshot>> batchTasks = new ArrayList<>();
                        if (rawTargets instanceof Map<?, ?>) for (Object batchId : ((Map<?, ?>) rawTargets).keySet())
                            batchTasks.add(db.collection("productBatches").document(String.valueOf(batchId)).get());
                        if (batchTasks.isEmpty()) loadModerationContext(section, detail, callback);
                        else Tasks.whenAllSuccess(batchTasks).addOnSuccessListener(results -> {
                            List<Map<String, Object>> batches = new ArrayList<>();
                            for (Object value : results) {
                                DocumentSnapshot batch = (DocumentSnapshot) value;
                                if (batch.exists()) {
                                    Map<String, Object> row = new HashMap<>(batch.getData() == null
                                            ? java.util.Collections.emptyMap() : batch.getData());
                                    row.put("id", batch.getId()); batches.add(row);
                                }
                            }
                            detail.put("batches", batches); loadModerationContext(section, detail, callback);
                        }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.REPORTS) {
                        loadReportedTarget(detail, callback);
                    } else loadModerationContext(section, detail, callback);
                }).addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    private void loadSellerAccountQueue(String search, String filter, String cursor, int limit,
            PageCallback callback) {
        db.collection(USERS).whereEqualTo("role", "SELLER")
                .orderBy(com.google.firebase.firestore.FieldPath.documentId()).get()
                .addOnSuccessListener(snapshot -> {
                    String needle = search.toLowerCase(java.util.Locale.ROOT);
                    if (needle.startsWith("id:")) needle = needle.substring(3).trim();
                    List<AdminListItem> sellers = new ArrayList<>();
                    for (DocumentSnapshot user : snapshot.getDocuments()) {
                        String sellerStatus = clean(user.getString("sellerStatus"));
                        String status = "PENDING_APPROVAL".equals(sellerStatus) ? "PENDING" : sellerStatus;
                        if (!filter.isEmpty() && !filter.equals(status)) continue;
                        String id = user.getId();
                        String title = first(user, "shopName", "representativeName", "fullName", "email");
                        String email = text(user, "email");
                        if (!needle.isEmpty() && !(id.toLowerCase(java.util.Locale.ROOT).contains(needle)
                                || title.toLowerCase(java.util.Locale.ROOT).contains(needle)
                                || email.toLowerCase(java.util.Locale.ROOT).contains(needle))) continue;
                        sellers.add(mapSellerAccount(user));
                    }
                    int start = 0;
                    if (!cursor.isEmpty()) for (int i = 0; i < sellers.size(); i++)
                        if (sellers.get(i).getId().equals(cursor)) { start = i + 1; break; }
                    int end = Math.min(sellers.size(), start + limit);
                    List<AdminListItem> page = new ArrayList<>(sellers.subList(start, end));
                    callback.onSuccess(page, end == 0 ? "" : sellers.get(end - 1).getId(), end < sellers.size());
                }).addOnFailureListener(error -> fail(error, callback));
    }

    private void loadSellerDetail(String sellerId, DetailCallback callback) {
        DocumentReference applicationRef = db.collection("sellerApplications").document(sellerId);
        DocumentReference userRef = db.collection(USERS).document(sellerId);
        Tasks.whenAllSuccess(applicationRef.get(), userRef.get()).addOnSuccessListener(results -> {
            DocumentSnapshot application = (DocumentSnapshot) results.get(0);
            DocumentSnapshot user = (DocumentSnapshot) results.get(1);
            if (!application.exists() && !user.exists()) {
                callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản seller.");
                return;
            }
            Map<String, Object> detail = new HashMap<>();
            if (application.exists() && application.getData() != null) detail.putAll(application.getData());
            if (user.exists()) {
                detail.put("sellerId", sellerId);
                detail.put("sellerProfile", user.getData());
            }
            detail.put("id", sellerId);
            if (!application.exists()) detail.put("applicationStatus", "CHƯA NỘP HỒ SƠ CHI TIẾT");
            callback.onSuccess(detail);
        }).addOnFailureListener(error -> fail(error, callback));
    }

    private static AdminListItem mapSellerAccount(DocumentSnapshot user) {
        String sellerStatus = clean(user.getString("sellerStatus"));
        String status = "PENDING_APPROVAL".equals(sellerStatus) ? "PENDING" : sellerStatus;
        return new AdminListItem(user.getId(), first(user, "shopName", "representativeName", "fullName", "email"),
                text(user, "email"), status, false);
    }

    private void runSellerAccountTransition(String sellerId, String target, String note,
            ActionCallback callback) {
        String adminId = auth.getCurrentUser().getUid();
        DocumentReference userRef = db.collection(USERS).document(sellerId);
        DocumentReference audit = db.collection(AUDIT).document();
        db.runTransaction(tx -> {
            DocumentSnapshot seller = tx.get(userRef);
            if (!seller.exists() || !"SELLER".equals(clean(seller.getString("role"))))
                throw new IllegalStateException("SELLER_NOT_FOUND");
            String before = clean(seller.getString("sellerStatus"));
            if (!"PENDING_APPROVAL".equals(before))
                throw new IllegalStateException("ADMIN_TRANSITION_INVALID");
            policy.validate(Section.SELLER_APPLICATIONS, "PENDING", target, note);
            Map<String, Object> changes = moderation(adminId, note);
            changes.put("sellerStatus", target);
            tx.update(userRef, changes);
            tx.set(audit, auditMap(adminId, Section.SELLER_APPLICATIONS, sellerId, "PENDING", target, note));
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> fail(error, callback));
    }

    private static List<Map<String, Object>> snapshotMaps(QuerySnapshot snapshot) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Map<String, Object> row = new HashMap<>(document.getData() == null
                    ? java.util.Collections.emptyMap() : document.getData());
            row.put("id", document.getId()); rows.add(row);
        }
        return rows;
    }

    private void loadReportedTarget(Map<String, Object> detail, DetailCallback callback) {
        String type = clean(String.valueOf(detail.get("targetType")));
        String targetId = trim(String.valueOf(detail.get("targetId")));
        String collection = targetCollection(type);
        if ("COMMENT".equals(type)) {
            resolveCommentReference(detail, reference -> reference.get().addOnSuccessListener(comment -> {
                if (!comment.exists()) { callback.onSuccess(detail); return; }
                Map<String, Object> target = new HashMap<>(comment.getData());
                target.put("id", comment.getId()); target.put("targetPath", reference.getPath());
                target.put("parentId", reference.getParent().getParent().getId());
                target.put("parentType", reference.getPath().startsWith("campaigns/") ? "CAMPAIGN" : "POST");
                detail.put("target", target); loadModerationContext(Section.REPORTS, detail, callback);
            }).addOnFailureListener(error -> fail(error, callback)), message -> { detail.put("targetResolutionError", message); callback.onSuccess(detail); });
        } else if (collection.isEmpty()) callback.onSuccess(detail);
        else db.collection(collection).document(targetId).get().addOnSuccessListener(target -> {
            if (target.exists()) {
                Map<String, Object> targetData = new HashMap<>(target.getData() == null
                        ? java.util.Collections.emptyMap() : target.getData());
                targetData.put("id", target.getId()); detail.put("target", targetData);
            }
            loadModerationContext(Section.REPORTS, detail, callback);
        }).addOnFailureListener(error -> fail(error, callback));
    }

    /** Explicit paths take precedence. Legacy ID-only reports must resolve uniquely. */
    private void resolveCommentReference(Map<String, Object> report,
            java.util.function.Consumer<DocumentReference> success, java.util.function.Consumer<String> error) {
        String id = mapText(report, "targetId"), path = mapText(report, "targetPath");
        if (!path.isEmpty()) {
            String[] parts = path.split("/", -1);
            String parentType = path.startsWith("campaigns/") ? "CAMPAIGN" : "POST";
            if (parts.length != 4 || !(parts[0].equals("campaigns") || parts[0].equals("posts"))
                    || !parts[2].equals("comments") || !parts[3].equals(id) || parts[1].isEmpty()
                    || !parts[1].equals(mapText(report, "parentId")) || !parentType.equals(mapText(report, "parentType"))) {
                error.accept("Ngữ cảnh bình luận trong báo cáo không hợp lệ."); return;
            }
            success.accept(db.document(path)); return;
        }
        db.collectionGroup("comments").whereEqualTo("id", id).limit(2).get().addOnSuccessListener(matches -> {
            if (matches.size() != 1) { error.accept(matches.isEmpty() ? "Bình luận đã bị xóa."
                    : "Báo cáo cũ có ID trùng ở nhiều nơi; cần bổ sung parentType, parentId và targetPath trước khi xử lý."); return; }
            DocumentReference reference = matches.getDocuments().get(0).getReference();
            if (!reference.getPath().matches("(campaigns|posts)/[^/]+/comments/[^/]+")) {
                error.accept("Loại bình luận không được hỗ trợ."); return;
            }
            success.accept(reference);
        }).addOnFailureListener(e -> error.accept("Không tải được bình luận: " + e.getMessage()));
    }

    private static final class RelatedDocument {
        final DocumentReference reference;
        final String key;
        RelatedDocument(DocumentReference reference, String key) { this.reference = reference; this.key = key; }
    }

    private void loadModerationContext(Section section, Map<String, Object> detail,
            DetailCallback callback) {
        Map<String, Object> subject = detail;
        Section subjectSection = section;
        if (section == Section.REPORTS) {
            Object raw = detail.get("target");
            if (!(raw instanceof Map<?, ?>)) { callback.onSuccess(detail); return; }
            subject = new HashMap<>((Map<String, Object>) raw);
            String type = clean(mapText(detail, "targetType"));
            subjectSection = "PRODUCT".equals(type) ? Section.PRODUCTS
                    : "CAMPAIGN".equals(type) ? Section.CAMPAIGNS
                    : "POST".equals(type) ? Section.POSTS
                    : "REVIEW".equals(type) ? Section.REVIEWS : Section.REPORTS;
        }
        List<RelatedDocument> related = new ArrayList<>();
        String sellerId = mapText(subject, "sellerId");
        switch (subjectSection) {
            case PRODUCTS:
                if (!sellerId.isEmpty()) related.add(new RelatedDocument(db.collection(USERS).document(sellerId), "sellerProfile"));
                String categoryId = mapText(subject, "categoryId");
                if (!categoryId.isEmpty()) related.add(new RelatedDocument(db.collection("categories").document(categoryId), "categoryDetail"));
                break;
            case CAMPAIGNS:
                if (!sellerId.isEmpty()) related.add(new RelatedDocument(db.collection(USERS).document(sellerId), "sellerProfile"));
                break;
            case POSTS:
                if (!sellerId.isEmpty()) related.add(new RelatedDocument(db.collection(USERS).document(sellerId), "sellerProfile"));
                String campaignId = mapText(subject, "campaignId");
                if (!campaignId.isEmpty()) related.add(new RelatedDocument(db.collection("campaigns").document(campaignId), "campaignDetail"));
                Object linked = subject.get("linkedProductIds");
                if (linked instanceof List<?>) for (Object rawId : (List<?>) linked) {
                    String productId = rawId == null ? "" : trim(String.valueOf(rawId));
                    if (!productId.isEmpty()) related.add(new RelatedDocument(db.collection("products").document(productId), "linkedProducts"));
                }
                break;
            case REVIEWS:
                String productId = mapText(subject, "productId");
                String customerId = mapText(subject, "customerId");
                String orderId = mapText(subject, "orderId");
                String orderItemId = mapText(subject, "orderItemId");
                if (!productId.isEmpty()) related.add(new RelatedDocument(db.collection("products").document(productId), "productDetail"));
                if (!customerId.isEmpty()) related.add(new RelatedDocument(db.collection(USERS).document(customerId), "customerProfile"));
                if (!orderId.isEmpty()) {
                    related.add(new RelatedDocument(db.collection("orders").document(orderId), "orderDetail"));
                    if (!orderItemId.isEmpty()) related.add(new RelatedDocument(db.collection("orders").document(orderId)
                            .collection("items").document(orderItemId), "orderItem"));
                }
                break;
            case REPORTS:
                String parentType = mapText(subject, "parentType"), parentId = mapText(subject, "parentId");
                if (!parentId.isEmpty() && ("CAMPAIGN".equals(parentType) || "POST".equals(parentType)))
                    related.add(new RelatedDocument(db.collection("CAMPAIGN".equals(parentType) ? "campaigns" : "posts").document(parentId), "commentParent"));
                Object targetRaw = detail.get("target");
                if (targetRaw instanceof Map<?, ?>) {
                    Map<?, ?> target = (Map<?, ?>) targetRaw;
                    String userId = mapText(target, "userId");
                    if (userId.isEmpty()) userId = mapText(target, "customerId");
                    if (userId.isEmpty()) userId = mapText(target, "sellerId");
                    if (userId.isEmpty() && "USER".equals(clean(mapText(detail, "targetType"))))
                        userId = mapText(target, "id");
                    if (!userId.isEmpty()) related.add(new RelatedDocument(db.collection(USERS).document(userId), "targetOwnerProfile"));
                }
                break;
            default: break;
        }
        if (related.isEmpty()) {
            if (section == Section.REPORTS) detail.put("target", subject);
            callback.onSuccess(detail); return;
        }
        final Map<String, Object> subjectResult = subject;
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (RelatedDocument relation : related) tasks.add(relation.reference.get());
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            for (int index = 0; index < results.size(); index++) {
                DocumentSnapshot document = (DocumentSnapshot) results.get(index);
                if (!document.exists()) continue;
                Map<String, Object> value = new HashMap<>(document.getData() == null
                        ? java.util.Collections.emptyMap() : document.getData());
                value.put("id", document.getId());
                String key = related.get(index).key;
                if ("linkedProducts".equals(key)) grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(value);
                else subjectResult.put(key, value);
            }
            if (!grouped.isEmpty()) subjectResult.putAll(grouped);
            if (section == Section.REPORTS) detail.put("target", subjectResult);
            callback.onSuccess(detail);
        }).addOnFailureListener(error -> fail(error, callback));
    }

    private static String mapText(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    @Override public void resolveReport(String reportId, ReportAction action, String reason,
            ActionCallback callback) {
        if (!online(callback)) return;
        String id = trim(reportId), note = reason == null ? "" : reason.trim();
        if (id.isEmpty() || action == null || (action != ReportAction.MARK_IN_REVIEW && note.length() < 3)) {
            callback.onError(ErrorCode.VALIDATION, "Cần nhập lý do xử lý từ 3 ký tự."); return;
        }
        ensureAdmin(() -> db.collection("reports").document(id).get().addOnSuccessListener(report -> {
            if (!report.exists()) { callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy report."); return; }
            String type = clean(report.getString("targetType"));
            String targetId = trim(report.getString("targetId"));
            if (action == ReportAction.MARK_IN_REVIEW || action == ReportAction.DISMISS) {
                runReportResolution(id, action, note, null, callback); return;
            }
            if ("COMMENT".equals(type)) {
                resolveCommentReference(report.getData(), reference ->
                        runReportResolution(id, action, note, reference, callback),
                        message -> callback.onError(ErrorCode.VALIDATION, message));
                return;
            }
            String collection = targetCollection(type);
            if (collection.isEmpty()) { callback.onError(ErrorCode.VALIDATION, "Loại đối tượng này không hỗ trợ thao tác."); return; }
            runReportResolution(id, action, note, db.collection(collection).document(targetId), callback);
        }).addOnFailureListener(error -> fail(error, callback)), callback);
    }

    private void runReportResolution(String reportId, ReportAction action, String note,
            DocumentReference commentRef, ActionCallback callback) {
        String adminId = auth.getCurrentUser().getUid();
        DocumentReference reportRef = db.collection("reports").document(reportId);
        db.runTransaction(tx -> {
            DocumentSnapshot report = tx.get(reportRef);
            if (!report.exists()) throw new IllegalStateException("REPORT_NOT_FOUND");
            String currentStatus = clean(report.getString("status"));
            if (!currentStatus.equals("PENDING") && !currentStatus.equals("IN_REVIEW"))
                throw new IllegalStateException("REPORT_ALREADY_CLOSED");
            if (action == ReportAction.MARK_IN_REVIEW && !currentStatus.equals("PENDING"))
                throw new IllegalStateException("REPORT_TRANSITION_INVALID");
            DocumentReference targetRef = commentRef;
            String type = clean(report.getString("targetType"));
            if (targetRef == null && action != ReportAction.MARK_IN_REVIEW && action != ReportAction.DISMISS) {
                String collection = "USER".equals(type) ? "users" : targetCollection(type);
                if (collection.isEmpty()) throw new IllegalArgumentException("Unsupported report target");
                targetRef = db.collection(collection).document(trim(report.getString("targetId")));
            }
            if ("COMMENT".equals(type) && targetRef != null && !clean(report.getString("targetPath")).isEmpty()
                    && !targetRef.getPath().equals(report.getString("targetPath"))) throw new IllegalStateException("REPORT_TARGET_MISMATCH");
            DocumentSnapshot target = targetRef == null ? null : tx.get(targetRef);
            if (targetRef != null && (target == null || !target.exists()))
                throw new IllegalStateException("REPORT_TARGET_NOT_FOUND");
            List<DocumentSnapshot> batches = new ArrayList<>();
            String targetStatus = target == null ? "" : clean(target.getString("status"));
            String targetCollection = targetRef == null ? "" : targetRef.getParent().getId();
            if (action == ReportAction.HIDE_TARGET && "campaigns".equals(targetCollection)) {
                Object raw = target.get("batchTargets");
                if (raw instanceof Map<?, ?>) for (Object rawId : ((Map<?, ?>) raw).keySet()) {
                    DocumentSnapshot batch = tx.get(db.collection("productBatches").document(String.valueOf(rawId)));
                    if (!batch.exists()) throw new IllegalStateException("CAMPAIGN_BATCH_NOT_FOUND");
                    if (target.getId().equals(text(batch, "activeCampaignId"))) batches.add(batch);
                }
            }
            DocumentSnapshot reviewProduct = action == ReportAction.HIDE_TARGET && "reviews".equals(targetCollection)
                    ? tx.get(db.collection("products").document(text(target, "productId"))) : null;
            DocumentSnapshot user = null;
            if (action == ReportAction.BLOCK_USER) {
                String userId = "users".equals(targetCollection) ? target.getId()
                        : first(target, "sellerId", "customerId", "userId");
                if (userId.isEmpty() || userId.equals(adminId)) throw new SecurityException("TARGET_USER_FORBIDDEN");
                targetRef = db.collection(USERS).document(userId);
                user = targetRef.equals(target.getReference()) ? target : tx.get(targetRef);
                if (!user.exists() || "ADMIN".equals(clean(user.getString("role"))))
                    throw new SecurityException("TARGET_USER_FORBIDDEN");
            }
            if (action == ReportAction.HIDE_TARGET) validateHideTarget(targetCollection, targetStatus);
            Map<String, Object> reportChanges = new HashMap<>();
            reportChanges.put("status", action == ReportAction.MARK_IN_REVIEW ? "IN_REVIEW"
                    : action == ReportAction.DISMISS ? "REJECTED" : "RESOLVED");
            reportChanges.put("resolvedBy", adminId); reportChanges.put("resolutionNote", note);
            reportChanges.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(reportRef, reportChanges);
            String before = currentStatus;
            String after = String.valueOf(reportChanges.get("status"));
            if (action == ReportAction.HIDE_TARGET) {
                Map<String, Object> changes = moderation(adminId, note);
                changes.put("status", "campaigns".equals(targetCollection) ? "STOPPED" : "HIDDEN");
                if ("products".equals(targetCollection)) changes.put("moderationPreviousStatus", targetStatus);
                tx.update(targetRef, changes);
                Map<String, Object> targetAudit = auditMap(adminId,
                        sectionForCollection(targetCollection), target.getId(), targetStatus,
                        String.valueOf(changes.get("status")), note);
                if ("comments".equals(targetCollection)) {
                    targetAudit.put("resourceType", "COMMENTS"); targetAudit.put("resourcePath", targetRef.getPath());
                }
                targetAudit.put("reportId", reportId);
                tx.set(db.collection(AUDIT).document(), targetAudit);
                if (reviewProduct != null) FirebaseReviewSummary.update(tx, reviewProduct, target,
                        target.getId(), 0);
                for (DocumentSnapshot batch : batches) {
                    long version = batch.getLong("inventoryVersion") == null ? 0L : batch.getLong("inventoryVersion");
                    tx.update(batch.getReference(), "activeCampaignId", null,
                            "inventoryVersion", version + 1L, "updatedAt", FieldValue.serverTimestamp());
                    Map<String, Object> batchAudit = auditMap(adminId, "PRODUCT_BATCHES", batch.getId(),
                            target.getId(), "UNLINKED_FROM_STOPPED_CAMPAIGN", note);
                    batchAudit.put("reportId", reportId);
                    tx.set(db.collection(AUDIT).document(), batchAudit);
                }
            } else if (action == ReportAction.BLOCK_USER) {
                Map<String, Object> changes = moderation(adminId, note); changes.put("status", "BLOCKED");
                tx.update(targetRef, changes);
                Map<String, Object> userAudit = auditMap(adminId, "USERS", targetRef.getId(),
                        clean(user.getString("status")), "BLOCKED", note);
                userAudit.put("reportId", reportId);
                tx.set(db.collection(AUDIT).document(), userAudit);
            } else if (action == ReportAction.MARK_IN_REVIEW || action == ReportAction.DISMISS) {
                // The report transition itself is the auditable moderation action.
            }
            Map<String, Object> reportAudit = auditMap(adminId, "REPORTS", reportId, before, after, note);
            reportAudit.put("reportId", reportId);
            tx.set(db.collection(AUDIT).document(), reportAudit);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> fail(error, callback));
    }

    private static void validateHideTarget(String collection, String status) {
        boolean allowed = ("products".equals(collection) && java.util.Arrays.asList("ACTIVE", "INACTIVE", "SOLD_OUT").contains(status))
                || ("posts".equals(collection) && "PUBLISHED".equals(status))
                || ("campaigns".equals(collection) && "ACTIVE".equals(status))
                || ("reviews".equals(collection) && "PUBLISHED".equals(status))
                || ("comments".equals(collection) && java.util.Arrays.asList("ACTIVE", "EDITED").contains(status));
        if (!allowed) throw new IllegalStateException("REPORT_TARGET_CANNOT_BE_HIDDEN");
    }

    private static String targetCollection(String type) {
        switch (clean(type)) {
            case "USER": return "users"; case "PRODUCT": return "products";
            case "CAMPAIGN": return "campaigns"; case "POST": return "posts";
            case "REVIEW": return "reviews"; case "COMMENT": return "comments";
            default: return "";
        }
    }

    private static Section sectionForCollection(String collection) {
        switch (collection) {
            case "products": return Section.PRODUCTS; case "posts": return Section.POSTS;
            case "campaigns": return Section.CAMPAIGNS; case "reviews": return Section.REVIEWS;
            default: return Section.REPORTS;
        }
    }

    private void runTransition(Section section, String id, String target, String note,
            List<String> candidateBatchIds, ActionCallback callback) {
        String adminId = auth.getCurrentUser().getUid();
        DocumentReference ref = db.collection(section.getCollection()).document(id);
        DocumentReference audit = db.collection(AUDIT).document();
        db.runTransaction(tx -> {
            DocumentSnapshot current = tx.get(ref);
            if (!current.exists()) throw new IllegalStateException("RESOURCE_NOT_FOUND");
            String before = clean(current.getString("status"));
            policy.validate(section, before, target, note);
            if (section == Section.PRODUCTS && "HIDDEN".equals(before)
                    && !target.equals(clean(current.getString("moderationPreviousStatus")))) {
                String legacyStatus = clean(current.getString("moderationPreviousStatus"));
                if (legacyStatus.isEmpty()) legacyStatus = "INACTIVE";
                if (!target.equals(legacyStatus)) throw new IllegalStateException("PRODUCT_RESTORE_STATUS_MISMATCH");
            }
            if (section == Section.USERS && "ADMIN".equals(clean(current.getString("role")))) {
                throw new SecurityException("ADMIN_TARGET_FORBIDDEN");
            }
            List<DocumentSnapshot> restorableProductBatches = new ArrayList<>();
            if (section == Section.PRODUCTS && "HIDDEN".equals(before) && "ACTIVE".equals(target)) {
                for (String batchId : candidateBatchIds)
                    restorableProductBatches.add(tx.get(db.collection("productBatches").document(batchId)));
                boolean hasSellableBatch = false;
                Date now = new Date();
                for (DocumentSnapshot batch : restorableProductBatches) {
                    if (!batch.exists()) continue;
                    Date expiry = batch.getDate("expiryDate");
                    String batchStatus = clean(batch.getString("status"));
                    if (number(batch, "availableQuantity") > 0D
                            && (expiry == null || expiry.after(now))
                            && (batchStatus.equals("AVAILABLE") || batchStatus.equals("RESERVED_PARTIAL"))) {
                        hasSellableBatch = true; break;
                    }
                }
                if (!hasSellableBatch) throw new IllegalStateException("PRODUCT_NO_SELLABLE_BATCH");
            }
            List<DocumentSnapshot> campaignBatches = new ArrayList<>();
            if (section == Section.CAMPAIGNS && "STOPPED".equals(target)) {
                Object rawTargets = current.get("batchTargets");
                if (rawTargets instanceof Map<?, ?>) {
                    for (Object rawId : ((Map<?, ?>) rawTargets).keySet()) {
                        String batchId = trim(String.valueOf(rawId));
                        if (!batchId.isEmpty()) {
                            DocumentSnapshot batch = tx.get(db.collection("productBatches").document(batchId));
                            if (!batch.exists()) throw new IllegalStateException("CAMPAIGN_BATCH_NOT_FOUND");
                            String linkedCampaign = text(batch, "activeCampaignId");
                            if (!linkedCampaign.isEmpty() && !id.equals(linkedCampaign))
                                throw new IllegalStateException("CAMPAIGN_BATCH_LINK_CONFLICT");
                            campaignBatches.add(batch);
                        }
                    }
                }
            }
            DocumentSnapshot reviewProduct = section == Section.REVIEWS
                ? tx.get(db.collection("products").document(current.getString("productId"))) : null;
            Map<String, Object> changes = moderation(adminId, note);
            changes.put("status", target);
            if (section == Section.PRODUCTS && "HIDDEN".equals(target))
                changes.put("moderationPreviousStatus", before);
            else if (section == Section.PRODUCTS && "HIDDEN".equals(before))
                changes.put("moderationPreviousStatus", FieldValue.delete());
            if (section == Section.REPORTS) {
                changes.put("resolvedBy", adminId);
                changes.put("resolutionNote", note);
                changes.remove("moderatedBy"); changes.remove("moderatedAt"); changes.remove("moderationReason");
            }
            if (section == Section.SELLER_APPLICATIONS) {
                changes.put("rejectionReason", target.equals("REJECTED") ? note : "");
                String userId = clean(current.getString("sellerId"));
                if (userId.isEmpty()) userId = clean(current.getString("id"));
                if (userId.isEmpty()) userId = id;
                DocumentReference userRef = db.collection(USERS).document(userId);
                DocumentSnapshot seller = tx.get(userRef);
                if (!seller.exists()) {
                    throw new IllegalStateException("SELLER_NOT_FOUND");
                }
                String role = clean(seller.getString("role"));
                if (!role.isEmpty() && !"SELLER".equals(role)) {
                    throw new IllegalStateException("SELLER_NOT_FOUND");
                }
                Map<String, Object> sellerChanges = moderation(adminId, note);
                sellerChanges.put("sellerStatus", target);
                tx.update(userRef, sellerChanges);
            }
            tx.update(ref, changes);
            for (DocumentSnapshot batch : campaignBatches) {
                if (!id.equals(text(batch, "activeCampaignId"))) continue;
                long version = batch.getLong("inventoryVersion") == null
                        ? 0L : batch.getLong("inventoryVersion");
                tx.update(batch.getReference(), "activeCampaignId", null,
                        "inventoryVersion", version + 1L, "updatedAt", FieldValue.serverTimestamp());
                tx.set(db.collection(AUDIT).document(), auditMap(adminId, "PRODUCT_BATCHES",
                        batch.getId(), id, "UNLINKED_FROM_STOPPED_CAMPAIGN", note));
            }
            if (reviewProduct != null) FirebaseReviewSummary.update(tx, reviewProduct, current, id,
                "PUBLISHED".equals(target) ? (int)number(current,"rating") : 0);
            tx.set(audit, auditMap(adminId, section, id, before, target, note));
            return true;
        }).addOnSuccessListener(value -> {
            callback.onSuccess();

        })
                .addOnFailureListener(error -> fail(error, callback));
    }


    private void ensureAdmin(Runnable action, Object callback) {
        if (auth.getCurrentUser() == null) {
            error(callback, ErrorCode.UNAUTHENTICATED, "Cần đăng nhập tài khoản admin."); return;
        }
        db.collection(USERS).document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists() || !"ADMIN".equals(clean(document.getString("role")))
                            || !"ACTIVE".equals(clean(document.getString("status")))) {
                        error(callback, ErrorCode.FORBIDDEN, "Chức năng chỉ dành cho ADMIN."); return;
                    }
                    action.run();
                }).addOnFailureListener(error -> fail(error, callback));
    }

    private AdminListItem mapItem(Section section, DocumentSnapshot document) {
        String title, subtitle, status = clean(document.getString("status"));
        String imageUrl = "";
        boolean active = Boolean.TRUE.equals(document.getBoolean("active"));
        switch (section) {
            case SELLER_APPLICATIONS:
                title = first(document, "shopName", "representativeName", "sellerId"); subtitle = text(document, "sellerId"); break;
            case USERS:
                title = first(document, "fullName", "email", "id"); subtitle = first(document, "email", "role"); break;
            case POSTS: case PRODUCTS: case CAMPAIGNS:
                title = first(document, "title", "name", "id"); subtitle = text(document, "sellerId"); break;
            case CATEGORIES:
                title = first(document, "name", "id"); subtitle = "Category"; imageUrl = text(document, "imageUrl"); status = active ? "ACTIVE" : "INACTIVE"; break;
            case BANNERS:
                title = first(document, "title", "id"); subtitle = text(document, "campaignId"); imageUrl = text(document, "imageUrl"); status = active ? "ACTIVE" : "INACTIVE"; break;
            case REVIEWS:
                title = "★ " + (long) number(document, "rating") + " — " + text(document, "productId"); subtitle = text(document, "content"); break;
            case REPORTS:
                title = first(document, "reason", "targetId", "id"); subtitle = text(document, "targetType") + " • " + text(document, "targetId"); break;
            case ORDERS:
                title = first(document, "orderCode", "id"); subtitle = text(document, "sellerId") + " • " + (long) number(document, "totalAmount") + " ₫"; break;
            default:
                title = document.getId(); subtitle = "";
        }
        return new AdminListItem(document.getId(), title, subtitle, status, active, imageUrl);
    }

    private static Section[] dashboardSections() {
        return new Section[]{Section.SELLER_APPLICATIONS, Section.USERS, Section.POSTS,
                Section.PRODUCTS, Section.CAMPAIGNS, Section.CATEGORIES, Section.BANNERS,
                Section.REVIEWS, Section.REPORTS, Section.ORDERS};
    }

    private static Map<String, Object> moderation(String adminId, String reason) {
        Map<String, Object> map = new HashMap<>();
        map.put("moderatedBy", adminId); map.put("moderatedAt", FieldValue.serverTimestamp());
        map.put("moderationReason", reason); map.put("updatedAt", FieldValue.serverTimestamp());
        return map;
    }

    private static Map<String, Object> auditMap(String adminId, Section section, String resourceId,
            String before, String after, String reason) {
        return auditMap(adminId, section.name(), resourceId, before, after, reason);
    }

    private static Map<String, Object> auditMap(String adminId, String resourceType, String resourceId,
            String before, String after, String reason) {
        Map<String, Object> map = new HashMap<>();
        map.put("adminId", adminId); map.put("resourceType", resourceType);
        map.put("resourceId", resourceId); map.put("fromState", before);
        map.put("toState", after); map.put("reason", reason);
        map.put("createdAt", FieldValue.serverTimestamp()); return map;
    }

    private boolean online(Object callback) {
        if (network.isOnline()) return true;
        error(callback, ErrorCode.NETWORK, "Cần kết nối mạng để quản trị."); return false;
    }

    private void fail(Exception exception, Object callback) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (exception instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) exception).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            error(callback, ErrorCode.FORBIDDEN, "Firestore Rules từ chối quyền ADMIN.");
        } else if (message.contains("NOT_FOUND")) {
            error(callback, ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu cần quản trị.");
        } else if (message.contains("TRANSITION") || message.contains("FORBIDDEN")
                || message.contains("MISMATCH") || message.contains("CONFLICT")) {
            error(callback, ErrorCode.CONFLICT, "Trạng thái đã thay đổi hoặc thao tác không hợp lệ.");
        } else if (message.contains("PRODUCT_NO_SELLABLE_BATCH")) {
            error(callback, ErrorCode.VALIDATION, "Không thể khôi phục ACTIVE khi Product không còn batch bán được.");
        } else if (exception instanceof IllegalArgumentException) {
            error(callback, ErrorCode.VALIDATION, exception.getMessage());
        } else error(callback, ErrorCode.UNKNOWN, "Không thể hoàn tất thao tác quản trị.");
    }

    private void error(Object callback, ErrorCode code, String message) {
        if (callback instanceof DashboardCallback) ((DashboardCallback) callback).onError(code, message);
        else if (callback instanceof ListCallback) ((ListCallback) callback).onError(code, message);
        else if (callback instanceof PageCallback) ((PageCallback) callback).onError(code, message);
        else if (callback instanceof DetailCallback) ((DetailCallback) callback).onError(code, message);
        else if (callback instanceof ActionCallback) ((ActionCallback) callback).onError(code, message);
    }

    private static String first(DocumentSnapshot document, String... fields) {
        for (String field : fields) { String value = text(document, field); if (!value.isEmpty()) return value; }
        return document.getId();
    }
    private static String text(DocumentSnapshot document, String field) {
        Object value = document.get(field); return value == null ? "" : String.valueOf(value).trim();
    }
    private static double number(DocumentSnapshot document, String field) {
        Object value = document.get(field); return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }
    private static String clean(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
