package com.searchforge.ranking.evaluation;

/**
 * Detailed component latency profiling separating neural inference, vector search,
 * lexical inverted index lookup, and rank fusion (Task 7D).
 *
 * @param avgBm25Ms          average BM25 lexical candidate retrieval time in milliseconds
 * @param avgInferenceMs     average query embedding inference time in milliseconds
 * @param avgVectorSearchMs  average dense vector similarity search time in milliseconds
 * @param avgFusionMs        average Reciprocal Rank Fusion aggregation time in milliseconds
 * @param avgTotalHybridMs   average end-to-end hybrid search latency in milliseconds
 * @param p95TotalHybridMs   95th percentile end-to-end hybrid search latency in milliseconds
 */
public record ComponentTimingBreakdown(
        double avgBm25Ms,
        double avgInferenceMs,
        double avgVectorSearchMs,
        double avgFusionMs,
        double avgTotalHybridMs,
        double p95TotalHybridMs
) {
    public static ComponentTimingBreakdown of(
            double bm25, double inference, double vectorSearch, double fusion, double total, double p95) {
        return new ComponentTimingBreakdown(
                round3(bm25),
                round3(inference),
                round3(vectorSearch),
                round3(fusion),
                round3(total),
                round3(p95)
        );
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
