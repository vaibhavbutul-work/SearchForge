package com.searchforge.ranking.evaluation;

/**
 * Data split assignment for Information Retrieval evaluation queries.
 *
 * Prevents data leakage between model/parameter exploration, tuning, and final evaluation:
 * - DEV: Exploratory analysis, baseline benchmarking, and algorithm profiling.
 * - TUNING: Hyperparameter optimization (e.g. BM25 k1/b tuning, RRF constant, hybrid weights).
 * - EVAL: Held-out test set evaluated strictly once to report final generalization metrics.
 */
public enum EvaluationDatasetSplit {
    DEV,
    TUNING,
    EVAL
}
