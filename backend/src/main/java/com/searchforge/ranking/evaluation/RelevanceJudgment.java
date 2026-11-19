package com.searchforge.ranking.evaluation;

/**
 * Human relevance judgment for a document relative to a query.
 *
 * @param docId Unique identifier of the document.
 * @param grade Graded relevance score (e.g. 0 = irrelevant, 1 = relevant, 2 = highly relevant).
 */
public record RelevanceJudgment(
        long docId,
        int grade
) {
    public RelevanceJudgment {
        if (docId <= 0) {
            throw new IllegalArgumentException("docId must be positive: " + docId);
        }
        if (grade < 0) {
            throw new IllegalArgumentException("grade cannot be negative: " + grade);
        }
    }
}
