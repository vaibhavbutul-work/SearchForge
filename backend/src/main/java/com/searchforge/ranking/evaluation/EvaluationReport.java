package com.searchforge.ranking.evaluation;

import java.util.List;

/**
 * Benchmark evaluation report aggregating ranking metrics across multiple queries.
 *
 * @param totalQueries       Total number of evaluated queries.
 * @param k                  Evaluation cutoff rank K.
 * @param meanPrecisionAtK   Mean Precision@K across all evaluated queries.
 * @param meanRecallAtK      Mean Recall@K across all evaluated queries.
 * @param meanReciprocalRank Mean Reciprocal Rank (MRR) across all evaluated queries.
 * @param meanNdcgAtK        Mean NDCG@K across all evaluated queries.
 * @param queryResults       Individual query evaluation results.
 */
public record EvaluationReport(
        int totalQueries,
        int k,
        double meanPrecisionAtK,
        double meanRecallAtK,
        double meanReciprocalRank,
        double meanNdcgAtK,
        List<QueryEvaluationResult> queryResults
) {
    public EvaluationReport {
        queryResults = queryResults != null ? List.copyOf(queryResults) : List.of();
    }
}
