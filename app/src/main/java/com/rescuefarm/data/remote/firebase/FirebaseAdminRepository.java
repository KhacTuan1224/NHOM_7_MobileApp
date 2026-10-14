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
        ensureAdmin(() -> {
            List<Task<QuerySnapshot>> tasks = new ArrayList<>();
            Section[] sections = dashboardSections();
            for (Section section : sections) tasks.add(db.collection(section.getCollection()).get());
            Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                EnumMap<Section, Integer> totals = new EnumMap<>(Section.class);
                int pendingSellers = 0, pendingPosts = 0, pendingCampaigns = 0;
                int pendingReviews = 0, openReports = 0;
                double revenue = 0D;
                for (int i = 0; i < results.size(); i++) {
                    Section section = sections[i];
                    QuerySnapshot snapshot = (QuerySnapshot) results.get(i);
                    totals.put(section, snapshot.size());
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String status = clean(document.getString("status"));
                        if (section == Section.SELLER_APPLICATIONS && status.equals("PENDING")) pendingSellers++;
                        else if (section == Section.POSTS && status.equals("PENDING_APPROVAL")) pendingPosts++;
                        else if (section == Section.CAMPAIGNS && status.equals("PENDING_APPROVAL")) pendingCampaigns++;
                        else if (section == Section.REVIEWS && status.equals("PENDING")) pendingReviews++;
                        else if (section == Section.REPORTS && (status.equals("PENDING") || status.equals("IN_REVIEW"))) openReports++;
                        else if (section == Section.ORDERS && status.equals("DELIVERED")) revenue += number(document, "totalAmount");
                    }
                }
                callback.onSuccess(new AdminDashboardSnapshot(totals, pendingSellers, pendingPosts,
                        pendingCampaigns, pendingReviews, openReports, revenue));
            }).addOnFailureListener(error -> fail(error, callback));
        }, callback);
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
            if (reviewProduct != null) FirebaseReviewSummary.update(tx, reviewProduct, current, id,
                "PUBLISHED".equals(target) ? (int)number(current,"rating") : 0);
            tx.set(audit, auditMap(adminId, section, id, before, target, note));
            return true;
        }).addOnSuccessListener(value -> callback.onSuccess())
                .addOnFailureListener(error -> fail(error, callback));
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
        Map<String, Object> map = new HashMap<>();
        map.put("adminId", adminId); map.put("resourceType", section.name());
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
