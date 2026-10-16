package com.rescuefarm.data.remote.firebase;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.rescuefarm.data.repository.CampaignEngagementRepository;
import com.rescuefarm.domain.enums.CommentStatus;
import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.domain.model.Comment;
import com.rescuefarm.domain.model.CampaignEngagementSnapshot;
import com.rescuefarm.service.feed.ReactionPolicy;
import com.rescuefarm.service.network.NetworkStatusProvider;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Firestore-backed social actions owned by a Campaign document. */
public final class FirebaseCampaignEngagementRepository implements CampaignEngagementRepository {
    private static final String CAMPAIGNS = "campaigns";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final NetworkStatusProvider network;
    private final String viewerId;
    public FirebaseCampaignEngagementRepository(NetworkStatusProvider network, String viewerId) {
        this.network = network; this.viewerId = viewerId;
    }

    @Override public void load(String campaignId, String userId, Callback callback) {
        if (!network.isOnline()) { callback.onError("Reaction và bình luận cần kết nối mạng."); return; }
        DocumentReference campaign = db.collection(CAMPAIGNS).document(campaignId);
        campaign.collection("reactions").get().addOnSuccessListener(reactions -> {
            EnumMap<ReactionType, Integer> counts = new EnumMap<>(ReactionType.class);
            ReactionType mine = null;
            for (DocumentSnapshot row : reactions) {
                ReactionType type = enumValue(row.getString("type")); if (type == null) continue;
                counts.put(type, counts.getOrDefault(type, 0) + 1);
                if (!empty(userId) && userId.equals(row.getString("userId"))) mine = type;
            }
            ReactionType selected = mine;
            campaign.collection("comments").orderBy("createdAt", Query.Direction.ASCENDING).limit(100)
                    .get().addOnSuccessListener(rows -> {
                        List<Comment> comments = new ArrayList<>();
                        for (DocumentSnapshot row : rows) {
                            try {
                                CommentStatus status = CommentStatus.valueOf(value(row.getString("status"), "ACTIVE"));
                                String content = value(row.getString("content"), "");
                                if (status == CommentStatus.HIDDEN || status == CommentStatus.DELETED) continue;
                                comments.add(Comment.restore(row.getId(), campaignId, row.getString("userId"),
                                        content, status, date(row.get("createdAt")), date(row.get("updatedAt"))));
                            } catch (RuntimeException ignored) { }
                        }
                        callback.onSuccess(new CampaignEngagementSnapshot(counts, selected, comments));
                    }).addOnFailureListener(error -> callback.onError(message(error)));
        }).addOnFailureListener(error -> callback.onError(message(error)));
    }

    @Override public void toggleReaction(String campaignId, String userId, ReactionType type, Action callback) {
        if (!online(callback)) return;
        if (empty(userId) || type == null) { callback.onError("Đăng nhập để reaction chiến dịch."); return; }
        DocumentReference ref = db.collection(CAMPAIGNS).document(campaignId)
                .collection("reactions").document(userId);
        db.runTransaction(tx -> {
            DocumentSnapshot old = tx.get(ref);
            ReactionType existing = old.exists() ? enumValue(old.getString("type")) : null;
            if (ReactionPolicy.shouldRemove(existing, type)) tx.delete(ref);
            else {
                Map<String,Object> data = new HashMap<>(); data.put("id", userId);
                data.put("campaignId", campaignId); data.put("userId", userId);
                data.put("type", type.name()); data.put("createdAt", FieldValue.serverTimestamp());
                tx.set(ref, data);
            }
            return null;
        }).addOnSuccessListener(v -> callback.onSuccess()).addOnFailureListener(e -> callback.onError(message(e)));
    }

    @Override public void addComment(String campaignId, String userId, String content, Action callback) {
        if (!online(callback)) return;
        if (empty(userId)) { callback.onError("Đăng nhập để bình luận chiến dịch."); return; }
        try { Comment.validateContent(content); } catch (IllegalArgumentException e) { callback.onError(e.getMessage()); return; }
        DocumentReference ref = db.collection(CAMPAIGNS).document(campaignId).collection("comments").document();
        Map<String,Object> data = new HashMap<>(); data.put("id", ref.getId()); data.put("campaignId", campaignId);
        data.put("userId", userId); data.put("status", "ACTIVE"); data.put("content", content.trim());
        data.put("createdAt", FieldValue.serverTimestamp()); data.put("updatedAt", FieldValue.serverTimestamp());
        ref.set(data).addOnSuccessListener(v -> callback.onSuccess()).addOnFailureListener(e -> callback.onError(message(e)));
    }

    @Override public void editComment(String campaignId, String commentId, String userId, String content, Action callback) {
        if (!online(callback)) return;
        try { Comment.validateContent(content); } catch (IllegalArgumentException e) { callback.onError(e.getMessage()); return; }
        DocumentReference ref = db.collection(CAMPAIGNS).document(campaignId).collection("comments").document(commentId);
        ref.update("content", content.trim(), "status", "EDITED", "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(v -> callback.onSuccess()).addOnFailureListener(e -> callback.onError(message(e)));
    }

    @Override public void deleteComment(String campaignId, String commentId, String userId, Action callback) {
        if (!online(callback)) return;
        DocumentReference ref = db.collection(CAMPAIGNS).document(campaignId).collection("comments").document(commentId);
        ref.update("content", "", "status", "DELETED", "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(v -> callback.onSuccess()).addOnFailureListener(e -> callback.onError(message(e)));
    }
    @Override public void recordView(String campaignId, ViewCallback callback) {
        if (!network.isOnline()) { callback.onError("Lượt xem chiến dịch sẽ đồng bộ khi có mạng."); return; }
        org.json.JSONObject body = new org.json.JSONObject();
        try { body.put("campaignId", campaignId); body.put("viewerId", viewerId); }
        catch (org.json.JSONException error) { callback.onError("Không tạo được bản ghi lượt xem."); return; }
        com.rescuefarm.service.backend.RenderBackendClient.postGuest("/recordCampaignView", body,
                new com.rescuefarm.service.backend.RenderBackendClient.Callback() {
                    @Override public void onSuccess(org.json.JSONObject response) {
                        callback.onSuccess(Math.max(0L, response.optLong("viewCount", 0L)));
                    }
                    @Override public void onError(String message) { callback.onError(message); }
                });
    }
    private boolean online(Action callback) {
        if (network.isOnline()) return true;
        callback.onError("Tính năng này cần kết nối mạng."); return false;
    }
    private static ReactionType enumValue(String value) {
        try { return value == null ? null : ReactionType.valueOf(value); } catch (IllegalArgumentException e) { return null; }
    }
    private static Date date(Object raw) { return raw instanceof Timestamp ? ((Timestamp) raw).toDate() : new Date(); }
    private static String value(String value, String fallback) { return value == null ? fallback : value; }
    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private static String message(Exception e) { return e.getLocalizedMessage() == null ? "Không thể cập nhật tương tác." : e.getLocalizedMessage(); }
    @Override public void close() { }
}
