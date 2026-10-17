package com.rescuefarm.data.remote.firebase;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.rescuefarm.data.local.dao.PostCacheDao;
import com.rescuefarm.data.local.database.RescueFarmDatabase;
import com.rescuefarm.data.local.entity.PostCacheEntity;
import com.rescuefarm.data.local.mapper.PostCacheMapper;
import com.rescuefarm.data.repository.PostRepository;
import com.rescuefarm.domain.enums.CommentStatus;
import com.rescuefarm.domain.enums.PostStatus;
import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.domain.enums.UrgencyLevel;
import com.rescuefarm.domain.model.Comment;
import com.rescuefarm.domain.model.Post;
import com.rescuefarm.service.feed.ReactionPolicy;
import com.rescuefarm.service.network.NetworkStatusProvider;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class FirebasePostRepository implements PostRepository {
    private static final String POSTS = "posts";
    private static final String REACTIONS = "reactions";
    private static final String COMMENTS = "comments";
    private static final int MAX_PAGE_SIZE = 30;
    private static final int COMMENT_LIMIT = 100;
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final RescueFarmDatabase database; private final PostCacheDao dao;
    private final ExecutorService cacheExecutor; private DocumentSnapshot lastPublishedDocument;
    private final Map<String, DocumentSnapshot> sellerPostCursors = new HashMap<>();
    private final NetworkStatusProvider networkStatusProvider;
    private final String viewerId;

    public FirebasePostRepository(RescueFarmDatabase database, ExecutorService cacheExecutor,
            NetworkStatusProvider networkStatusProvider, String viewerId) {
        this.database = database; this.dao = database.postCacheDao(); this.cacheExecutor = cacheExecutor;
        this.networkStatusProvider = networkStatusProvider;
        this.viewerId = viewerId;
    }
    @Override public LiveData<List<Post>> observePublishedPosts() {
        return Transformations.map(dao.observePublishedPosts(), PostCacheMapper::posts);
    }
    @Override public LiveData<List<Post>> observeSellerPosts(String sellerId) {
        return Transformations.map(dao.observeSellerPosts(sellerId), PostCacheMapper::posts);
    }
    @Override public LiveData<Post> observePost(String postId) {
        return new LiveData<Post>() {
            private ListenerRegistration registration;
            @Override protected void onActive() {
                registration = firestore.collection(POSTS).document(postId)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) return;
                            Post post = snapshot.exists() ? mapPost(snapshot) : null;
                            postValue(post);
                            if (post != null) cache(post);
                        });
            }
            @Override protected void onInactive() {
                if (registration != null) { registration.remove(); registration = null; }
            }
        };
    }
    @Override public void refreshFeed(boolean reset, int requestedPageSize, PageCallback callback) {
        if (!networkStatusProvider.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Đang offline; feed tiếp tục dùng Room cache."); return;
        }
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedPageSize));
        Query query = firestore.collection(POSTS)
                .whereEqualTo("status", PostStatus.PUBLISHED.name())
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(pageSize);
        if (!reset && lastPublishedDocument != null) query = query.startAfter(lastPublishedDocument);
        query.get().addOnSuccessListener(snapshot -> {
            List<PostCacheEntity> entities = mapPostEntities(snapshot.getDocuments());
            DocumentSnapshot nextCursor = snapshot.isEmpty() ? (reset ? null : lastPublishedDocument)
                    : snapshot.getDocuments().get(snapshot.size() - 1);
            cacheExecutor.execute(() -> {
                try {
                    database.runInTransaction(() -> {
                        if (reset) dao.clearPublishedPosts();
                        dao.replacePosts(entities);
                    });
                    lastPublishedDocument = nextCursor;
                    callback.onSuccess(snapshot.size() == pageSize);
                } catch (RuntimeException error) {
                    callback.onError(ErrorCode.UNKNOWN, "Không thể cập nhật post cache.");
                }
            });
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    @Override public void refreshSellerPosts(String sellerId, ActionCallback callback) {
        if (!networkStatusProvider.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Đang offline; danh sách post tiếp tục dùng cache."); return;
        }
        if (clean(sellerId).isEmpty()) { callback.onError(ErrorCode.UNAUTHENTICATED, "Vui lòng đăng nhập seller."); return; }
        firestore.collection(POSTS).whereEqualTo("sellerId", sellerId).get()
                .addOnSuccessListener(snapshot -> {
                    List<PostCacheEntity> entities = mapPostEntities(snapshot.getDocuments());
                    cacheExecutor.execute(() -> {
                        database.runInTransaction(() -> {
                            dao.clearSellerPosts(sellerId); dao.replacePosts(entities);
                        });
                        callback.onSuccess();
                    });
                }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    @Override public void refreshSellerPostPage(String sellerId, boolean reset, int requestedPageSize,
            PageCallback callback) {
        if (!networkStatusProvider.isOnline()) { callback.onError(ErrorCode.NETWORK, "Đang offline; bài đăng tiếp tục dùng cache."); return; }
        if (clean(sellerId).isEmpty()) { callback.onError(ErrorCode.UNAUTHENTICATED, "Vui lòng đăng nhập seller."); return; }
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedPageSize));
        Query query = firestore.collection(POSTS).whereEqualTo("sellerId", sellerId)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(pageSize);
        DocumentSnapshot cursor = sellerPostCursors.get(sellerId);
        if (!reset && cursor != null) query = query.startAfter(cursor);
        query.get().addOnSuccessListener(snapshot -> {
            List<PostCacheEntity> entities = mapPostEntities(snapshot.getDocuments());
            if (reset) sellerPostCursors.remove(sellerId);
            if (!snapshot.isEmpty()) sellerPostCursors.put(sellerId, snapshot.getDocuments().get(snapshot.size() - 1));
            cacheExecutor.execute(() -> {
                try {
                    database.runInTransaction(() -> {
                        if (reset) dao.clearSellerPosts(sellerId);
                        dao.replacePosts(entities);
                    });
                    callback.onSuccess(snapshot.size() == pageSize);
                } catch (RuntimeException error) {
                    callback.onError(ErrorCode.UNKNOWN, "Không thể cập nhật cache bài đăng Seller.");
                }
            });
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    @Override public void getPost(String postId, PostCallback callback) {
        if (!networkStatusProvider.isOnline()) { fallback(postId, callback); return; }
        firestore.collection(POSTS).document(postId).get().addOnSuccessListener(snapshot -> {
            Post value = snapshot.exists() ? mapPost(snapshot) : null;
            if (value == null) { fallback(postId, callback); return; }
            cache(value); callback.onSuccess(value);
        }).addOnFailureListener(error -> cacheExecutor.execute(() -> {
            PostCacheEntity cached = dao.findPost(postId);
            if (cached != null) try { callback.onSuccess(PostCacheMapper.toDomain(cached)); }
            catch (IllegalArgumentException invalidCache) { notifyFailure(error, callback::onError); }
            else notifyFailure(error, callback::onError);
        }));
    }
    @Override public void savePost(Post source, boolean submit, PostCallback callback) {
        if (!networkStatusProvider.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Cần kết nối mạng để lưu hoặc gửi duyệt post."); return;
        }
        if (source == null || clean(source.getSellerId()).isEmpty()) {
            callback.onError(ErrorCode.VALIDATION, "Post không hợp lệ."); return;
        }
        DocumentReference reference = clean(source.getId()).isEmpty()
                ? firestore.collection(POSTS).document() : firestore.collection(POSTS).document(source.getId());
        List<String> previousImageUrls = new ArrayList<>();
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(reference);
            previousImageUrls.clear();
            if (existing.exists()) {
                Object oldImages = existing.get("imageUrls");
                if (oldImages instanceof List<?>) for (Object value : (List<?>) oldImages)
                    if (value instanceof String) previousImageUrls.add((String) value);
            }
            if (existing.exists() && !source.getSellerId().equals(existing.getString("sellerId"))) {
                throw new IllegalStateException("FORBIDDEN");
            }
            PostStatus oldStatus = existing.exists() ? enumValue(PostStatus.class,
                    existing.getString("status"), PostStatus.DRAFT) : PostStatus.DRAFT;
            if (existing.exists() && oldStatus != PostStatus.DRAFT && oldStatus != PostStatus.REJECTED) {
                throw new IllegalStateException("POST_LOCKED");
            }
            Post saved = Post.restore(reference.getId(), source.getSellerId(), source.getCampaignId(),
                    source.getTitle(), source.getContent(), source.getImageUrls(),
                    source.getLinkedProductIds(), source.getUrgencyLevel(), PostStatus.DRAFT,
                    existing.exists() ? longValue(existing, "viewCount") : 0L,
                    existing.exists() ? date(existing, "createdAt") : new Date());
            if (submit) saved.submitForApproval();
            Map<String, Object> data = postMap(saved);
            data.put("createdAt", existing.exists() && existing.get("createdAt") != null
                    ? existing.get("createdAt") : FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp()); transaction.set(reference, data); return saved;
        }).addOnSuccessListener(saved -> {
            com.rescuefarm.service.media.CloudinaryAssetCleanup.deleteRemoved(
                    previousImageUrls, saved.getImageUrls());

            cache(saved); callback.onSuccess(saved);
        })
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    @Override public void loadEngagement(String postId, String currentUserId,
            EngagementCallback callback) {
        if (!networkStatusProvider.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Reaction và comment cần kết nối mạng."); return;
        }
        DocumentReference post = firestore.collection(POSTS).document(postId);
        post.collection(REACTIONS).get().addOnSuccessListener(reactionSnapshot -> {
            EnumMap<ReactionType, Integer> counts = new EnumMap<>(ReactionType.class);
            ReactionType mine = null;
            for (DocumentSnapshot value : reactionSnapshot.getDocuments()) {
                ReactionType type = enumValue(ReactionType.class, value.getString("type"), null);
                if (type == null) continue;
                counts.put(type, counts.getOrDefault(type, 0) + 1);
                if (!clean(currentUserId).isEmpty() && currentUserId.equals(value.getString("userId"))) mine = type;
            }
            ReactionType current = mine;
            post.collection(COMMENTS).orderBy("createdAt", Query.Direction.ASCENDING)
                    .limit(COMMENT_LIMIT).get().addOnSuccessListener(commentSnapshot -> {
                        List<Comment> comments = new ArrayList<>();
                        for (DocumentSnapshot value : commentSnapshot.getDocuments()) {
                            Comment comment = mapComment(postId, value); if (comment != null) comments.add(comment);
                        }
                        callback.onSuccess(new Engagement(counts, current, comments));
                    }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
        }).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    @Override public void toggleReaction(String postId, String userId, ReactionType type,
            ActionCallback callback) {
        if (!networkStatusProvider.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Cần kết nối mạng để cập nhật reaction."); return;
        }
        if (clean(userId).isEmpty()) { callback.onError(ErrorCode.UNAUTHENTICATED, "Đăng nhập để tương tác."); return; }
        if (type == null) { callback.onError(ErrorCode.VALIDATION, "Reaction không hợp lệ."); return; }
        DocumentReference reference = firestore.collection(POSTS).document(postId)
                .collection(REACTIONS).document(ReactionPolicy.documentIdForUser(userId));
        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(reference);
            ReactionType existingType = existing.exists() ? enumValue(ReactionType.class,
                    existing.getString("type"), null) : null;
            if (ReactionPolicy.shouldRemove(existingType, type)) transaction.delete(reference);
            else {
                Map<String, Object> data = new HashMap<>(); data.put("id", userId);
                data.put("postId", postId); data.put("userId", userId); data.put("type", type.name());
                data.put("createdAt", FieldValue.serverTimestamp()); transaction.set(reference, data);
            }
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    @Override public void addComment(String postId, String userId, String content,
            ActionCallback callback) {
        if (!networkStatusProvider.isOnline()) {
            callback.onError(ErrorCode.NETWORK, "Cần kết nối mạng để gửi bình luận."); return;
        }
        if (clean(userId).isEmpty()) { callback.onError(ErrorCode.UNAUTHENTICATED, "Đăng nhập để bình luận."); return; }
        try { Comment.validateContent(content); } catch (IllegalArgumentException error) {
            callback.onError(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        DocumentReference reference = firestore.collection(POSTS).document(postId)
                .collection(COMMENTS).document();
        Map<String, Object> data = new HashMap<>(); data.put("id", reference.getId());
        data.put("postId", postId); data.put("userId", userId); data.put("content", clean(content));
        data.put("status", CommentStatus.ACTIVE.name()); data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp()); reference.set(data)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }
    private List<PostCacheEntity> mapPostEntities(List<DocumentSnapshot> documents) {
        List<PostCacheEntity> result = new ArrayList<>(); long now = System.currentTimeMillis();
        for (DocumentSnapshot document : documents) {
            Post value = mapPost(document); if (value != null) result.add(PostCacheMapper.toEntity(value, now));
        }
        return result;
    }

    @Override public void editComment(String postId, String commentId, String userId,
            String content, ActionCallback callback) {
        try { Comment.validateContent(content); }
        catch (IllegalArgumentException error) { callback.onError(ErrorCode.VALIDATION, error.getMessage()); return; }
        mutateComment(postId, commentId, userId, content.trim(), CommentStatus.EDITED, callback);
    }

    @Override public void deleteComment(String postId, String commentId, String userId, ActionCallback callback) {
        mutateComment(postId, commentId, userId, "", CommentStatus.DELETED, callback);
    }

    private void mutateComment(String postId, String commentId, String userId, String content,
            CommentStatus status, ActionCallback callback) {
        if (!networkStatusProvider.isOnline()) { callback.onError(ErrorCode.NETWORK, "Cần kết nối mạng để sửa bình luận."); return; }
        DocumentReference reference = firestore.collection(POSTS).document(postId).collection(COMMENTS).document(commentId);
        firestore.runTransaction(tx -> {
            DocumentSnapshot post = tx.get(firestore.collection(POSTS).document(postId));
            DocumentSnapshot comment = tx.get(reference);
            if (!post.exists() || !PostStatus.PUBLISHED.name().equals(post.getString("status"))) throw new IllegalStateException("POST_LOCKED");
            if (!comment.exists()) throw new IllegalStateException("NOT_FOUND");
            if (!clean(userId).equals(comment.getString("userId")) || clean(userId).isEmpty()) throw new IllegalStateException("FORBIDDEN");
            if (!"ACTIVE".equals(comment.getString("status")) && !"EDITED".equals(comment.getString("status"))) throw new IllegalStateException("COMMENT_LOCKED");
            tx.update(reference, "content", content, "status", status.name(), "updatedAt", FieldValue.serverTimestamp());
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess()).addOnFailureListener(error -> notifyFailure(error, callback::onError));
    }

    @Override public void recordView(String postId, ActionCallback callback) {
        if (!networkStatusProvider.isOnline()) { callback.onError(ErrorCode.NETWORK, "Đang offline."); return; }
        try {
            org.json.JSONObject request = new org.json.JSONObject().put("postId", postId).put("viewerId", viewerId);
            com.rescuefarm.service.backend.RenderBackendClient.postGuest("/recordPostView", request,
                    new com.rescuefarm.service.backend.RenderBackendClient.Callback() {
                public void onSuccess(org.json.JSONObject response) { callback.onSuccess(); }
                public void onError(String message) { callback.onError(ErrorCode.NETWORK, message); }
            });
        } catch (org.json.JSONException invalid) { callback.onError(ErrorCode.VALIDATION, "Mã bài viết không hợp lệ."); }
    }
    private Post mapPost(DocumentSnapshot value) {
        try {
            return Post.restore(value.getId(), value.getString("sellerId"), value.getString("campaignId"),
                    value.getString("title"), value.getString("content"), strings(value.get("imageUrls")),
                    strings(value.get("linkedProductIds")), enumValue(UrgencyLevel.class,
                            value.getString("urgencyLevel"), UrgencyLevel.NORMAL),
                    enumValue(PostStatus.class, value.getString("status"), PostStatus.DRAFT),
                    longValue(value, "viewCount"), date(value, "createdAt"));
        } catch (RuntimeException error) { return null; }
    }
    private Comment mapComment(String postId, DocumentSnapshot value) {
        try {
            CommentStatus status = enumValue(CommentStatus.class, value.getString("status"), CommentStatus.ACTIVE);
            if (status == CommentStatus.DELETED || status == CommentStatus.HIDDEN) return null;
            return Comment.restore(value.getId(), postId, value.getString("userId"), value.getString("content"),
                    status, date(value, "createdAt"), date(value, "updatedAt"));
        } catch (RuntimeException error) { return null; }
    }
    private Map<String, Object> postMap(Post value) {
        Map<String, Object> data = new HashMap<>(); data.put("id", value.getId());
        data.put("sellerId", value.getSellerId()); data.put("campaignId", value.getCampaignId());
        data.put("title", value.getTitle()); data.put("content", value.getContent());
        data.put("imageUrls", value.getImageUrls()); data.put("linkedProductIds", value.getLinkedProductIds());
        data.put("urgencyLevel", value.getUrgencyLevel().name()); data.put("status", value.getStatus().name());
        data.put("viewCount", value.getViewCount()); return data;
    }
    private void fallback(String id, PostCallback callback) {
        cacheExecutor.execute(() -> {
            PostCacheEntity cached = dao.findPost(id);
            if (cached == null) callback.onError(ErrorCode.NOT_FOUND, "Không tìm thấy post.");
            else try { callback.onSuccess(PostCacheMapper.toDomain(cached)); }
            catch (IllegalArgumentException error) { callback.onError(ErrorCode.NOT_FOUND, "Cache post không hợp lệ."); }
        });
    }
    private void cache(Post value) {
        cacheExecutor.execute(() -> dao.replacePost(PostCacheMapper.toEntity(value, System.currentTimeMillis())));
    }
    private void notifyFailure(Exception error, ErrorConsumer consumer) {
        String code = error.getMessage();
        if ("FORBIDDEN".equals(code)) { consumer.accept(ErrorCode.FORBIDDEN, "Bạn không sở hữu post này."); return; }
        if ("POST_LOCKED".equals(code)) { consumer.accept(ErrorCode.CONFLICT, "Post đang chờ duyệt hoặc đã xuất bản."); return; }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            consumer.accept(ErrorCode.VALIDATION, error.getMessage()); return;
        }
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code value = ((FirebaseFirestoreException) error).getCode();
            if (value == FirebaseFirestoreException.Code.PERMISSION_DENIED) { consumer.accept(ErrorCode.FORBIDDEN, "Không có quyền thực hiện thao tác feed."); return; }
            if (value == FirebaseFirestoreException.Code.UNAVAILABLE) { consumer.accept(ErrorCode.NETWORK, "Mất kết nối; dữ liệu post cache vẫn được giữ."); return; }
            if (value == FirebaseFirestoreException.Code.ABORTED) { consumer.accept(ErrorCode.CONFLICT, "Dữ liệu vừa thay đổi. Hãy thử lại."); return; }
        }
        consumer.accept(ErrorCode.UNKNOWN, "Không thể xử lý post.");
    }
    private static List<String> strings(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?>) for (Object value : (List<?>) raw) if (value instanceof String) result.add((String) value);
        return result;
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
}
