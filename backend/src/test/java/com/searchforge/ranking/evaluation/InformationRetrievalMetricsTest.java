package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.2: Information Retrieval Evaluation Metrics Test Suite")
class InformationRetrievalMetricsTest {

    private EvaluationQueryV2 createQuery(String id, Map<Long, Integer> groundTruth) {
        return new EvaluationQueryV2(
                id,
                "query text for " + id,
                EvaluationCategory.EXACT_KEYWORD,
                EvaluationDatasetSplit.DEV,
                groundTruth,
                "Test rationale"
        );
    }

    // =========================================================================
    // 1. PRECISION@K TESTS
    // =========================================================================

    @Nested
    @DisplayName("Precision@K Evaluation")
    class PrecisionAtKTests {

        @Test
        @DisplayName("Should calculate exact Precision@K for varied cutoffs")
        void shouldCalculateExactPrecision() {
            // Ground truth: 101L -> 3 (rel), 102L -> 2 (rel), 103L -> 1 (irrel for binary), 104L -> 0 (irrel)
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2, 103L, 1, 104L, 0));
            List<Long> retrieved = List.of(101L, 103L, 102L, 104L, 105L);

            // Top 1: [101] -> 1/1 = 1.0
            assertEquals(1.0, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 1), 1e-6);

            // Top 2: [101, 103] -> 1 relevant (101) / 2 = 0.5
            assertEquals(0.5, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 2), 1e-6);

            // Top 3: [101, 103, 102] -> 2 relevant (101, 102) / 3 = 0.66667
            assertEquals(2.0 / 3.0, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 3), 1e-5);

            // Top 5: [101, 103, 102, 104, 105] -> 2 relevant / 5 = 0.4
            assertEquals(0.4, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 5), 1e-6);

            // Top 10: only 5 retrieved -> 2 relevant / 10 = 0.2
            assertEquals(0.2, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 10), 1e-6);
        }

        @Test
        @DisplayName("Should return 0.0 when no relevant documents are retrieved")
        void shouldReturnZeroWhenNoRelevantRetrieved() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2));
            List<Long> retrieved = List.of(201L, 202L, 203L);

            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 3), 1e-6);
        }

        @Test
        @DisplayName("Should deduplicate retrieved documents to prevent precision inflation")
        void shouldDeduplicateRetrievedDocuments() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3));
            // Search engine incorrectly returns doc 101 multiple times
            List<Long> duplicateRetrieved = List.of(101L, 101L, 101L, 102L);

            // Without deduplication, top 3 would contain 3 relevant items -> 3/3 = 1.0 (inflated)
            // With deduplication, unique items are [101, 102] -> 1 relevant / 3 = 0.33333
            assertEquals(1.0 / 3.0, RelevanceMetricsCalculator.precisionAtK(duplicateRetrieved, query, 3), 1e-5);
        }
    }

    // =========================================================================
    // 2. RECALL@K TESTS
    // =========================================================================

    @Nested
    @DisplayName("Recall@K Evaluation")
    class RecallAtKTests {

        @Test
        @DisplayName("Should calculate exact Recall@K for varied cutoffs")
        void shouldCalculateExactRecall() {
            // Ground truth: 4 relevant docs (101, 102, 103, 104 have grade >= 2)
            EvaluationQueryV2 query = createQuery("Q1", Map.of(
                    101L, 3, 102L, 3, 103L, 2, 104L, 2, 105L, 1, 106L, 0
            ));
            List<Long> retrieved = List.of(101L, 105L, 102L, 107L, 103L, 108L);

            // Total relevant = 4
            // Top 1: [101] -> 1/4 = 0.25
            assertEquals(0.25, RelevanceMetricsCalculator.recallAtK(retrieved, query, 1), 1e-6);

            // Top 3: [101, 105, 102] -> 2/4 = 0.50
            assertEquals(0.50, RelevanceMetricsCalculator.recallAtK(retrieved, query, 3), 1e-6);

            // Top 5: [101, 105, 102, 107, 103] -> 3/4 = 0.75
            assertEquals(0.75, RelevanceMetricsCalculator.recallAtK(retrieved, query, 5), 1e-6);
        }

        @Test
        @DisplayName("Should return 1.0 when total relevant documents in ground truth is zero")
        void shouldReturnOneWhenTotalRelevantIsZero() {
            // Query with no relevant documents (all 0 or 1)
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 1, 102L, 0));
            List<Long> retrieved = List.of(101L, 102L);

            // Vacuously satisfied: engine retrieved 100% of the 0 relevant documents
            assertEquals(1.0, RelevanceMetricsCalculator.recallAtK(retrieved, query, 5), 1e-6);
        }

        @Test
        @DisplayName("Should return 0.0 when retrieved list is empty and relevant documents exist")
        void shouldReturnZeroForEmptyRetrieved() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2));
            assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(Collections.emptyList(), query, 5), 1e-6);
            assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(null, query, 5), 1e-6);
        }
    }

    // =========================================================================
    // 3. F1@K TESTS
    // =========================================================================

    @Nested
    @DisplayName("F1@K Evaluation")
    class F1AtKTests {

        @Test
        @DisplayName("Should calculate harmonic mean of Precision and Recall")
        void shouldCalculateHarmonicMean() {
            // Ground truth: 2 relevant documents (101, 102)
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2, 103L, 0));
            List<Long> retrieved = List.of(101L, 103L, 104L, 105L);

            // At K=2: top 2 = [101, 103] -> 1 relevant
            // P@2 = 1/2 = 0.5
            // R@2 = 1/2 = 0.5
            // F1@2 = 2 * (0.5 * 0.5) / (0.5 + 0.5) = 0.5
            assertEquals(0.5, RelevanceMetricsCalculator.f1AtK(retrieved, query, 2), 1e-6);

            // At K=4: top 4 = [101, 103, 104, 105] -> 1 relevant
            // P@4 = 1/4 = 0.25
            // R@4 = 1/2 = 0.50
            // F1@4 = 2 * (0.25 * 0.50) / (0.25 + 0.50) = 2 * 0.125 / 0.75 = 0.25 / 0.75 = 1/3 = 0.33333
            assertEquals(1.0 / 3.0, RelevanceMetricsCalculator.f1AtK(retrieved, query, 4), 1e-5);
        }

        @Test
        @DisplayName("Should return 0.0 safely when P + R == 0")
        void shouldReturnZeroWhenPrecisionAndRecallAreZero() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3));
            List<Long> retrieved = List.of(201L, 202L);

            assertEquals(0.0, RelevanceMetricsCalculator.f1AtK(retrieved, query, 2), 1e-6);
        }
    }

    // =========================================================================
    // 4. RECIPROCAL RANK (RR) TESTS
    // =========================================================================

    @Nested
    @DisplayName("Reciprocal Rank (RR) Evaluation")
    class ReciprocalRankTests {

        @Test
        @DisplayName("Should return 1.0 when first document is relevant")
        void shouldReturnOneWhenFirstDocIsRelevant() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 2, 102L, 3));
            List<Long> retrieved = List.of(101L, 103L);

            assertEquals(1.0, RelevanceMetricsCalculator.reciprocalRank(retrieved, query), 1e-6);
        }

        @Test
        @DisplayName("Should return 1/rank for first relevant document")
        void shouldReturnReciprocalOfRank() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 2, 102L, 3));

            // Rank 2
            assertEquals(0.5, RelevanceMetricsCalculator.reciprocalRank(List.of(999L, 101L), query), 1e-6);

            // Rank 3
            assertEquals(1.0 / 3.0, RelevanceMetricsCalculator.reciprocalRank(List.of(999L, 998L, 102L), query), 1e-5);

            // Rank 4
            assertEquals(0.25, RelevanceMetricsCalculator.reciprocalRank(List.of(999L, 998L, 997L, 101L), query), 1e-6);
        }

        @Test
        @DisplayName("Should return 0.0 when no relevant document is found in entire retrieved list")
        void shouldReturnZeroWhenNoneRelevant() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 2));
            List<Long> retrieved = List.of(999L, 998L, 997L);

            assertEquals(0.0, RelevanceMetricsCalculator.reciprocalRank(retrieved, query), 1e-6);
        }
    }

    // =========================================================================
    // 5. AVERAGE PRECISION (AP) TESTS
    // =========================================================================

    @Nested
    @DisplayName("Average Precision (AP) Evaluation")
    class AveragePrecisionTests {

        @Test
        @DisplayName("Should calculate exact hand-computed Average Precision")
        void shouldCalculateExactAveragePrecision() {
            // Ground truth: 3 relevant docs (101, 102, 103)
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2, 103L, 2));

            // Retrieved: [101 (rel), 201 (irrel), 102 (rel), 202 (irrel), 103 (rel)]
            // Position 1: doc 101 is relevant -> P@1 = 1/1 = 1.0
            // Position 2: doc 201 is irrelevant
            // Position 3: doc 102 is relevant -> P@3 = 2/3 = 0.66667
            // Position 4: doc 202 is irrelevant
            // Position 5: doc 103 is relevant -> P@5 = 3/5 = 0.60000
            // Sum of Precisions at relevant ranks = 1.0 + 2/3 + 3/5 = 1.0 + 0.666667 + 0.6 = 2.266667
            // AP = Sum / Total Relevant (3) = 2.266667 / 3 = 0.755556
            List<Long> retrieved = List.of(101L, 201L, 102L, 202L, 103L);

            double expectedAp = (1.0 + (2.0 / 3.0) + (3.0 / 5.0)) / 3.0;
            assertEquals(expectedAp, RelevanceMetricsCalculator.averagePrecision(retrieved, query), 1e-5);
        }

        @Test
        @DisplayName("Should return 1.0 for perfect ranking of all relevant documents")
        void shouldReturnOneForPerfectRanking() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2));
            List<Long> retrieved = List.of(101L, 102L, 999L);

            // Pos 1: P@1 = 1/1 = 1.0
            // Pos 2: P@2 = 2/2 = 1.0
            // Sum = 2.0 / 2 = 1.0
            assertEquals(1.0, RelevanceMetricsCalculator.averagePrecision(retrieved, query), 1e-6);
        }

        @Test
        @DisplayName("Should return 0.0 when no relevant documents are retrieved")
        void shouldReturnZeroWhenNoRelevantRetrieved() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2));
            List<Long> retrieved = List.of(999L, 998L);

            assertEquals(0.0, RelevanceMetricsCalculator.averagePrecision(retrieved, query), 1e-6);
        }

        @Test
        @DisplayName("Should return 0.0 when total relevant documents in ground truth is zero")
        void shouldReturnZeroWhenZeroRelevantInGroundTruth() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 1, 102L, 0));
            List<Long> retrieved = List.of(101L, 102L);

            assertEquals(0.0, RelevanceMetricsCalculator.averagePrecision(retrieved, query), 1e-6);
        }
    }

    // =========================================================================
    // 6. DCG@K, IDCG@K, AND NDCG@K TESTS
    // =========================================================================

    @Nested
    @DisplayName("Discounted Cumulative Gain & NDCG Evaluation")
    class NdcgTests {

        @Test
        @DisplayName("Should calculate exact DCG@K according to standard formula: SUM((2^rel - 1)/log2(i+1))")
        void shouldCalculateExactDcg() {
            // Grades: [3, 2, 0, 1]
            // i=1 (rank 1): (2^3 - 1) / log2(2) = 7 / 1 = 7.0
            // i=2 (rank 2): (2^2 - 1) / log2(3) = 3 / 1.5849625 = 1.892789
            // i=3 (rank 3): (2^0 - 1) / log2(4) = 0 / 2 = 0.0
            // i=4 (rank 4): (2^1 - 1) / log2(5) = 1 / 2.321928 = 0.430676
            // DCG@1 = 7.0
            // DCG@2 = 7.0 + 1.892789 = 8.892789
            // DCG@3 = 8.892789
            // DCG@4 = 8.892789 + 0.430676 = 9.323465
            List<Integer> grades = List.of(3, 2, 0, 1);

            assertEquals(7.0, RelevanceMetricsCalculator.dcgAtK(grades, 1), 1e-4);
            assertEquals(8.8928, RelevanceMetricsCalculator.dcgAtK(grades, 2), 1e-4);
            assertEquals(8.8928, RelevanceMetricsCalculator.dcgAtK(grades, 3), 1e-4);
            assertEquals(9.3235, RelevanceMetricsCalculator.dcgAtK(grades, 4), 1e-4);
        }

        @Test
        @DisplayName("Should compute IDCG@K by ordering all known positive grades descending")
        void shouldComputeIdcgDescending() {
            // Ground truth grades: 101->1, 102->3, 103->0, 104->2
            // Sorted descending: [3, 2, 1]
            // IDCG@1 = 7.0
            // IDCG@2 = 7.0 + 3/log2(3) = 8.8928
            // IDCG@3 = 8.8928 + 1/log2(4) = 8.8928 + 0.5 = 9.3928
            List<Integer> allGrades = List.of(1, 3, 0, 2);

            assertEquals(7.0, RelevanceMetricsCalculator.idcgAtK(allGrades, 1), 1e-4);
            assertEquals(8.8928, RelevanceMetricsCalculator.idcgAtK(allGrades, 2), 1e-4);
            assertEquals(9.3928, RelevanceMetricsCalculator.idcgAtK(allGrades, 3), 1e-4);
        }

        @Test
        @DisplayName("Should return 1.0 for perfect ranking in NDCG@K")
        void shouldReturnOneForPerfectNdcg() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2, 103L, 1));
            // Retrieved matches ideal ordering
            List<Long> retrieved = List.of(101L, 102L, 103L);

            assertEquals(1.0, RelevanceMetricsCalculator.ndcgAtK(retrieved, query, 3), 1e-6);
            assertEquals(1.0, RelevanceMetricsCalculator.ndcgAtK(retrieved, query, 5), 1e-6);
        }

        @Test
        @DisplayName("Should penalize reversed ranking severely in NDCG@K")
        void shouldPenalizeReversedRanking() {
            // Ground truth: 101->3, 102->2, 103->1
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2, 103L, 1));

            // Inverted order: 103 (grade 1), 102 (grade 2), 101 (grade 3)
            // DCG@3:
            // rank 1 (103): (2^1 - 1)/log2(2) = 1.0
            // rank 2 (102): (2^2 - 1)/log2(3) = 3 / 1.58496 = 1.8928
            // rank 3 (101): (2^3 - 1)/log2(4) = 7 / 2 = 3.5000
            // Total DCG@3 = 1.0 + 1.8928 + 3.5 = 6.3928
            // IDCG@3 = 9.3928
            // NDCG@3 = 6.3928 / 9.3928 = 0.6806
            List<Long> inverted = List.of(103L, 102L, 101L);

            double ndcg = RelevanceMetricsCalculator.ndcgAtK(inverted, query, 3);
            assertEquals(0.6806, ndcg, 1e-4);
            assertTrue(ndcg < 1.0);
        }

        @Test
        @DisplayName("Should return 0.0 when all retrieved documents are completely irrelevant")
        void shouldReturnZeroForIrrelevantList() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3, 102L, 2));
            List<Long> irrelevant = List.of(999L, 998L, 997L);

            assertEquals(0.0, RelevanceMetricsCalculator.ndcgAtK(irrelevant, query, 5), 1e-6);
        }

        @Test
        @DisplayName("Should return 1.0 when no relevant documents exist anywhere (IDCG == 0)")
        void shouldReturnOneWhenNoRelevantDocsExist() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 0, 102L, 0));
            List<Long> retrieved = List.of(101L, 102L);

            assertEquals(1.0, RelevanceMetricsCalculator.ndcgAtK(retrieved, query, 5), 1e-6);
        }
    }

    // =========================================================================
    // 7. EDGE CASE & ROBUSTNESS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Edge Case & Robustness Validation")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should safely handle null and empty inputs across all metrics")
        void shouldHandleNullAndEmptySafely() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3));

            // Null retrieved
            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(null, query, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(null, query, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.f1AtK(null, query, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.reciprocalRank(null, query));
            assertEquals(0.0, RelevanceMetricsCalculator.averagePrecision(null, query));
            assertEquals(0.0, RelevanceMetricsCalculator.ndcgAtK(null, query, 5));

            // Empty retrieved
            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(List.of(), query, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(List.of(), query, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.f1AtK(List.of(), query, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.reciprocalRank(List.of(), query));
            assertEquals(0.0, RelevanceMetricsCalculator.averagePrecision(List.of(), query));
            assertEquals(0.0, RelevanceMetricsCalculator.ndcgAtK(List.of(), query, 5));

            // Null query
            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(List.of(101L), (EvaluationQueryV2) null, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(List.of(101L), (EvaluationQueryV2) null, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.f1AtK(List.of(101L), (EvaluationQueryV2) null, 5));
            assertEquals(0.0, RelevanceMetricsCalculator.reciprocalRank(List.of(101L), (EvaluationQueryV2) null));
            assertEquals(0.0, RelevanceMetricsCalculator.averagePrecision(List.of(101L), (EvaluationQueryV2) null));
            assertEquals(0.0, RelevanceMetricsCalculator.ndcgAtK(List.of(101L), (EvaluationQueryV2) null, 5));

            // Negative or zero K
            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(List.of(101L), query, 0));
            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(List.of(101L), query, -5));
            assertEquals(0.0, RelevanceMetricsCalculator.recallAtK(List.of(101L), query, 0));
            assertEquals(0.0, RelevanceMetricsCalculator.f1AtK(List.of(101L), query, 0));
            assertEquals(0.0, RelevanceMetricsCalculator.ndcgAtK(List.of(101L), query, 0));
        }

        @Test
        @DisplayName("Should treat unjudged documents strictly as non-relevant (grade 0)")
        void shouldTreatUnjudgedAsGradeZero() {
            EvaluationQueryV2 query = createQuery("Q1", Map.of(101L, 3));
            // 999L is not in ground truth map
            List<Long> retrieved = List.of(999L, 101L);

            // Precision@1 should be 0.0 (unjudged 999L is not relevant)
            assertEquals(0.0, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 1), 1e-6);

            // Precision@2 should be 0.5 (101L is relevant)
            assertEquals(0.5, RelevanceMetricsCalculator.precisionAtK(retrieved, query, 2), 1e-6);

            // Reciprocal Rank should be 1/2 = 0.5
            assertEquals(0.5, RelevanceMetricsCalculator.reciprocalRank(retrieved, query), 1e-6);
        }
    }

    // =========================================================================
    // 8. EVALUATION METRICS AGGREGATOR TESTS
    // =========================================================================

    @Nested
    @DisplayName("EvaluationMetricsAggregator Tests")
    class AggregatorTests {

        @Test
        @DisplayName("Should evaluate single query into complete QueryEvaluationMetrics record")
        void shouldEvaluateSingleQuery() {
            EvaluationQueryV2 query = createQuery("Q_TEST", Map.of(101L, 3, 102L, 2, 103L, 1));
            List<Long> retrieved = List.of(101L, 102L, 103L, 104L, 105L);

            EvaluationMetricsAggregator.QueryEvaluationMetrics metrics =
                    EvaluationMetricsAggregator.evaluateQuery(retrieved, query);

            assertEquals("Q_TEST", metrics.queryId());
            assertEquals(EvaluationCategory.EXACT_KEYWORD, metrics.category());
            assertEquals(EvaluationDatasetSplit.DEV, metrics.split());

            // 101 and 102 are relevant (grade >= 2) out of 2 total relevant
            // Top 5: 2 relevant / 5 = 0.4
            assertEquals(0.4, metrics.precisionAt5(), 1e-4);
            // Recall@5: 2 retrieved / 2 total = 1.0
            assertEquals(1.0, metrics.recallAt5(), 1e-4);
            // RR: first relevant at rank 1 = 1.0
            assertEquals(1.0, metrics.reciprocalRank(), 1e-4);
            // AP: P@1=1/1=1.0, P@2=2/2=1.0 -> AP = (1.0 + 1.0)/2 = 1.0
            assertEquals(1.0, metrics.averagePrecision(), 1e-4);
            // NDCG@5: perfect order = 1.0
            assertEquals(1.0, metrics.ndcgAt5(), 1e-4);
        }

        @Test
        @DisplayName("Should compute macro-averaged summary across multiple queries")
        void shouldComputeMacroAveragedSummary() {
            EvaluationQueryV2 q1 = new EvaluationQueryV2("Q1", "text1", EvaluationCategory.EXACT_KEYWORD,
                    EvaluationDatasetSplit.DEV, Map.of(101L, 3), "r1");
            EvaluationQueryV2 q2 = new EvaluationQueryV2("Q2", "text2", EvaluationCategory.EXACT_KEYWORD,
                    EvaluationDatasetSplit.DEV, Map.of(201L, 3), "r2");

            // Q1 retrieved perfect: [101] -> P@5 = 0.2, R@5 = 1.0, RR = 1.0, MAP = 1.0, NDCG@5 = 1.0
            var m1 = EvaluationMetricsAggregator.evaluateQuery(List.of(101L), q1);

            // Q2 retrieved completely irrelevant: [999] -> P@5 = 0.0, R@5 = 0.0, RR = 0.0, MAP = 0.0, NDCG@5 = 0.0
            var m2 = EvaluationMetricsAggregator.evaluateQuery(List.of(999L), q2);

            var summary = EvaluationMetricsAggregator.aggregate(List.of(m1, m2));

            assertEquals(2, summary.queryCount());
            assertEquals(0.1, summary.meanPrecisionAt5(), 1e-4); // (0.2 + 0.0) / 2
            assertEquals(0.5, summary.meanRecallAt5(), 1e-4);    // (1.0 + 0.0) / 2
            assertEquals(0.5, summary.mrr(), 1e-4);              // (1.0 + 0.0) / 2
            assertEquals(0.5, summary.map(), 1e-4);              // (1.0 + 0.0) / 2
            assertEquals(0.5, summary.meanNdcgAt5(), 1e-4);      // (1.0 + 0.0) / 2
        }

        @Test
        @DisplayName("Should aggregate metrics grouped by EvaluationCategory")
        void shouldAggregateByCategory() {
            EvaluationQueryV2 qExact = new EvaluationQueryV2("Q1", "exact", EvaluationCategory.EXACT_KEYWORD,
                    EvaluationDatasetSplit.DEV, Map.of(101L, 3), "r1");
            EvaluationQueryV2 qSynonym = new EvaluationQueryV2("Q2", "synonym", EvaluationCategory.SYNONYM,
                    EvaluationDatasetSplit.DEV, Map.of(201L, 3), "r2");

            var m1 = EvaluationMetricsAggregator.evaluateQuery(List.of(101L), qExact);
            var m2 = EvaluationMetricsAggregator.evaluateQuery(List.of(201L), qSynonym);

            Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> byCategory =
                    EvaluationMetricsAggregator.aggregateByCategory(List.of(m1, m2));

            assertEquals(2, byCategory.size());
            assertTrue(byCategory.containsKey(EvaluationCategory.EXACT_KEYWORD));
            assertTrue(byCategory.containsKey(EvaluationCategory.SYNONYM));
            assertEquals(1, byCategory.get(EvaluationCategory.EXACT_KEYWORD).queryCount());
            assertEquals(1, byCategory.get(EvaluationCategory.SYNONYM).queryCount());
        }

        @Test
        @DisplayName("Should aggregate metrics grouped by EvaluationDatasetSplit")
        void shouldAggregateBySplit() {
            EvaluationQueryV2 qDev = new EvaluationQueryV2("Q1", "dev", EvaluationCategory.EXACT_KEYWORD,
                    EvaluationDatasetSplit.DEV, Map.of(101L, 3), "r1");
            EvaluationQueryV2 qTuning = new EvaluationQueryV2("Q2", "tuning", EvaluationCategory.EXACT_KEYWORD,
                    EvaluationDatasetSplit.TUNING, Map.of(201L, 3), "r2");
            EvaluationQueryV2 qEval = new EvaluationQueryV2("Q3", "eval", EvaluationCategory.EXACT_KEYWORD,
                    EvaluationDatasetSplit.EVAL, Map.of(301L, 3), "r3");

            var m1 = EvaluationMetricsAggregator.evaluateQuery(List.of(101L), qDev);
            var m2 = EvaluationMetricsAggregator.evaluateQuery(List.of(201L), qTuning);
            var m3 = EvaluationMetricsAggregator.evaluateQuery(List.of(301L), qEval);

            Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> bySplit =
                    EvaluationMetricsAggregator.aggregateBySplit(List.of(m1, m2, m3));

            assertEquals(3, bySplit.size());
            assertTrue(bySplit.containsKey(EvaluationDatasetSplit.DEV));
            assertTrue(bySplit.containsKey(EvaluationDatasetSplit.TUNING));
            assertTrue(bySplit.containsKey(EvaluationDatasetSplit.EVAL));
            assertEquals(1, bySplit.get(EvaluationDatasetSplit.DEV).queryCount());
            assertEquals(1, bySplit.get(EvaluationDatasetSplit.TUNING).queryCount());
            assertEquals(1, bySplit.get(EvaluationDatasetSplit.EVAL).queryCount());
        }
    }
}
