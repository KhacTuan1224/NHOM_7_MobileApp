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


    private final List<com.google.firebase.firestore.ListenerRegistration> liveListeners = new ArrayList<>();
    private int liveGeneration;
    private final Map<String,org.json.JSONObject> authorCache=new HashMap<>();
    private final java.util.Set<String> authorRequests=new java.util.HashSet<>();
    @Override public void load(String campaignId, String userId, Callback callback) {
        close(); int generation = liveGeneration;
        DocumentReference campaign = db.collection(CAMPAIGNS).document(campaignId);
        final com.google.firebase.firestore.QuerySnapshot[] snapshots = new com.google.firebase.firestore.QuerySnapshot[2];
        final Runnable[] publish = new Runnable[1];
        publish[0] = () -> {
            if (generation != liveGeneration || snapshots[0] == null || snapshots[1] == null) return;
            EnumMap<ReactionType, Integer> counts = new EnumMap<>(ReactionType.class);
            ReactionType mine = null;
            for (DocumentSnapshot row : snapshots[0].getDocuments()) {
                ReactionType type = enumValue(row.getString("type")); if (type == null) continue;
                counts.put(type, counts.getOrDefault(type, 0) + 1);
                if (!empty(userId) && userId.equals(row.getString("userId"))) mine = type;
            }
            List<Comment> comments = new ArrayList<>();
            org.json.JSONArray missingAuthors=new org.json.JSONArray();
            for (DocumentSnapshot row : snapshots[1].getDocuments()) {
                try {
                    CommentStatus status = CommentStatus.valueOf(value(row.getString("status"), "ACTIVE"));
                    if (status == CommentStatus.HIDDEN || status == CommentStatus.DELETED) continue;
                    Comment comment=Comment.restore(row.getId(), campaignId, row.getString("userId"),
                            value(row.getString("content"), ""), status, date(row.get("createdAt")), date(row.get("updatedAt")));
                    org.json.JSONObject author=authorCache.get(campaignId+":"+row.getId());
                    List<String> images=new ArrayList<>();Object raw=row.get("imageUrls");
                    if(raw instanceof List<?>)for(Object item:(List<?>)raw)if(item instanceof String)images.add((String)item);
                    boolean anonymous=Boolean.TRUE.equals(row.getBoolean("anonymous"));
                    comment.setPresentation(row.contains("senderName")?row.getString("senderName"):author==null?"":author.optString("name"),
                        row.contains("senderAvatarUrl")?row.getString("senderAvatarUrl"):author==null?"":author.optString("avatarUrl"),anonymous,images);
                    if(!row.contains("senderName")&&author==null)missingAuthors.put(row.getId());
                    comments.add(comment);
                } catch (RuntimeException ignored) { }
            }
            callback.onSuccess(new CampaignEngagementSnapshot(counts, mine, comments));
            String key=campaignId+missingAuthors.toString();
            if(missingAuthors.length()>0&&authorRequests.add(key)) {
                org.json.JSONObject body=new org.json.JSONObject();
                try{body.put("campaignId",campaignId).put("commentIds",missingAuthors);}catch(org.json.JSONException ignored){}
                com.rescuefarm.service.backend.RenderBackendClient.postGuest("/campaigns/comments/presentation",body,
                    new com.rescuefarm.service.backend.RenderBackendClient.Callback(){
                        public void onSuccess(org.json.JSONObject result){if(generation!=liveGeneration)return;
                            org.json.JSONObject authors=result.optJSONObject("authors");if(authors==null)return;
                            java.util.Iterator<String> keys=authors.keys();while(keys.hasNext()){String id=keys.next();authorCache.put(campaignId+":"+id,authors.optJSONObject(id));}
                            publish[0].run();
                        }
                        public void onError(String message){/* Keep readable fallback, retry on explicit refresh. */}
                    });
            }
        };
        liveListeners.add(campaign.collection("reactions").addSnapshotListener((rows, error) -> {
            if (generation != liveGeneration) return;
            if (error != null) { callback.onError(message(error)); return; }
            snapshots[0] = rows; publish[0].run();
        }));
        liveListeners.add(campaign.collection("comments").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener((rows, error) -> {
                    if (generation != liveGeneration) return;
                    if (error != null) { callback.onError(message(error)); return; }
                    snapshots[1] = rows; publish[0].run();
                }));
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
        addComment(campaignId,userId,java.util.UUID.randomUUID().toString(),content,java.util.Collections.emptyList(),false,callback);
    }
    @Override public void addComment(String campaignId,String userId,String commentId,String content,List<String> images,boolean anonymous,Action callback) {
        if (!online(callback)) return;
        if (empty(userId)) { callback.onError("Đăng nhập để bình luận chiến dịch."); return; }
        try { Comment.validateContent(content); } catch (IllegalArgumentException e) { callback.onError(e.getMessage()); return; }
        org.json.JSONObject body=new org.json.JSONObject();
        try{body.put("campaignId",campaignId).put("commentId",commentId).put("content",content.trim())
            .put("imageUrls",new org.json.JSONArray(images)).put("anonymous",anonymous);}
        catch(org.json.JSONException error){callback.onError("Không tạo được bình luận.");return;}
        com.rescuefarm.service.backend.RenderBackendClient.post("/campaigns/comments/add",body,
            new com.rescuefarm.service.backend.RenderBackendClient.Callback(){
                public void onSuccess(org.json.JSONObject result){callback.onSuccess();}
                public void onError(String message){callback.onError(message);}
            });
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
        ref.update("content", "", "imageUrls",java.util.Collections.emptyList(), "status", "DELETED", "updatedAt", FieldValue.serverTimestamp())
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
    @Override public void close() { liveGeneration++; authorRequests.clear(); for (com.google.firebase.firestore.ListenerRegistration listener : liveListeners) listener.remove(); liveListeners.clear(); }
}
