package com.rescuefarm.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.rescuefarm.domain.enums.ReactionType;
import com.rescuefarm.service.feed.ReactionPolicy;
import org.junit.Test;

public class ReactionPolicyTest {
    @Test public void documentId_isUserId_soEachUserHasOnlyOneReactionPerCampaign() {
        assertEquals("user-1", ReactionPolicy.documentIdForUser(" user-1 "));
    }
    @Test public void sameTypeTogglesOff_differentTypeReplaces() {
        assertTrue(ReactionPolicy.shouldRemove(ReactionType.LIKE, ReactionType.LIKE));
        assertFalse(ReactionPolicy.shouldRemove(ReactionType.LIKE, ReactionType.CARE));
        assertFalse(ReactionPolicy.shouldRemove(null, ReactionType.SUPPORT));
    }
}
