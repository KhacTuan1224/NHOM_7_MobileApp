package com.rescuefarm.service;

import com.rescuefarm.service.engagement.ReviewSummary;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReviewSummaryTest {
    @Test public void editingChangesSumWithoutChangingCountAndWithdrawalResetsLastReview() {
        ReviewSummary created=ReviewSummary.change(0,0,0,5);
        ReviewSummary edited=ReviewSummary.change(created.count,created.sum,5,3);
        assertEquals(1,edited.count); assertEquals(3D,edited.average,0);
        ReviewSummary deleted=ReviewSummary.change(edited.count,edited.sum,3,0);
        assertEquals(0,deleted.count); assertEquals(0D,deleted.average,0);
    }
    @Test public void multipleReviewsUseFractionalAverage() {
        ReviewSummary summary=ReviewSummary.change(1,3,0,4);
        assertEquals(2,summary.count); assertEquals(3.5D,summary.average,0);
    }
    @Test(expected=IllegalArgumentException.class) public void inconsistentStoredSummaryIsRejected() {
        ReviewSummary.change(0,0,5,0);
    }
}
