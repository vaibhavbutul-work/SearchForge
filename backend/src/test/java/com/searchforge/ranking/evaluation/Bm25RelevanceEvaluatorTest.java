package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.3: BM25 Relevance Evaluation Test Suite")
class Bm25RelevanceEvaluatorTest {

    private static Bm25RelevanceEvaluator evaluator;
    private static Bm25RelevanceEvaluator.Bm25EvaluationResult baselineResult;

    @BeforeAll
    static void setUp() {
        evaluator = new Bm25RelevanceEvaluator();
        baselineResult = evaluator.evaluateAll();
    }

    @Test
    @DisplayName("Should successfully index all 120 evaluation corpus documents")
    void shouldIndexCompleteCorpus() {
        assertEquals(120, evaluator.getIndexedDocumentCount(), "Corpus must index exactly 120 documents");
    }

    @Test
    @DisplayName("Should evaluate all 48 queries with valid metric ranges")
    void shouldEvaluateAllQueriesSuccessfully() {
        assertNotNull(baselineResult);
        assertEquals(48, baselineResult.overallSummary().queryCount(), "Must evaluate exactly 48 queries");

        EvaluationMetricsAggregator.AggregateMetricsSummary overall = baselineResult.overallSummary();

        // Metrics must be strictly within [0.0, 1.0]
        assertTrue(overall.meanPrecisionAt5() >= 0.0 && overall.meanPrecisionAt5() <= 1.0);
        assertTrue(overall.meanPrecisionAt10() >= 0.0 && overall.meanPrecisionAt10() <= 1.0);
        assertTrue(overall.meanRecallAt5() >= 0.0 && overall.meanRecallAt5() <= 1.0);
        assertTrue(overall.meanRecallAt10() >= 0.0 && overall.meanRecallAt10() <= 1.0);
        assertTrue(overall.mrr() >= 0.0 && overall.mrr() <= 1.0);
        assertTrue(overall.map() >= 0.0 && overall.map() <= 1.0);
        assertTrue(overall.meanNdcgAt5() >= 0.0 && overall.meanNdcgAt5() <= 1.0);
        assertTrue(overall.meanNdcgAt10() >= 0.0 && overall.meanNdcgAt10() <= 1.0);

        // Sanity checks: BM25 baseline should have positive overall scores
        assertTrue(overall.mrr() > 0.40, "BM25 MRR baseline should exceed 0.40");
        assertTrue(overall.meanNdcgAt5() > 0.40, "BM25 NDCG@5 baseline should exceed 0.40");
    }

    @Test
    @DisplayName("Should aggregate metrics across all 3 dataset splits (DEV, TUNING, EVAL)")
    void shouldAggregateBySplit() {
        Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> splitSummary =
                baselineResult.splitSummary();

        assertEquals(3, splitSummary.size());
        assertTrue(splitSummary.containsKey(EvaluationDatasetSplit.DEV));
        assertTrue(splitSummary.containsKey(EvaluationDatasetSplit.TUNING));
        assertTrue(splitSummary.containsKey(EvaluationDatasetSplit.EVAL));

        assertEquals(24, splitSummary.get(EvaluationDatasetSplit.DEV).queryCount());
        assertEquals(12, splitSummary.get(EvaluationDatasetSplit.TUNING).queryCount());
        assertEquals(12, splitSummary.get(EvaluationDatasetSplit.EVAL).queryCount());
    }

    @Test
    @DisplayName("Should aggregate metrics across all 8 query categories (6 queries each)")
    void shouldAggregateByCategory() {
        Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> categorySummary =
                baselineResult.categorySummary();

        assertEquals(8, categorySummary.size(), "Must cover all 8 IR query archetypes");

        for (EvaluationCategory cat : EvaluationCategory.values()) {
            assertTrue(categorySummary.containsKey(cat), "Missing category: " + cat);
            assertEquals(6, categorySummary.get(cat).queryCount(), "Category " + cat + " must have 6 queries");
        }
    }

    @Test
    @DisplayName("Should exhibit high NDCG@5 on EXACT_KEYWORD queries")
    void shouldExhibitStrongPerformanceOnExactKeywordQueries() {
        EvaluationMetricsAggregator.AggregateMetricsSummary exactSummary =
                baselineResult.categorySummary().get(EvaluationCategory.EXACT_KEYWORD);

        assertNotNull(exactSummary);
        assertTrue(exactSummary.meanNdcgAt5() >= 0.70,
                "BM25 should achieve high NDCG@5 (>= 0.70) on exact keyword queries");
        assertTrue(exactSummary.mrr() >= 0.80,
                "BM25 should achieve high MRR (>= 0.80) on exact keyword queries");
    }

    @Test
    @DisplayName("Should expose expected BM25 weakness on CONCEPTUAL and AMBIGUOUS queries")
    void shouldExposeWeaknessOnConceptualAndAmbiguousQueries() {
        EvaluationMetricsAggregator.AggregateMetricsSummary exactSummary =
                baselineResult.categorySummary().get(EvaluationCategory.EXACT_KEYWORD);
        EvaluationMetricsAggregator.AggregateMetricsSummary conceptualSummary =
                baselineResult.categorySummary().get(EvaluationCategory.CONCEPTUAL);
        EvaluationMetricsAggregator.AggregateMetricsSummary ambiguousSummary =
                baselineResult.categorySummary().get(EvaluationCategory.AMBIGUOUS);

        assertNotNull(exactSummary);
        assertNotNull(conceptualSummary);
        assertNotNull(ambiguousSummary);

        // Conceptual queries and ambiguous queries show significant degradation under pure lexical BM25
        assertTrue(conceptualSummary.meanNdcgAt5() < exactSummary.meanNdcgAt5(),
                "Conceptual NDCG@5 (" + conceptualSummary.meanNdcgAt5() + ") should be lower than Exact Keyword ("
                        + exactSummary.meanNdcgAt5() + ") due to conceptual vocabulary gap");
        assertTrue(ambiguousSummary.meanNdcgAt5() < exactSummary.meanNdcgAt5(),
                "Ambiguous NDCG@5 (" + ambiguousSummary.meanNdcgAt5() + ") should be lower than Exact Keyword ("
                        + exactSummary.meanNdcgAt5() + ") due to term polysemy");
        assertTrue(conceptualSummary.meanRecallAt5() < 0.80,
                "Conceptual Recall@5 should reflect vocabulary mismatch by dropping below 0.80");
        assertTrue(ambiguousSummary.meanRecallAt5() < 0.80,
                "Ambiguous Recall@5 should reflect polysemy by dropping below 0.80");
    }

    @Test
    @DisplayName("Should output comprehensive BM25 baseline evaluation summary table")
    void shouldPrintComprehensiveMetricsSummary() {
        System.out.println("=========================================================================================");
        System.out.println("                        SEARCHFORGE BM25 BASELINE EVALUATION REPORT                      ");
        System.out.println("=========================================================================================");
        System.out.printf("Parameters: k1=%.2f, b=%.2f | Corpus: %d docs | Queries: %d%n",
                evaluator.getConfig().k1(), evaluator.getConfig().b(), evaluator.getIndexedDocumentCount(),
                baselineResult.overallSummary().queryCount());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("OVERALL MACRO-AVERAGE METRICS:%n");
        System.out.printf("  P@5:    %.4f | P@10:   %.4f%n",
                baselineResult.overallSummary().meanPrecisionAt5(), baselineResult.overallSummary().meanPrecisionAt10());
        System.out.printf("  R@5:    %.4f | R@10:   %.4f%n",
                baselineResult.overallSummary().meanRecallAt5(), baselineResult.overallSummary().meanRecallAt10());
        System.out.printf("  F1@5:   %.4f | F1@10:  %.4f%n",
                baselineResult.overallSummary().meanF1At5(), baselineResult.overallSummary().meanF1At10());
        System.out.printf("  MRR:    %.4f | MAP:    %.4f%n",
                baselineResult.overallSummary().mrr(), baselineResult.overallSummary().map());
        System.out.printf("  NDCG@5: %.4f | NDCG@10:%.4f%n",
                baselineResult.overallSummary().meanNdcgAt5(), baselineResult.overallSummary().meanNdcgAt10());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("BREAKDOWN BY QUERY CATEGORY:");
        System.out.printf("%-18s | %-4s | %-6s | %-6s | %-6s | %-6s | %-6s | %-6s%n",
                "Category", "N", "P@5", "R@5", "MRR", "MAP", "NDCG@5", "NDCG10");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (Map.Entry<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> entry :
                baselineResult.categorySummary().entrySet()) {
            var s = entry.getValue();
            System.out.printf("%-18s | %-4d | %-6.4f | %-6.4f | %-6.4f | %-6.4f | %-6.4f | %-6.4f%n",
                    entry.getKey().name(), s.queryCount(), s.meanPrecisionAt5(), s.meanRecallAt5(),
                    s.mrr(), s.map(), s.meanNdcgAt5(), s.meanNdcgAt10());
        }
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("BREAKDOWN BY DATASET SPLIT:");
        for (Map.Entry<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> entry :
                baselineResult.splitSummary().entrySet()) {
            var s = entry.getValue();
            System.out.printf("%-18s | N=%-2d | P@5=%.4f | R@5=%.4f | MRR=%.4f | MAP=%.4f | NDCG@5=%.4f%n",
                    entry.getKey().name(), s.queryCount(), s.meanPrecisionAt5(), s.meanRecallAt5(),
                    s.mrr(), s.map(), s.meanNdcgAt5());
        }
        System.out.println("=========================================================================================");
    }
}
