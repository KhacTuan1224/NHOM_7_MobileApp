package com.rescuefarm.service.admin;

/** Firestore document IDs are case-sensitive, unlike status/role enum values. */
public final class SellerApplicationIdentity {
    private SellerApplicationIdentity() { }

    public static String resolve(String sellerId, String legacyId, String documentId) {
        for (String value : new String[]{sellerId, legacyId, documentId}) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        throw new IllegalArgumentException("Thiếu ID tài khoản Seller.");
    }
}
