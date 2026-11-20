package com.searchforge.ranking.evaluation;

import java.util.List;

/**
 * Multi-depth Information Retrieval evaluation report comparing BM25, Semantic, and Hybrid retrieval
 * across multiple cutoff depths (K=3, K=5) and standard IR effectiveness metrics.
 */
public record ComprehensiveMetricsReport(
        int totalQueries,
        String modelName,
        ModeMetrics bm25,
        ModeMetrics semantic,
        ModeMetrics hybrid,
        ComponentTimingBreakdown timings,
        List<CategoryPerformance> categoryBreakdown
) {

    public ComprehensiveMetricsReport {
        categoryBreakdown = categoryBreakdown != null ? List.copyOf(categoryBreakdown) : List.of();
    }

    /**
     * Metrics evaluated across multiple ranking cutoffs.
     */
    public record ModeMetrics(
            double ndcgAt3,
            double ndcgAt5,
            double mrr,
            double precisionAt3,
            double precisionAt5,
            double recallAt3,
            double recallAt5
    ) {
        public static ModeMetrics of(
                double ndcgAt3, double ndcgAt5, double mrr,
                double pAt3, double pAt5, double rAt3, double rAt5) {
            return new ModeMetrics(
                    round4(ndcgAt3),
                    round4(ndcgAt5),
                    round4(mrr),
                    round4(pAt3),
                    round4(pAt5),
                    round4(rAt3),
                    round4(rAt5)
            );
        }

        private static double round4(double val) {
            return Math.round(val * 10000.0) / 10000.0;
        }
    }
}
