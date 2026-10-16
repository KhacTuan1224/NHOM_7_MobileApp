package com.rescuefarm.domain.model;

import com.rescuefarm.domain.enums.ReactionType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Aggregated reactions and recent comments attached to one active Campaign. */
public final class CampaignEngagementSnapshot {
    private final Map<ReactionType, Integer> counts;
    private final ReactionType currentUserReaction;
    private final List<Comment> comments;
    public CampaignEngagementSnapshot(Map<ReactionType, Integer> counts,
            ReactionType currentUserReaction, List<Comment> comments) {
        EnumMap<ReactionType, Integer> copy = new EnumMap<>(ReactionType.class);
        if (counts != null) copy.putAll(counts);
        this.counts = Collections.unmodifiableMap(copy);
        this.currentUserReaction = currentUserReaction;
        this.comments = Collections.unmodifiableList(comments == null
                ? Collections.emptyList() : new java.util.ArrayList<>(comments));
    }
    public Map<ReactionType, Integer> getReactionCounts() { return counts; }
    public ReactionType getCurrentUserReaction() { return currentUserReaction; }
    public List<Comment> getComments() { return comments; }
}
