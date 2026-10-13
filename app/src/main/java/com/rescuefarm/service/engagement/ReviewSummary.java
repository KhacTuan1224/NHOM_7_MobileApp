package com.rescuefarm.service.engagement;

public final class ReviewSummary {
    public final int count;
    public final double sum, average;
    private ReviewSummary(int count, double sum) {
        this.count = count; this.sum = sum; this.average = count == 0 ? 0 : sum / count;
    }
    public static ReviewSummary change(int count, double sum, int previousRating, int nextRating) {
        if (count < 0 || !Double.isFinite(sum) || sum < 0 || previousRating < 0 || previousRating > 5
                || nextRating < 0 || nextRating > 5) throw new IllegalArgumentException("Invalid review summary");
        int updatedCount = count + (nextRating > 0 ? 1 : 0) - (previousRating > 0 ? 1 : 0);
        double updatedSum = sum + nextRating - previousRating;
        if (updatedCount < 0 || updatedSum < 0 || updatedSum > updatedCount * 5D)
            throw new IllegalArgumentException("Inconsistent review summary");
        return new ReviewSummary(updatedCount, updatedCount == 0 ? 0 : updatedSum);
    }
}
