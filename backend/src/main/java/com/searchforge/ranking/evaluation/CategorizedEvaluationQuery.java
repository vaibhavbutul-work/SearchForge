package com.searchforge.ranking.evaluation;

import java.util.Map;

/**
 * Benchmark evaluation query annotated with its category and ground truth rationale.
 *
 * @param query       search query string
 * @param category    evaluation category (e.g. EXACT_KEYWORD, CONCEPTUAL, SYNONYM)
 * @param groundTruth map of document ID to relevance grade (2 = highly relevant, 1 = relevant)
 * @param rationale   documented explanation of ground truth judgments
 */
public record CategorizedEvaluationQuery(
        String query,
        QueryCategory category,
        Map<Long, Integer> groundTruth,
        String rationale
) {
    public CategorizedEvaluationQuery {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }
        category = category != null ? category : QueryCategory.EXACT_KEYWORD;
        groundTruth = groundTruth != null ? Map.copyOf(groundTruth) : Map.of();
        rationale = rationale != null ? rationale.trim() : "";
    }

    public EvaluationQuery toEvaluationQuery() {
        return new EvaluationQuery(query, groundTruth);
    }
}
