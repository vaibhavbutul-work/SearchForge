package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelevanceMetricsCalculatorTest {

    @Test
    @DisplayName("Should match exact hand-calculated NDCG@3 values from Step 3 walkthrough")
    void shouldCalculateExactNdcgValues() {
        // Ground truth: doc 1 (grade 2), doc 2 (grade 1), doc 3 (grade 0)
        EvaluationQuery query = new EvaluationQuery(
                "java concurrency",
                Map.of(1L, 2, 2L, 1, 3L, 0)
        );

        // Suboptimal retrieved ranking: Doc 2, Doc 1, Doc 3
        List<Long> retrieved = List.of(2L, 1L, 3L);

        double ndcg = RelevanceMetricsCalculator.ndcgAtK(retrieved, query, 3);

        // Step 3 calculation: DCG=2.8928, IDCG=3.6309 => NDCG = 0.7967
        assertEquals(0.7967, ndcg, 1e-4);
    }

    @Test
    @DisplayName("Should return 1.0 for perfect ranking order")
    void shouldReturnOneForPerfectRanking() {
        EvaluationQuery query = new EvaluationQuery(
                "java concurrency",
                Map.of(1L, 2, 2L, 1, 3L, 0)
        );

        // Ideal order: Doc 1 (grade 2) followed by Doc 2 (grade 1)
        List<Long> idealRetrieved = List.of(1L, 2L, 3L);

        double ndcg = RelevanceMetricsCalculator.ndcgAtK(idealRetrieved, query, 3);
        assertEquals(1.0, ndcg, 1e-6);
    }

    @Test
    @DisplayName("Should severely penalize inverted ranking order")
    void shouldPenalizeInvertedRanking() {
        EvaluationQuery query = new EvaluationQuery(
                "java concurrency",
                Map.of(1L, 2, 2L, 1, 3L, 0)
        );

        // Inverted: Doc 3 (irrel), Doc 2 (grade 1), Doc 1 (grade 2)
        List<Long> invertedRetrieved = List.of(3L, 2L, 1L);

        double ndcg = RelevanceMetricsCalculator.ndcgAtK(invertedRetrieved, query, 3);

        // DCG: 0 + 1/log2(3) + 3/log2(4) = 0.6309 + 1.5000 = 2.1309
        // IDCG = 3.6309 => NDCG = 2.1309 / 3.6309 = 0.5869
        assertEquals(0.5869, ndcg, 1e-4);
        assertTrue(ndcg < 0.7967);
    }

    @Test
    @DisplayName("Should calculate Precision@K accurately")
    void shouldCalculatePrecisionAtK() {
        EvaluationQuery query = new EvaluationQuery(
                "test",
                Map.of(1L, 1, 2L, 2, 3L, 0, 4L, 0)
        );

        List<Long> retrieved = List.of(1L, 3L, 2L, 4L);

        // Top 1: [1L] => 1 relevant / 1 = 1.0
        assertEquals(1.0, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 1), 1e-6);

        // Top 2: [1L, 3L] => 1 relevant / 2 = 0.5
        assertEquals(0.5, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 2), 1e-6);

        // Top 4: [1L, 3L, 2L, 4L] => 2 relevant / 4 = 0.5
        assertEquals(0.5, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 4), 1e-6);
    }

    @Test
    @DisplayName("Should calculate Recall@K accurately")
    void shouldCalculateRecallAtK() {
        EvaluationQuery query = new EvaluationQuery(
                "test",
                Map.of(1L, 1, 2L, 2, 3L, 1) // 3 total relevant docs
        );

        List<Long> retrieved = List.of(1L, 4L, 2L);

        // Top 1: [1L] => 1 / 3 = 0.3333
        assertEquals(0.3333, RelevanceMetricsCalculator.recallAtK(retrieved, query, 1), 1e-4);

        // Top 3: [1L, 4L, 2L] => 2 / 3 = 0.6667
        assertEquals(0.6667, RelevanceMetricsCalculator.recallAtK(retrieved, query, 3), 1e-4);
    }

    @Test
    @DisplayName("Should calculate Reciprocal Rank (RR) accurately")
    void shouldCalculateReciprocalRank() {
        EvaluationQuery query = new EvaluationQuery("test", Map.of(10L, 1, 20L, 2));

        // First relevant at rank 1: RR = 1 / 1 = 1.0
        assertEquals(1.0, RelevanceMetricsCalculator.reciprocalRank(List.of(10L, 5L), query), 1e-6);

        // First relevant at rank 2: RR = 1 / 2 = 0.5
        assertEquals(0.5, RelevanceMetricsCalculator.reciprocalRank(List.of(5L, 20L), query), 1e-6);

        // First relevant at rank 3: RR = 1 / 3 = 0.3333
        assertEquals(0.3333, RelevanceMetricsCalculator.reciprocalRank(List.of(5L, 6L, 10L), query), 1e-4);

        // No relevant doc found: RR = 0.0
        assertEquals(0.0, RelevanceMetricsCalculator.reciprocalRank(List.of(1L, 2L, 3L), query), 1e-6);
    }

    @Test
    @DisplayName("Should handle edge cases safely")
    void shouldHandleEdgeCases() {
        EvaluationQuery emptyQuery = new EvaluationQuery("empty", Map.of());

        assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(List.of(1L), emptyQuery, 1));
        assertEquals(1.0, RelevanceMetricsCalculator.recallAtK(List.of(1L), emptyQuery, 1));
        assertEquals(0.0, RelevanceMetricsCalculator.reciprocalRank(List.of(1L), emptyQuery));
        assertEquals(1.0, RelevanceMetricsCalculator.ndcgAtK(List.of(1L), emptyQuery, 1));

        // Negative or zero K
        EvaluationQuery query = new EvaluationQuery("test", Map.of(1L, 1));
        assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(List.of(1L), query, 0));
        assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(List.of(1L), query, 0));
        assertEquals(0.0, RelevanceMetricsCalculator.ndcgAtK(List.of(1L), query, 0));
    }
}
