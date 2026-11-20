package com.searchforge.ranking.evaluation;

import java.util.List;

/**
 * Comprehensive benchmark comparison report comparing BM25, Semantic, and Hybrid retrieval modes (Task 7B/7C/7D).
 *
 * @param totalQueries      total queries evaluated
 * @param k                 ranking depth K
 * @param bm25Report        evaluation metrics for BM25 lexical retrieval
 * @param semanticReport    evaluation metrics for dense semantic retrieval
 * @param hybridReport      evaluation metrics for hybrid RRF retrieval
 * @param timings           sub-millisecond latency profile and component breakdown
 * @param categoryBreakdown category-level comparative analysis
 * @param methodologyNotes  environmental and architectural benchmark metadata
 */
public record ModeComparisonReport(
        int totalQueries,
        int k,
        EvaluationReport bm25Report,
        EvaluationReport semanticReport,
        EvaluationReport hybridReport,
        ComponentTimingBreakdown timings,
        List<CategoryPerformance> categoryBreakdown,
        String methodologyNotes
) {
    public ModeComparisonReport {
        categoryBreakdown = categoryBreakdown != null ? List.copyOf(categoryBreakdown) : List.of();
    }
}
