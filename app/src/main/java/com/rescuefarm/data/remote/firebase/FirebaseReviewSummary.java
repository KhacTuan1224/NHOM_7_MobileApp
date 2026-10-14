package com.rescuefarm.data.remote.firebase;

import com.google.firebase.firestore.*;
import com.rescuefarm.service.engagement.ReviewSummary;

final class FirebaseReviewSummary {
    private FirebaseReviewSummary() { }
    static void update(Transaction tx, DocumentSnapshot product, DocumentSnapshot previous,
            String reviewId, int nextRating) {
        if (!product.exists()) throw new IllegalStateException("PRODUCT_NOT_FOUND");
        int count = (int) number(product, "reviewCount");
        double sum = product.contains("ratingSum") ? number(product, "ratingSum")
            : number(product, "averageRating") * count;
        int oldRating = previous.exists() && "PUBLISHED".equals(previous.getString("status"))
            ? (int) number(previous, "rating") : 0;
        ReviewSummary next = ReviewSummary.change(count, sum, oldRating, nextRating);
        tx.update(product.getReference(), "reviewCount", next.count, "ratingSum", next.sum,
            "averageRating", next.average, "lastReviewId", reviewId, "updatedAt", FieldValue.serverTimestamp());
    }
    private static double number(DocumentSnapshot doc, String key) {
        Object value = doc.get(key); return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }
}
