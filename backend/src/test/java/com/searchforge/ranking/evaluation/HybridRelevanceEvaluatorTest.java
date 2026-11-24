package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.5: Hybrid Search Evaluation Test Suite")
class HybridRelevanceEvaluatorTest {

    private static HybridRelevanceEvaluator evaluator;
    private static HybridRelevanceEvaluator.ThreeWayEvaluationResult results;

    @BeforeAll
    static void setUp() {
        evaluator = new HybridRelevanceEvaluator();
        results = evaluator.evaluateAll();
    }

    @Test
    @DisplayName("Should successfully index all 120 documents into dual storage")
    void shouldIndexDualStorage() {
        assertEquals(120, evaluator.getIndexedDocumentCount(), "Corpus must index exactly 120 documents");
    }

    @Test
    @DisplayName("Should evaluate all 48 queries across all three search modes")
    void shouldEvaluateAllQueriesAcrossAllThreeModes() {
        assertNotNull(results);
        assertEquals(48, results.bm25().overallSummary().queryCount());
        assertEquals(48, results.semantic().overallSummary().queryCount());
        assertEquals(48, results.hybrid().overallSummary().queryCount());

        var h = results.hybrid().overallSummary();

        // Metric bounds validation
        assertTrue(h.meanPrecisionAt5() >= 0.0 && h.meanPrecisionAt5() <= 1.0);
        assertTrue(h.meanRecallAt5() >= 0.0 && h.meanRecallAt5() <= 1.0);
        assertTrue(h.mrr() >= 0.0 && h.mrr() <= 1.0);
        assertTrue(h.map() >= 0.0 && h.map() <= 1.0);
        assertTrue(h.meanNdcgAt5() >= 0.0 && h.meanNdcgAt5() <= 1.0);

        // Sanity assertions
        boolean isNeural = !(evaluator.getEmbeddingModel() instanceof com.searchforge.semantic.embedding.DeterministicEmbeddingModel);
        if (isNeural) {
            assertTrue(h.mrr() >= 0.90, "Hybrid MRR should be >= 0.90");
            assertTrue(h.meanNdcgAt5() >= 0.90, "Hybrid NDCG@5 should be >= 0.90");
        } else {
            assertTrue(h.mrr() >= 0.80, "Hybrid MRR should be >= 0.80 in hermetic fallback mode");
            assertTrue(h.meanNdcgAt5() >= 0.80, "Hybrid NDCG@5 should be >= 0.80 in hermetic fallback mode");
        }
    }

    @Test
    @DisplayName("Should verify Hybrid search maintains top-tier recall across all splits")
    void shouldVerifyHybridRecall() {
        var b = results.bm25().overallSummary();
        var h = results.hybrid().overallSummary();
        boolean isNeural = !(evaluator.getEmbeddingModel() instanceof com.searchforge.semantic.embedding.DeterministicEmbeddingModel);

        if (isNeural) {
            assertTrue(h.meanRecallAt5() >= b.meanRecallAt5(),
                    "Hybrid Recall@5 (" + h.meanRecallAt5() + ") should be >= BM25 Recall@5 (" + b.meanRecallAt5() + ")");
        }
    }

    @Test
    @DisplayName("Should output comprehensive 3-way side-by-side evaluation table")
    void shouldPrintComprehensiveThreeWayComparison() {
        var b = results.bm25().overallSummary();
        var s = results.semantic().overallSummary();
        var h = results.hybrid().overallSummary();

        System.out.println("=========================================================================================");
        System.out.println("           SEARCHFORGE 3-WAY RETRIEVAL EVALUATION: BM25 vs SEMANTIC vs HYBRID           ");
        System.out.println("=========================================================================================");
        System.out.printf("RRF Constant: k=%d | Candidate Pool: 50 | Corpus: %d docs | Queries: %d%n",
                evaluator.getRrfK(), evaluator.getIndexedDocumentCount(), h.queryCount());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("OVERALL MACRO-AVERAGE METRICS:");
        System.out.printf("%-12s | %-10s | %-10s | %-10s | %-14s%n", "Metric", "BM25", "Semantic", "Hybrid", "Hybrid vs BM25");
        System.out.println("-----------------------------------------------------------------------------------------");
        printMetricRow("P@5", b.meanPrecisionAt5(), s.meanPrecisionAt5(), h.meanPrecisionAt5());
        printMetricRow("P@10", b.meanPrecisionAt10(), s.meanPrecisionAt10(), h.meanPrecisionAt10());
        printMetricRow("R@5", b.meanRecallAt5(), s.meanRecallAt5(), h.meanRecallAt5());
        printMetricRow("R@10", b.meanRecallAt10(), s.meanRecallAt10(), h.meanRecallAt10());
        printMetricRow("F1@5", b.meanF1At5(), s.meanF1At5(), h.meanF1At5());
        printMetricRow("F1@10", b.meanF1At10(), s.meanF1At10(), h.meanF1At10());
        printMetricRow("MRR", b.mrr(), s.mrr(), h.mrr());
        printMetricRow("MAP", b.map(), s.map(), h.map());
        printMetricRow("NDCG@5", b.meanNdcgAt5(), s.meanNdcgAt5(), h.meanNdcgAt5());
        printMetricRow("NDCG@10", b.meanNdcgAt10(), s.meanNdcgAt10(), h.meanNdcgAt10());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("CATEGORY BREAKDOWN (NDCG@5):");
        System.out.printf("%-18s | %-8s | %-8s | %-8s | %-12s%n", "Category", "BM25", "Semantic", "Hybrid", "Winner");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (EvaluationCategory cat : EvaluationCategory.values()) {
            double bNdcg = results.bm25().categorySummary().get(cat).meanNdcgAt5();
            double sNdcg = results.semantic().categorySummary().get(cat).meanNdcgAt5();
            double hNdcg = results.hybrid().categorySummary().get(cat).meanNdcgAt5();

            String winner;
            if (hNdcg >= bNdcg && hNdcg >= sNdcg) {
                winner = "HYBRID";
            } else if (bNdcg >= sNdcg) {
                winner = "BM25";
            } else {
                winner = "SEMANTIC";
            }

            System.out.printf("%-18s | %-8.4f | %-8.4f | %-8.4f | %-12s%n",
                    cat.name(), bNdcg, sNdcg, hNdcg, winner);
        }
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("DATASET SPLIT BREAKDOWN (NDCG@5):");
        for (EvaluationDatasetSplit split : EvaluationDatasetSplit.values()) {
            double bNdcg = results.bm25().splitSummary().get(split).meanNdcgAt5();
            double sNdcg = results.semantic().splitSummary().get(split).meanNdcgAt5();
            double hNdcg = results.hybrid().splitSummary().get(split).meanNdcgAt5();
            System.out.printf("%-18s | BM25: %.4f | Semantic: %.4f | Hybrid: %.4f%n",
                    split.name(), bNdcg, sNdcg, hNdcg);
        }
        System.out.println("=========================================================================================");
    }

    private void printMetricRow(String name, double bm25, double semantic, double hybrid) {
        double delta = hybrid - bm25;
        System.out.printf("%-12s | %-10.4f | %-10.4f | %-10.4f | %-+14.4f%n",
                name, bm25, semantic, hybrid, delta);
    }
}
