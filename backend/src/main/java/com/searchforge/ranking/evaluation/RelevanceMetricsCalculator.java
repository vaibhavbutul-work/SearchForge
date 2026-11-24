package com.searchforge.ranking.evaluation;

import java.util.*;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * Mathematical calculator for Information Retrieval (IR) evaluation metrics:
 * - Precision@K
 * - Recall@K
 * - F1@K
 * - Reciprocal Rank (RR) / Mean Reciprocal Rank (MRR)
 * - Average Precision (AP) / Mean Average Precision (MAP)
 * - Discounted Cumulative Gain (DCG@K)
 * - Ideal Discounted Cumulative Gain (IDCG@K)
 * - Normalized Discounted Cumulative Gain (NDCG@K)
 *
 * Implements strict handling of edge cases (empty lists, unjudged documents,
 * zero relevant targets, duplicate candidate IDs, and division-by-zero guards).
 */
public final class RelevanceMetricsCalculator {

    private static final double LN_2 = Math.log(2.0);

    private RelevanceMetricsCalculator() {
        // Utility class
    }

    // =========================================================================
    // 1. PRECISION@K
    // =========================================================================

    /**
     * Calculates Precision@K: fraction of top-K retrieved unique documents that are relevant.
     * Formula: Precision@K = (Relevant documents in top K) / K
     */
    public static double precisionAtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (query == null) {
            return 0.0;
        }
        return calculatePrecision(retrievedDocIds, query::isRelevant, k);
    }

    public static double precisionAtK(List<Long> retrievedDocIds, EvaluationQueryV2 query, int k) {
        if (query == null) {
            return 0.0;
        }
        return calculatePrecision(retrievedDocIds, query::isRelevant, k);
    }

    private static double calculatePrecision(List<Long> retrievedDocIds, LongPredicate isRelevant, int k) {
        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || isRelevant == null || k <= 0) {
            return 0.0;
        }

        List<Long> sanitizedTopK = sanitizeAndLimit(retrievedDocIds, k);
        long relevantCount = sanitizedTopK.stream().filter(isRelevant::test).count();

        return (double) relevantCount / k;
    }

    // =========================================================================
    // 2. RECALL@K
    // =========================================================================

    /**
     * Calculates Recall@K: fraction of all known relevant documents retrieved in top-K.
     * Formula: Recall@K = (Relevant documents in top K) / (Total known relevant documents)
     * Policy: If total relevant documents is 0, returns 1.0 (vacuously satisfied).
     */
    public static double recallAtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (query == null) {
            return 0.0;
        }
        return calculateRecall(retrievedDocIds, query::isRelevant, query.countTotalRelevant(), k);
    }

    public static double recallAtK(List<Long> retrievedDocIds, EvaluationQueryV2 query, int k) {
        if (query == null) {
            return 0.0;
        }
        return calculateRecall(retrievedDocIds, query::isRelevant, query.countTotalRelevant(), k);
    }

    private static double calculateRecall(List<Long> retrievedDocIds, LongPredicate isRelevant, int totalRelevant, int k) {
        if (k <= 0) {
            return 0.0;
        }

        if (totalRelevant == 0) {
            return 1.0;
        }

        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || isRelevant == null) {
            return 0.0;
        }

        List<Long> sanitizedTopK = sanitizeAndLimit(retrievedDocIds, k);
        long relevantRetrieved = sanitizedTopK.stream().filter(isRelevant::test).count();

        return (double) relevantRetrieved / totalRelevant;
    }

    // =========================================================================
    // 3. F1@K
    // =========================================================================

    /**
     * Calculates F1@K: harmonic mean of Precision@K and Recall@K.
     * Formula: F1@K = 2 * (P * R) / (P + R)
     * Policy: Returns 0.0 if P + R == 0 to prevent division by zero.
     */
    public static double f1AtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (query == null) {
            return 0.0;
        }
        double p = precisionAtK(retrievedDocIds, query, k);
        double r = recallAtK(retrievedDocIds, query, k);
        return calculateF1(p, r);
    }

    public static double f1AtK(List<Long> retrievedDocIds, EvaluationQueryV2 query, int k) {
        if (query == null) {
            return 0.0;
        }
        double p = precisionAtK(retrievedDocIds, query, k);
        double r = recallAtK(retrievedDocIds, query, k);
        return calculateF1(p, r);
    }

    private static double calculateF1(double p, double r) {
        if (p + r <= 0.0) {
            return 0.0;
        }
        return (2.0 * p * r) / (p + r);
    }

    // =========================================================================
    // 4. RECIPROCAL RANK (RR)
    // =========================================================================

    /**
     * Calculates Reciprocal Rank (RR): 1 / (1-based rank of first relevant document).
     * Policy: Returns 0.0 if no relevant document is found in the retrieved list.
     */
    public static double reciprocalRank(List<Long> retrievedDocIds, EvaluationQuery query) {
        if (query == null) {
            return 0.0;
        }
        return calculateReciprocalRank(retrievedDocIds, query::isRelevant);
    }

    public static double reciprocalRank(List<Long> retrievedDocIds, EvaluationQueryV2 query) {
        if (query == null) {
            return 0.0;
        }
        return calculateReciprocalRank(retrievedDocIds, query::isRelevant);
    }

    private static double calculateReciprocalRank(List<Long> retrievedDocIds, LongPredicate isRelevant) {
        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || isRelevant == null) {
            return 0.0;
        }

        List<Long> sanitized = sanitize(retrievedDocIds);
        for (int i = 0; i < sanitized.size(); i++) {
            long docId = sanitized.get(i);
            if (isRelevant.test(docId)) {
                return 1.0 / (i + 1);
            }
        }

        return 0.0;
    }

    // =========================================================================
    // 5. AVERAGE PRECISION (AP)
    // =========================================================================

    /**
     * Calculates Average Precision (AP) for a single query across all retrieved positions:
     * Formula: AP = (SUM_{i=1..N} [ Precision@i * rel(i) ]) / (Total known relevant documents)
     * Policy: If total relevant documents is 0, returns 0.0.
     */
    public static double averagePrecision(List<Long> retrievedDocIds, EvaluationQuery query) {
        if (query == null) {
            return 0.0;
        }
        return calculateAveragePrecision(retrievedDocIds, query::isRelevant, query.countTotalRelevant());
    }

    public static double averagePrecision(List<Long> retrievedDocIds, EvaluationQueryV2 query) {
        if (query == null) {
            return 0.0;
        }
        return calculateAveragePrecision(retrievedDocIds, query::isRelevant, query.countTotalRelevant());
    }

    private static double calculateAveragePrecision(List<Long> retrievedDocIds, LongPredicate isRelevant, int totalRelevant) {
        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || isRelevant == null || totalRelevant == 0) {
            return 0.0;
        }

        List<Long> sanitized = sanitize(retrievedDocIds);
        double cumulativePrecision = 0.0;
        int relevantSoFar = 0;

        for (int i = 0; i < sanitized.size(); i++) {
            long docId = sanitized.get(i);
            if (isRelevant.test(docId)) {
                relevantSoFar++;
                double precisionAtThisRank = (double) relevantSoFar / (i + 1);
                cumulativePrecision += precisionAtThisRank;
            }
        }

        return cumulativePrecision / totalRelevant;
    }

    // =========================================================================
    // 6. DCG@K
    // =========================================================================

    /**
     * Calculates Discounted Cumulative Gain at K (DCG@K) using standard logarithmic discount.
     * Formula: DCG@K = SUM(i = 1 to K) [ (2^rel_i - 1) / log2(i + 1) ]
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

    // =========================================================================
    // 7. IDCG@K & NDCG@K
    // =========================================================================

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
     * Formula: NDCG@K = DCG@K / IDCG@K
     * Policy: If IDCG@K == 0 (no relevant items exist anywhere), returns 1.0.
     */
    public static double ndcgAtK(List<Long> retrievedDocIds, EvaluationQuery query, int k) {
        if (query == null) {
            return 0.0;
        }
        return calculateNdcg(retrievedDocIds, query.groundTruth().values(), query::getGrade, k);
    }

    public static double ndcgAtK(List<Long> retrievedDocIds, EvaluationQueryV2 query, int k) {
        if (query == null) {
            return 0.0;
        }
        return calculateNdcg(retrievedDocIds, query.groundTruth().values(), query::getGrade, k);
    }

    private static double calculateNdcg(List<Long> retrievedDocIds, Collection<Integer> allKnownGrades,
                                       LongFunction<Integer> gradeLookup, int k) {
        if (k <= 0) {
            return 0.0;
        }

        double idcg = idcgAtK(allKnownGrades, k);
        if (idcg <= 0.0) {
            return 1.0; // If no relevant documents exist anywhere, ranking is trivially optimal
        }

        if (retrievedDocIds == null || retrievedDocIds.isEmpty() || gradeLookup == null) {
            return 0.0;
        }

        List<Long> sanitizedTopK = sanitizeAndLimit(retrievedDocIds, k);
        List<Integer> retrievedGrades = sanitizedTopK.stream()
                .map(gradeLookup::apply)
                .toList();

        double dcg = dcgAtK(retrievedGrades, k);

        return Math.min(1.0, Math.max(0.0, dcg / idcg));
    }

    // =========================================================================
    // SANITIZATION HELPERS
    // =========================================================================

    /**
     * Deduplicates retrieved document IDs while strictly preserving initial ranking order.
     * Prevents buggy search engines from inflating P@K or DCG by spamming duplicate doc IDs.
     */
    private static List<Long> sanitize(List<Long> retrievedDocIds) {
        if (retrievedDocIds == null || retrievedDocIds.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new HashSet<>(retrievedDocIds.size());
        List<Long> deduped = new ArrayList<>(retrievedDocIds.size());
        for (Long id : retrievedDocIds) {
            if (id != null && seen.add(id)) {
                deduped.add(id);
            }
        }
        return deduped;
    }

    private static List<Long> sanitizeAndLimit(List<Long> retrievedDocIds, int k) {
        List<Long> sanitized = sanitize(retrievedDocIds);
        if (sanitized.size() <= k) {
            return sanitized;
        }
        return sanitized.subList(0, k);
    }
}
