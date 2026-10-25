package com.rescuefarm.data.repository;

import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.domain.model.CampaignEngagementSnapshot;

public interface CampaignEngagementRepository {
    interface Callback { void onSuccess(CampaignEngagementSnapshot value); void onError(String message); }
    interface Action { void onSuccess(); void onError(String message); }
    interface ViewCallback { void onSuccess(long viewCount); void onError(String message); }
    void load(String campaignId, String userId, Callback callback);
    void toggleReaction(String campaignId, String userId, ReactionType type, Action callback);
    void addComment(String campaignId, String userId, String content, Action callback);
    default void addComment(String campaignId,String userId,String commentId,String content,
            java.util.List<String> imageUrls,boolean anonymous,Action callback) {
        if(imageUrls.isEmpty()&&!anonymous)addComment(campaignId,userId,content,callback);
        else callback.onError("Chưa hỗ trợ ảnh hoặc ẩn danh trong môi trường này.");
    }
    void editComment(String campaignId, String commentId, String userId, String content, Action callback);
    void deleteComment(String campaignId, String commentId, String userId, Action callback);
    void recordView(String campaignId, ViewCallback callback);
    void close();
}
