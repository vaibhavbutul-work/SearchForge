package com.searchforge.ranking.evaluation;

import java.util.Map;

/**
 * Benchmark evaluation query containing ground-truth human relevance judgments.
 *
 * @param query       The search query string.
 * @param groundTruth Map of document ID to graded relevance score (grade >= 0).
 */
public record EvaluationQuery(
        String query,
        Map<Long, Integer> groundTruth
) {
    public EvaluationQuery {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }
        groundTruth = groundTruth != null ? Map.copyOf(groundTruth) : Map.of();
    }

    /**
     * Returns the graded relevance of a document (0 if unjudged / irrelevant).
     */
    public int getGrade(long docId) {
        return groundTruth.getOrDefault(docId, 0);
    }

    /**
     * Determines if a document is relevant under binary evaluation (grade > 0).
     */
    public boolean isRelevant(long docId) {
        return getGrade(docId) > 0;
    }

    /**
     * Counts total known relevant documents for this query.
     */
    public int countTotalRelevant() {
        return (int) groundTruth.values().stream()
                .filter(grade -> grade > 0)
                .count();
    }
}
