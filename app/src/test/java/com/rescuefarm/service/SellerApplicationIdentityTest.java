package com.rescuefarm.service;

import com.rescuefarm.service.admin.SellerApplicationIdentity;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SellerApplicationIdentityTest {
    @Test public void preservesMixedCaseSellerUidForApprovalAndRejection() {
        assertEquals("pM9Bn98b00ZDBgP1YOoeH4B5T303", SellerApplicationIdentity.resolve(
                "pM9Bn98b00ZDBgP1YOoeH4B5T303", "legacy", "document"));
    }

    @Test public void legacyAndDocumentFallbacksAlsoPreserveCase() {
        assertEquals("OldUidAbC", SellerApplicationIdentity.resolve(null, " OldUidAbC ", "other"));
        assertEquals("DocUidXyZ", SellerApplicationIdentity.resolve(" ", null, "DocUidXyZ"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingIdentity() {
        SellerApplicationIdentity.resolve(null, " ", "");
    }
}
