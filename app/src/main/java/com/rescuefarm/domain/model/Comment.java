package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.CommentStatus;
import java.util.Date;

public class Comment {
    private String id; private String campaignId; private String userId; private String content;
    private CommentStatus status; private Date createdAt; private Date updatedAt;
    public Comment() { status = CommentStatus.ACTIVE; }
    public static Comment create(String id, String campaignId, String userId, String content, Date createdAt) {
        return restore(id, campaignId, userId, content, CommentStatus.ACTIVE, createdAt, createdAt);
    }
    public static Comment restore(String id, String campaignId, String userId, String content,
            CommentStatus status, Date createdAt, Date updatedAt) {
        if (clean(id).isEmpty() || clean(campaignId).isEmpty() || clean(userId).isEmpty()) {
            throw new IllegalArgumentException("Comment identity is required");
        }
        CommentStatus safeStatus = status == null ? CommentStatus.ACTIVE : status;
        if (safeStatus == CommentStatus.DELETED) {
            if (!clean(content).isEmpty()) throw new IllegalArgumentException("Deleted comment content must be empty");
        } else validateContent(content);
        Comment value = new Comment(); value.id = clean(id);
        value.campaignId = clean(campaignId); value.userId = clean(userId); value.content = clean(content);
        value.status = safeStatus;
        value.createdAt = copy(createdAt); value.updatedAt = copy(updatedAt); return value;
    }
    public void edit(String updatedContent, Date updateTime) {
        validateContent(updatedContent); content = clean(updatedContent);
        updatedAt = updateTime == null ? new Date() : copy(updateTime); status = CommentStatus.EDITED;
    }
    public void delete() { content = ""; status = CommentStatus.DELETED; updatedAt = new Date(); }
    public static void validateContent(String value) {
        int length = clean(value).length();
        if (length < 1 || length > 500) throw new IllegalArgumentException("Comment must contain 1-500 characters");
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
    public String getId() { return id; } public String getCampaignId() { return campaignId; }
    public String getUserId() { return userId; } public String getContent() { return content; }
    public CommentStatus getStatus() { return status; } public Date getCreatedAt() { return copy(createdAt); }
    public Date getUpdatedAt() { return copy(updatedAt); }
}
