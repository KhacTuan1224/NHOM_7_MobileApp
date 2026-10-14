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
                            response.optDouble("completionRate")));
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
        if (section == null || section == Section.ANALYTICS || section.getCollection().isEmpty()) {
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
        if (!online(callback)) return;
        if (section == null || section == Section.ANALYTICS || section.getCollection().isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Khu vực quản trị không hợp lệ."); return;
        }
        ensureAdmin(() -> {
            com.google.firebase.firestore.Query query = db.collection(section.getCollection());
            String filter = clean(status);
            if (!filter.isEmpty()) {
                if (section == Section.CATEGORIES || section == Section.BANNERS)
                    query = query.whereEqualTo("active", "ACTIVE".equals(filter));
                else if (section == Section.ORDERS && filter.startsWith("FULFILLMENT:"))
                    query = query.whereEqualTo("fulfillmentType", filter.substring("FULFILLMENT:".length()));
                else if (section == Section.ORDERS && filter.startsWith("PAYMENT:"))
                    query = query.whereEqualTo("paymentMethod", filter.substring("PAYMENT:".length()));
                else query = query.whereEqualTo("status", section == Section.ORDERS
                        && filter.startsWith("STATUS:") ? filter.substring("STATUS:".length()) : filter);
            }
            query = query.orderBy(com.google.firebase.firestore.FieldPath.documentId());
            if (!trim(cursor).isEmpty()) query = query.startAfter(trim(cursor));
            query.limit(Math.max(1, Math.min(100, limit))).get().addOnSuccessListener(snapshot -> {
                List<AdminListItem> result = new ArrayList<>();
                for (DocumentSnapshot document : snapshot.getDocuments()) result.add(mapItem(section, document));
                String next = snapshot.isEmpty() ? trim(cursor)
                        : snapshot.getDocuments().get(snapshot.size() - 1).getId();
                callback.onSuccess(result, next, snapshot.size() >= Math.max(1, Math.min(100, limit)));
            }).addOnFailureListener(error -> fail(error, callback));
        }, callback);
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
                    registration = db.collection(section.getCollection())
                            .limit(Math.max(1, Math.min(100, limit)))
                            .addSnapshotListener((snapshot, error) -> {
                                if (error != null || snapshot == null) return;
                                List<AdminListItem> result = new ArrayList<>();
                                for (DocumentSnapshot document : snapshot.getDocuments())
                                    result.add(mapItem(section, document));
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
        ensureAdmin(() -> runTransition(section, id, target, note, callback), callback);
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
                tx.update(ref, changes);
                tx.set(audit, auditMap(adminId, section, id,
                        previous.isEmpty() ? "NO_IMAGE" : "IMAGE_SET", "IMAGE_SET", "Updated Cloudinary image"));
                return true;
            }).addOnSuccessListener(value -> callback.onSuccess())
                    .addOnFailureListener(error -> fail(error, callback));
        }, callback);
    }

    @Override public void loadDetail(Section section, String resourceId, DetailCallback callback) {
        if (!online(callback)) return;
        String id = trim(resourceId);
        if (section == null || id.isEmpty() || section.getCollection().isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Thiếu dữ liệu chi tiết cần xem."); return;
        }
        ensureAdmin(() -> db.collection(section.getCollection()).document(id).get()
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
                                db.collection("shipments").document(id).get())
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
                                    if (payment.exists()) detail.put("payment", payment.getData());
                                    if (shipment.exists()) detail.put("shipment", shipment.getData());
                                    callback.onSuccess(detail);
                                }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.PRODUCTS) {
                        db.collection("productBatches").whereEqualTo("productId", id).get()
                                .addOnSuccessListener(snapshot -> {
                                    detail.put("batches", snapshotMaps(snapshot)); callback.onSuccess(detail);
                                }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.CAMPAIGNS) {
                        Object rawTargets = detail.get("batchTargets");
                        List<Task<DocumentSnapshot>> batchTasks = new ArrayList<>();
                        if (rawTargets instanceof Map<?, ?>) for (Object batchId : ((Map<?, ?>) rawTargets).keySet())
                            batchTasks.add(db.collection("productBatches").document(String.valueOf(batchId)).get());
                        if (batchTasks.isEmpty()) callback.onSuccess(detail);
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
                            detail.put("batches", batches); callback.onSuccess(detail);
                        }).addOnFailureListener(error -> fail(error, callback));
                    } else if (section == Section.REPORTS) {
                        loadReportedTarget(detail, callback);
                    } else callback.onSuccess(detail);
                }).addOnFailureListener(error -> fail(error, callback)), callback);
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
            db.collectionGroup("comments").whereEqualTo("id", targetId).limit(1).get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) detail.put("target", snapshot.getDocuments().get(0).getData());
                        callback.onSuccess(detail);
                    }).addOnFailureListener(error -> fail(error, callback));
        } else if (collection.isEmpty()) callback.onSuccess(detail);
        else db.collection(collection).document(targetId).get().addOnSuccessListener(target -> {
            if (target.exists()) {
                Map<String, Object> targetData = new HashMap<>(target.getData() == null
                        ? java.util.Collections.emptyMap() : target.getData());
                targetData.put("id", target.getId()); detail.put("target", targetData);
            }
            callback.onSuccess(detail);
        }).addOnFailureListener(error -> fail(error, callback));
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
                db.collectionGroup("comments").whereEqualTo("id", targetId).limit(1).get()
                        .addOnSuccessListener(matches -> {
                            if (matches.isEmpty()) { callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy bình luận."); return; }
                            runReportResolution(id, action, note, matches.getDocuments().get(0).getReference(), callback);
                        }).addOnFailureListener(error -> fail(error, callback));
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
                tx.update(targetRef, changes);
                tx.set(db.collection(AUDIT).document(), auditMap(adminId,
                        sectionForCollection(targetCollection), target.getId(), targetStatus,
                        String.valueOf(changes.get("status")), "Report " + reportId + ": " + note));
                if (reviewProduct != null) FirebaseReviewSummary.update(tx, reviewProduct, target,
                        target.getId(), 0);
                for (DocumentSnapshot batch : batches) {
                    long version = batch.getLong("inventoryVersion") == null ? 0L : batch.getLong("inventoryVersion");
                    tx.update(batch.getReference(), "activeCampaignId", null,
                            "inventoryVersion", version + 1L, "updatedAt", FieldValue.serverTimestamp());
                    tx.set(db.collection(AUDIT).document(), auditMap(adminId, "PRODUCT_BATCHES", batch.getId(),
                            target.getId(), "UNLINKED_FROM_STOPPED_CAMPAIGN", "Report " + reportId));
                }
            } else if (action == ReportAction.BLOCK_USER) {
                Map<String, Object> changes = moderation(adminId, note); changes.put("status", "BLOCKED");
                tx.update(targetRef, changes);
                tx.set(db.collection(AUDIT).document(), auditMap(adminId, "USERS", targetRef.getId(),
                        clean(user.getString("status")), "BLOCKED", "Report " + reportId + ": " + note));
            } else if (action == ReportAction.MARK_IN_REVIEW || action == ReportAction.DISMISS) {
                // The report transition itself is the auditable moderation action.
            }
            tx.set(db.collection(AUDIT).document(), auditMap(adminId, "REPORTS", reportId,
                    before, after, note));
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
            ActionCallback callback) {
        String adminId = auth.getCurrentUser().getUid();
        DocumentReference ref = db.collection(section.getCollection()).document(id);
        DocumentReference audit = db.collection(AUDIT).document();
        db.runTransaction(tx -> {
            DocumentSnapshot current = tx.get(ref);
            if (!current.exists()) throw new IllegalStateException("RESOURCE_NOT_FOUND");
            String before = clean(current.getString("status"));
            policy.validate(section, before, target, note);
            if (section == Section.USERS && "ADMIN".equals(clean(current.getString("role")))) {
                throw new SecurityException("ADMIN_TARGET_FORBIDDEN");
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
            if (section == Section.REPORTS) {
                changes.put("resolvedBy", adminId);
                changes.put("resolutionNote", note);
                changes.remove("moderatedBy"); changes.remove("moderatedAt"); changes.remove("moderationReason");
            }
            if (section == Section.SELLER_APPLICATIONS) {
                changes.put("rejectionReason", target.equals("REJECTED") ? note : "");
                DocumentReference userRef = db.collection(USERS).document(id);
                DocumentSnapshot seller = tx.get(userRef);
                if (!seller.exists() || !"SELLER".equals(clean(seller.getString("role")))) {
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
            if (section == Section.CAMPAIGNS && "ACTIVE".equals(target)) notifyCampaignCustomers(id);
        })
                .addOnFailureListener(error -> fail(error, callback));
    }

    private void notifyCampaignCustomers(String campaignId) {
        JSONObject request = new JSONObject();
        try { request.put("type", "CAMPAIGN"); request.put("referenceId", campaignId); }
        catch (org.json.JSONException ignored) { return; }
        RenderBackendClient.post("/send-notification", request, new RenderBackendClient.Callback() {
            @Override public void onSuccess(JSONObject response) { }
            @Override public void onError(String message) { }
        });
    }

    private void ensureAdmin(Runnable action, Object callback) {
        if (auth.getCurrentUser() == null) {
            error(callback, ErrorCode.UNAUTHENTICATED, "Cần đăng nhập tài khoản admin."); return;
        }
        db.collection(USERS).document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists() || !"ADMIN".equals(clean(document.getString("role")))) {
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
        } else if (message.contains("TRANSITION") || message.contains("FORBIDDEN")) {
            error(callback, ErrorCode.CONFLICT, "Trạng thái đã thay đổi hoặc thao tác không hợp lệ.");
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
