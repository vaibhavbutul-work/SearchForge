package com.searchforge.ranking.evaluation;

import java.util.Map;

/**
 * Enhanced evaluation query specification for Phase 12 Search Quality Evaluation.
 *
 * Encapsulates a unique query identifier, category archetype, evaluation split,
 * 4-level graded ground-truth relevance judgments, and documented intent rationale.
 *
 * @param queryId    Unique stable identifier (e.g. "Q01", "Q02").
 * @param queryText  Exact query string submitted to search engines.
 * @param category   Information Retrieval query archetype.
 * @param split      Evaluation split (DEV, TUNING, or EVAL).
 * @param groundTruth Map of document ID to graded relevance (0 = Irrelevant, 1 = Slightly Relevant, 2 = Relevant, 3 = Highly Relevant).
 * @param rationale  Documented evaluation rationale explaining why targets were assigned their respective grades.
 */
public record EvaluationQueryV2(
        String queryId,
        String queryText,
        EvaluationCategory category,
        EvaluationDatasetSplit split,
        Map<Long, Integer> groundTruth,
        String rationale
) {
    public EvaluationQueryV2 {
        if (queryId == null || queryId.isBlank()) {
            throw new IllegalArgumentException("queryId cannot be null or blank");
        }
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText cannot be null or blank");
        }
        category = category != null ? category : EvaluationCategory.EXACT_KEYWORD;
        split = split != null ? split : EvaluationDatasetSplit.DEV;
        groundTruth = groundTruth != null ? Map.copyOf(groundTruth) : Map.of();
        rationale = rationale != null ? rationale.trim() : "";

        // Validate that all relevance grades fall within standard [0, 3] range
        for (Map.Entry<Long, Integer> entry : groundTruth.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0) {
                throw new IllegalArgumentException("docId must be positive: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 3) {
                throw new IllegalArgumentException("Relevance grade must be in range [0, 3], got: " + entry.getValue());
            }
        }
    }

    /**
     * Returns the graded relevance of a document (0 if unjudged or irrelevant).
     */
    public int getGrade(long docId) {
        return groundTruth.getOrDefault(docId, 0);
    }

    /**
     * Binary relevance check: documents with grade >= 2 are considered relevant.
     */
    public boolean isRelevant(long docId) {
        return getGrade(docId) >= 2;
    }

    /**
     * Graded check: documents with grade == 3 are considered highly relevant / primary targets.
     */
    public boolean isHighlyRelevant(long docId) {
        return getGrade(docId) == 3;
    }

    /**
     * Counts total relevant documents (grade >= 2) for binary metrics (e.g. Recall@K).
     */
    public int countTotalRelevant() {
        return (int) groundTruth.values().stream()
                .filter(grade -> grade != null && grade >= 2)
                .count();
    }

    /**
     * Converts to legacy EvaluationQuery for seamless interoperability with existing calculators.
     */
    public EvaluationQuery toEvaluationQuery() {
        return new EvaluationQuery(queryText, groundTruth);
    }
}
