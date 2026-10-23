package com.rescuefarm.service;

import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.data.repository.admin.AdminDashboardSnapshot;
import com.rescuefarm.domain.enums.UserRole;
import com.rescuefarm.service.admin.AdminTransitionPolicy;
import java.util.EnumMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdminTransitionPolicyTest {
    private final AdminTransitionPolicy policy = new AdminTransitionPolicy();

    @Test public void onlyAdminRolePassesRoleGate() {
        policy.requireAdmin(UserRole.ADMIN);
        assertThrows(SecurityException.class, () -> policy.requireAdmin(UserRole.SELLER));
        assertThrows(SecurityException.class, () -> policy.requireAdmin(UserRole.CUSTOMER));
    }

    @Test public void approvalTransitionsAreExplicit() {
        assertTrue(policy.isAllowed(Section.SELLER_APPLICATIONS, "PENDING", "APPROVED"));
        assertTrue(policy.isAllowed(Section.CAMPAIGNS, "PENDING_APPROVAL", "ACTIVE"));
        assertFalse(policy.isAllowed(Section.CAMPAIGNS, "DRAFT", "ACTIVE"));
        assertFalse(policy.isAllowed(Section.SELLER_APPLICATIONS, "REJECTED", "APPROVED"));
    }

    @Test public void restrictiveTransitionsRequireAuditReason() {
        Section[] sections = {Section.PRODUCTS, Section.REVIEWS, Section.CAMPAIGNS,
                Section.USERS, Section.SELLER_APPLICATIONS};
        String[] current = {"ACTIVE", "PUBLISHED", "ACTIVE", "ACTIVE", "PENDING"};
        String[] targets = {"HIDDEN", "HIDDEN", "STOPPED", "BLOCKED", "REJECTED"};
        for (int i = 0; i < targets.length; i++) {
            final int index = i;
            assertTrue(policy.requiresReason(targets[i]));
            for (String reason : new String[]{null, "", "   ", "ab", "  ab  "}) {
                assertThrows(IllegalArgumentException.class,
                        () -> policy.validate(sections[index], current[index], targets[index], reason));
            }
            policy.validate(sections[i], current[i], targets[i], "Lý do kiểm duyệt hợp lệ");
            policy.validate(sections[i], current[i], targets[i], " abc ");
        }
        assertTrue(policy.requiresReason(" hidden "));
        assertFalse(policy.requiresReason("ACTIVE"));
        assertFalse(policy.requiresReason("APPROVED"));
        policy.validate(Section.USERS, "BLOCKED", "ACTIVE", "");
        policy.validate(Section.SELLER_APPLICATIONS, "PENDING", "APPROVED", null);
    }

    @Test public void transactionalResourcesHaveNoAdminTransition() {
        assertTrue(policy.allowedTargets(Section.ORDERS, "PENDING").isEmpty());
        assertTrue(policy.allowedTargets(Section.ANALYTICS, "READY").isEmpty());
    }

    @Test public void dashboardAggregationKeepsModerationAndRevenueSeparate() {
        EnumMap<Section, Integer> totals = new EnumMap<>(Section.class);
        totals.put(Section.USERS, 10); totals.put(Section.ORDERS, 4);
        AdminDashboardSnapshot snapshot = new AdminDashboardSnapshot(totals,
                2, 1, 1, 2, 450_000D);
        assertEquals(6, snapshot.getPendingModerationTotal());
        assertEquals(10, snapshot.total(Section.USERS));
        assertEquals(0, snapshot.total(Section.BANNERS));
        assertEquals(450_000D, snapshot.getDeliveredRevenue(), 0.001D);
    }
}
