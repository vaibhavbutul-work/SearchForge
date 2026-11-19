package com.searchforge.ranking.evaluation;

import java.util.*;

/**
 * Mathematical calculator for core Information Retrieval (IR) evaluation metrics:
 * Precision@K, Recall@K, Mean Reciprocal Rank (MRR), and NDCG@K.
 */
public final class RelevanceMetricsCalculator {

    private static final double LN_2 = Math.log(2.0);

    private RelevanceMetricsCalculator() {
        // Utility class
    }

    /**
     * Calculates Precision@K: fraction of top-K retrieved documents that are relevant.
     */
    public static double precisionAtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || query == null || k <= 0) {
            return 0.0;
        }

        List<Long> topK = retrievedDocIds.stream().limit(k).toList();
        long relevantCount = topK.stream().filter(query::isRelevant).count();

        return (double) relevantCount / k;
    }

    /**
     * Calculates Recall@K: fraction of all known relevant documents retrieved in top-K.
     */
    public static double recallAtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (query == null || k <= 0) {
            return 0.0;
        }

        int totalRelevant = query.countTotalRelevant();
        if (totalRelevant == 0) {
            return 1.0;
        }

        if (retrievedDocIds == null || retrievedDocIds.isEmpty()) {
            return 0.0;
        }

        List<Long> topK = retrievedDocIds.stream().limit(k).toList();
        long relevantRetrieved = topK.stream().filter(query::isRelevant).count();

        return (double) relevantRetrieved / totalRelevant;
    }

    /**
     * Calculates Reciprocal Rank (RR): 1 / (rank of first relevant document), or 0 if none found.
     */
    public static double reciprocalRank(List<Long> retrievedDocIds, EvaluationQuery query) {
        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || query == null) {
            return 0.0;
        }

        for (int i = 0; i < retrievedDocIds.size(); i++) {
            long docId = retrievedDocIds.get(i);
            if (query.isRelevant(docId)) {
                return 1.0 / (i + 1);
            }
        }

        return 0.0;
    }

    /**
     * Calculates Discounted Cumulative Gain at K (DCG@K) for a list of relevance grades.
     *
     * Formula:
     * DCG@K = SUM(i = 1 to K) [ (2^rel_i - 1) / log2(i + 1) ]
     */
    public static double dcgAtK(List<Integer> grades, int k) {
        if (grades == null || grades.isEmpty() || k <= 0) {
            return 0.0;
        }

        double dcg = 0.0;
        int limit = Math.min(grades.size(), k);

        for (int i = 0; i < limit; i++) {
            int rel = grades.get(i);
            if (rel > 0) {
                double numerator = Math.pow(2.0, rel) - 1.0;
                double denominator = Math.log(i + 2.0) / LN_2; // log2(i + 2)
                dcg += numerator / denominator;
            }
        }

        return dcg;
    }

    /**
     * Calculates Ideal Discounted Cumulative Gain at K (IDCG@K) by sorting all known relevant grades descending.
     */
    public static double idcgAtK(Collection<Integer> allKnownGrades, int k) {
        if (allKnownGrades == null || allKnownGrades.isEmpty() || k <= 0) {
            return 0.0;
        }

        List<Integer> sortedGrades = allKnownGrades.stream()
                .filter(grade -> grade != null && grade > 0)
                .sorted(Comparator.reverseOrder())
                .limit(k)
                .toList();

        return dcgAtK(sortedGrades, k);
    }

    /**
     * Calculates Normalized Discounted Cumulative Gain at K (NDCG@K) in range [0.0, 1.0].
     *
     * NDCG@K = DCG@K / IDCG@K
     */
    public static double ndcgAtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (query == null || k <= 0) {
            return 0.0;
        }

        double idcg = idcgAtK(query.groundTruth().values(), k);
        if (idcg <= 0.0) {
            return 1.0; // If no relevant documents exist anywhere, perfect ranking by definition
        }

        if (retrievedDocIds == null || retrievedDocIds.isEmpty()) {
            return 0.0;
        }

        List<Integer> retrievedGrades = retrievedDocIds.stream()
                .limit(k)
                .map(query::getGrade)
                .toList();

        double dcg = dcgAtK(retrievedGrades, k);

        return Math.min(1.0, Math.max(0.0, dcg / idcg));
    }
}
