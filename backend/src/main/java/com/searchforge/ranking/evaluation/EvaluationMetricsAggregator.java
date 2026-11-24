package com.searchforge.ranking.evaluation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregation engine for Information Retrieval evaluation metrics.
 *
 * Computes per-query metrics and macro-averaged aggregate summaries across:
 * - Precision@5, Precision@10
 * - Recall@5, Recall@10
 * - F1@5, F1@10
 * - Mean Reciprocal Rank (MRR)
 * - Mean Average Precision (MAP)
 * - NDCG@5, NDCG@10
 *
 * Supports multi-dimensional slicing:
 * - Overall macro-averaged dataset summary
 * - Per-category archetype breakdown
 * - Per-split (DEV, TUNING, EVAL) breakdown
 */
public final class EvaluationMetricsAggregator {

    private EvaluationMetricsAggregator() {
        // Utility class
    }

    /**
     * Evaluates a single query's retrieved ranked document list against its ground truth.
     */
    public static QueryEvaluationMetrics evaluateQuery(List<Long> retrievedDocIds, EvaluationQueryV2 query) {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }

        double p5 = RelevanceMetricsCalculator.precisionAtK(retrievedDocIds, query, 5);
        double p10 = RelevanceMetricsCalculator.precisionAtK(retrievedDocIds, query, 10);
        double r5 = RelevanceMetricsCalculator.recallAtK(retrievedDocIds, query, 5);
        double r10 = RelevanceMetricsCalculator.recallAtK(retrievedDocIds, query, 10);
        double f1_5 = RelevanceMetricsCalculator.f1AtK(retrievedDocIds, query, 5);
        double f1_10 = RelevanceMetricsCalculator.f1AtK(retrievedDocIds, query, 10);
        double rr = RelevanceMetricsCalculator.reciprocalRank(retrievedDocIds, query);
        double ap = RelevanceMetricsCalculator.averagePrecision(retrievedDocIds, query);
        double ndcg5 = RelevanceMetricsCalculator.ndcgAtK(retrievedDocIds, query, 5);
        double ndcg10 = RelevanceMetricsCalculator.ndcgAtK(retrievedDocIds, query, 10);

        return new QueryEvaluationMetrics(
                query.queryId(),
                query.queryText(),
                query.category(),
                query.split(),
                round4(p5),
                round4(p10),
                round4(r5),
                round4(r10),
                round4(f1_5),
                round4(f1_10),
                round4(rr),
                round4(ap),
                round4(ndcg5),
                round4(ndcg10)
        );
    }

    /**
     * Computes macro-averaged aggregate metrics across a collection of evaluated queries.
     */
    public static AggregateMetricsSummary aggregate(List<QueryEvaluationMetrics> queryMetrics) {
        if (queryMetrics == null || queryMetrics.isEmpty()) {
            return new AggregateMetricsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        int count = queryMetrics.size();
        double p5 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::precisionAt5).average().orElse(0.0);
        double p10 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::precisionAt10).average().orElse(0.0);
        double r5 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::recallAt5).average().orElse(0.0);
        double r10 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::recallAt10).average().orElse(0.0);
        double f1_5 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::f1At5).average().orElse(0.0);
        double f1_10 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::f1At10).average().orElse(0.0);
        double mrr = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::reciprocalRank).average().orElse(0.0);
        double map = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::averagePrecision).average().orElse(0.0);
        double ndcg5 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::ndcgAt5).average().orElse(0.0);
        double ndcg10 = queryMetrics.stream().mapToDouble(QueryEvaluationMetrics::ndcgAt10).average().orElse(0.0);

        return new AggregateMetricsSummary(
                count,
                round4(p5),
                round4(p10),
                round4(r5),
                round4(r10),
                round4(f1_5),
                round4(f1_10),
                round4(mrr),
                round4(map),
                round4(ndcg5),
                round4(ndcg10)
        );
    }

    /**
     * Computes aggregate metrics grouped by Information Retrieval query category.
     */
    public static Map<EvaluationCategory, AggregateMetricsSummary> aggregateByCategory(List<QueryEvaluationMetrics> queryMetrics) {
        if (queryMetrics == null || queryMetrics.isEmpty()) {
            return Map.of();
        }

        Map<EvaluationCategory, List<QueryEvaluationMetrics>> grouped = queryMetrics.stream()
                .collect(Collectors.groupingBy(QueryEvaluationMetrics::category, () -> new EnumMap<>(EvaluationCategory.class), Collectors.toList()));

        Map<EvaluationCategory, AggregateMetricsSummary> result = new EnumMap<>(EvaluationCategory.class);
        for (Map.Entry<EvaluationCategory, List<QueryEvaluationMetrics>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), aggregate(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Computes aggregate metrics grouped by evaluation split (DEV, TUNING, EVAL).
     */
    public static Map<EvaluationDatasetSplit, AggregateMetricsSummary> aggregateBySplit(List<QueryEvaluationMetrics> queryMetrics) {
        if (queryMetrics == null || queryMetrics.isEmpty()) {
            return Map.of();
        }

        Map<EvaluationDatasetSplit, List<QueryEvaluationMetrics>> grouped = queryMetrics.stream()
                .collect(Collectors.groupingBy(QueryEvaluationMetrics::split, () -> new EnumMap<>(EvaluationDatasetSplit.class), Collectors.toList()));

        Map<EvaluationDatasetSplit, AggregateMetricsSummary> result = new EnumMap<>(EvaluationDatasetSplit.class);
        for (Map.Entry<EvaluationDatasetSplit, List<QueryEvaluationMetrics>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), aggregate(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static double round4(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }

    /**
     * Individual query evaluation metrics record.
     */
    public record QueryEvaluationMetrics(
            String queryId,
            String queryText,
            EvaluationCategory category,
            EvaluationDatasetSplit split,
            double precisionAt5,
            double precisionAt10,
            double recallAt5,
            double recallAt10,
            double f1At5,
            double f1At10,
            double reciprocalRank,
            double averagePrecision,
            double ndcgAt5,
            double ndcgAt10
    ) {}

    /**
     * Macro-averaged aggregate metrics summary record.
     */
    public record AggregateMetricsSummary(
            int queryCount,
            double meanPrecisionAt5,
            double meanPrecisionAt10,
            double meanRecallAt5,
            double meanRecallAt10,
            double meanF1At5,
            double meanF1At10,
            double mrr,
            double map,
            double meanNdcgAt5,
            double meanNdcgAt10
    ) {}
}
