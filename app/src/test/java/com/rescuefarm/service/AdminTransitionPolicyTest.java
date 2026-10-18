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
        assertThrows(IllegalArgumentException.class,
                () -> policy.validate(Section.PRODUCTS, "ACTIVE", "HIDDEN", ""));
        policy.validate(Section.PRODUCTS, "ACTIVE", "HIDDEN", "Vi phạm nội dung");
        policy.validate(Section.CAMPAIGNS, "ACTIVE", "STOPPED", "Sai thông tin");
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
