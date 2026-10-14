package com.rescuefarm.data.repository;

import androidx.lifecycle.LiveData;
import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.domain.model.Comment;
import com.rescuefarm.domain.model.Post;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public interface PostRepository {
    enum ErrorCode { NOT_CONFIGURED, NOT_FOUND, UNAUTHENTICATED, FORBIDDEN, VALIDATION, NETWORK, CONFLICT, UNKNOWN }
    interface ActionCallback { void onSuccess(); void onError(ErrorCode error, String message); }
    interface PageCallback { void onSuccess(boolean hasMore); void onError(ErrorCode error, String message); }
    interface PostCallback { void onSuccess(Post post); void onError(ErrorCode error, String message); }
    interface EngagementCallback { void onSuccess(Engagement value); void onError(ErrorCode error, String message); }

    final class Engagement {
        private final Map<ReactionType, Integer> reactionCounts;
        private final ReactionType currentUserReaction;
        private final List<Comment> comments;
        public Engagement(Map<ReactionType, Integer> reactionCounts,
                ReactionType currentUserReaction, List<Comment> comments) {
            EnumMap<ReactionType, Integer> counts = new EnumMap<>(ReactionType.class);
            counts.putAll(reactionCounts); this.reactionCounts = Collections.unmodifiableMap(counts);
            this.currentUserReaction = currentUserReaction;
            this.comments = Collections.unmodifiableList(comments);
        }
        public Map<ReactionType, Integer> getReactionCounts() { return reactionCounts; }
        public ReactionType getCurrentUserReaction() { return currentUserReaction; }
        public List<Comment> getComments() { return comments; }
        public int getTotalReactions() {
            int total = 0; for (int count : reactionCounts.values()) total += count; return total;
        }
    }

    LiveData<List<Post>> observePublishedPosts();
    LiveData<List<Post>> observeSellerPosts(String sellerId);
    default LiveData<Post> observePost(String postId) {
        return new androidx.lifecycle.MutableLiveData<>();
    }
    void refreshFeed(boolean reset, int pageSize, PageCallback callback);
    void refreshSellerPosts(String sellerId, ActionCallback callback);
    void getPost(String postId, PostCallback callback);
    void savePost(Post post, boolean submitForApproval, PostCallback callback);
    void loadEngagement(String postId, String currentUserId, EngagementCallback callback);
    void toggleReaction(String postId, String userId, ReactionType type, ActionCallback callback);
    void addComment(String postId, String userId, String content, ActionCallback callback);
    default void editComment(String postId, String commentId, String userId, String content, ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Cần kết nối mạng để sửa bình luận.");
    }
    default void deleteComment(String postId, String commentId, String userId, ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Cần kết nối mạng để xóa bình luận.");
    }
    default void recordView(String postId, ActionCallback callback) {
        callback.onError(ErrorCode.NOT_CONFIGURED, "Lượt xem được ghi nhận khi có kết nối mạng.");
    }
    default void close() { }
}
