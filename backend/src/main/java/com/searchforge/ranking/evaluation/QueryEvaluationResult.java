package com.searchforge.ranking.evaluation;

/**
 * Computed evaluation metrics for a single search query at cutoff K.
 *
 * @param query                  The evaluated query string.
 * @param k                      Cutoff threshold K.
 * @param precisionAtK           Precision@K (fraction of top-K results that are relevant).
 * @param recallAtK              Recall@K (fraction of all relevant documents retrieved in top-K).
 * @param reciprocalRank         Reciprocal Rank (1 / rank of the first relevant document).
 * @param ndcgAtK                Normalized Discounted Cumulative Gain at K (graded ranking quality).
 * @param retrievedCount         Number of documents returned by the search engine.
 * @param relevantRetrievedCount Number of relevant documents retrieved within the top-K.
 */
public record QueryEvaluationResult(
        String query,
        int k,
        double precisionAtK,
        double recallAtK,
        double reciprocalRank,
        double ndcgAtK,
        int retrievedCount,
        int relevantRetrievedCount
) {
    public QueryEvaluationResult {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }
        if (precisionAtK < 0.0 || precisionAtK > 1.0001) {
            throw new IllegalArgumentException("precisionAtK out of bounds: " + precisionAtK);
        }
        if (recallAtK < 0.0 || recallAtK > 1.0001) {
            throw new IllegalArgumentException("recallAtK out of bounds: " + recallAtK);
        }
        if (reciprocalRank < 0.0 || reciprocalRank > 1.0001) {
            throw new IllegalArgumentException("reciprocalRank out of bounds: " + reciprocalRank);
        }
        if (ndcgAtK < 0.0 || ndcgAtK > 1.0001) {
            throw new IllegalArgumentException("ndcgAtK out of bounds: " + ndcgAtK);
        }
    }
}
