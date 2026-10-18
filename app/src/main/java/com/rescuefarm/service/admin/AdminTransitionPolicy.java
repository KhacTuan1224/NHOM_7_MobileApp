package com.rescuefarm.service.admin;

import com.rescuefarm.data.repository.AdminRepository.Section;
import com.rescuefarm.domain.enums.UserRole;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AdminTransitionPolicy {
    public void requireAdmin(UserRole role) {
        if (role != UserRole.ADMIN) throw new SecurityException("ADMIN role is required");
    }

    public List<String> allowedTargets(Section section, String current) {
        String status = clean(current);
        switch (section) {
            case SELLER_APPLICATIONS:
                return status.equals("PENDING") ? list("APPROVED", "REJECTED") : none();
            case USERS:
                if (status.equals("ACTIVE") || status.equals("INACTIVE")) return list("BLOCKED");
                return status.equals("BLOCKED") ? list("ACTIVE") : none();
            case PRODUCTS:
                if (status.equals("ACTIVE") || status.equals("INACTIVE") || status.equals("SOLD_OUT")) return list("HIDDEN");
                return status.equals("HIDDEN") ? list("ACTIVE", "INACTIVE", "SOLD_OUT") : none();
            case CAMPAIGNS:
                if (status.equals("PENDING_APPROVAL")) return list("ACTIVE", "REJECTED");
                return status.equals("ACTIVE") ? list("STOPPED") : none();
            case REVIEWS:
                if (status.equals("PENDING")) return list("PUBLISHED", "REJECTED");
                if (status.equals("PUBLISHED")) return list("HIDDEN");
                return status.equals("HIDDEN") ? list("PUBLISHED") : none();
            case REPORTS:
                if (status.equals("PENDING")) return list("IN_REVIEW");
                return status.equals("IN_REVIEW") ? list("RESOLVED", "REJECTED") : none();
            default:
                return none();
        }
    }

    public boolean isAllowed(Section section, String current, String target) {
        return allowedTargets(section, current).contains(clean(target));
    }

    public boolean requiresReason(String target) {
        String value = clean(target);
        return value.equals("REJECTED") || value.equals("HIDDEN") || value.equals("STOPPED")
                || value.equals("BLOCKED");
    }

    public void validate(Section section, String current, String target, String reason) {
        if (!isAllowed(section, current, target)) throw new IllegalStateException("ADMIN_TRANSITION_INVALID");
        if (requiresReason(target) && clean(reason).length() < 3) {
            throw new IllegalArgumentException("Moderation reason must have at least 3 characters");
        }
    }

    private static List<String> list(String... values) { return Arrays.asList(values); }
    private static List<String> none() { return Collections.emptyList(); }
    private static String clean(String value) { return value == null ? "" : value.trim().toUpperCase(); }
}
