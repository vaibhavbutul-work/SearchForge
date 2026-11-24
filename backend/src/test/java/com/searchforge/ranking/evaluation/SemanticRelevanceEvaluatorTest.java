package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.4: Semantic Search Evaluation Test Suite")
class SemanticRelevanceEvaluatorTest {

    private static SemanticRelevanceEvaluator semanticEvaluator;
    private static SemanticRelevanceEvaluator.SemanticEvaluationResult semanticResult;
    private static Bm25RelevanceEvaluator.Bm25EvaluationResult bm25Result;

    @BeforeAll
    static void setUp() {
        semanticEvaluator = new SemanticRelevanceEvaluator();
        semanticResult = semanticEvaluator.evaluateAll();

        // Also evaluate BM25 to produce direct side-by-side comparison data
        Bm25RelevanceEvaluator bm25Evaluator = new Bm25RelevanceEvaluator();
        bm25Result = bm25Evaluator.evaluateAll();
    }

    @Test
    @DisplayName("Should successfully vectorize and store all 120 evaluation corpus documents")
    void shouldIndexCompleteCorpus() {
        assertEquals(120, semanticEvaluator.getIndexedDocumentCount(), "Corpus must index exactly 120 document vectors");
    }

    @Test
    @DisplayName("Should evaluate all 48 queries with valid metric ranges")
    void shouldEvaluateAllQueriesSuccessfully() {
        assertNotNull(semanticResult);
        assertEquals(48, semanticResult.overallSummary().queryCount(), "Must evaluate exactly 48 queries");

        EvaluationMetricsAggregator.AggregateMetricsSummary overall = semanticResult.overallSummary();

        // Metrics must be strictly within [0.0, 1.0]
        assertTrue(overall.meanPrecisionAt5() >= 0.0 && overall.meanPrecisionAt5() <= 1.0);
        assertTrue(overall.meanPrecisionAt10() >= 0.0 && overall.meanPrecisionAt10() <= 1.0);
        assertTrue(overall.meanRecallAt5() >= 0.0 && overall.meanRecallAt5() <= 1.0);
        assertTrue(overall.meanRecallAt10() >= 0.0 && overall.meanRecallAt10() <= 1.0);
        assertTrue(overall.mrr() >= 0.0 && overall.mrr() <= 1.0);
        assertTrue(overall.map() >= 0.0 && overall.map() <= 1.0);
        assertTrue(overall.meanNdcgAt5() >= 0.0 && overall.meanNdcgAt5() <= 1.0);
        assertTrue(overall.meanNdcgAt10() >= 0.0 && overall.meanNdcgAt10() <= 1.0);

        // Sanity check: Semantic search should have positive baseline scores
        assertTrue(overall.mrr() > 0.40, "Semantic MRR baseline should exceed 0.40");
        assertTrue(overall.meanNdcgAt5() > 0.40, "Semantic NDCG@5 baseline should exceed 0.40");
    }

    @Test
    @DisplayName("Should aggregate metrics across all 3 dataset splits (DEV, TUNING, EVAL)")
    void shouldAggregateBySplit() {
        Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> splitSummary =
                semanticResult.splitSummary();

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
                semanticResult.categorySummary();

        assertEquals(8, categorySummary.size(), "Must cover all 8 IR query archetypes");

        for (EvaluationCategory cat : EvaluationCategory.values()) {
            assertTrue(categorySummary.containsKey(cat), "Missing category: " + cat);
            assertEquals(6, categorySummary.get(cat).queryCount(), "Category " + cat + " must have 6 queries");
        }
    }

    @Test
    @DisplayName("Should output comprehensive side-by-side BM25 vs Semantic evaluation report")
    void shouldPrintComprehensiveMetricsSummary() {
        var sOverall = semanticResult.overallSummary();
        var bOverall = bm25Result.overallSummary();

        System.out.println("=========================================================================================");
        System.out.println("                 SEARCHFORGE SEMANTIC SEARCH EVALUATION & BM25 COMPARISON                ");
        System.out.println("=========================================================================================");
        System.out.printf("Model: %s (%d-D) | Corpus: %d docs | Queries: %d%n",
                semanticResult.modelName(), semanticResult.dimensions(),
                semanticEvaluator.getIndexedDocumentCount(), sOverall.queryCount());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("OVERALL MACRO-AVERAGE COMPARISON:");
        System.out.printf("%-12s | %-10s | %-10s | %-12s%n", "Metric", "BM25", "Semantic", "Delta");
        System.out.println("-----------------------------------------------------------------------------------------");
        printMetricRow("P@5", bOverall.meanPrecisionAt5(), sOverall.meanPrecisionAt5());
        printMetricRow("P@10", bOverall.meanPrecisionAt10(), sOverall.meanPrecisionAt10());
        printMetricRow("R@5", bOverall.meanRecallAt5(), sOverall.meanRecallAt5());
        printMetricRow("R@10", bOverall.meanRecallAt10(), sOverall.meanRecallAt10());
        printMetricRow("F1@5", bOverall.meanF1At5(), sOverall.meanF1At5());
        printMetricRow("F1@10", bOverall.meanF1At10(), sOverall.meanF1At10());
        printMetricRow("MRR", bOverall.mrr(), sOverall.mrr());
        printMetricRow("MAP", bOverall.map(), sOverall.map());
        printMetricRow("NDCG@5", bOverall.meanNdcgAt5(), sOverall.meanNdcgAt5());
        printMetricRow("NDCG@10", bOverall.meanNdcgAt10(), sOverall.meanNdcgAt10());
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("HEAD-TO-HEAD CATEGORY BREAKDOWN (NDCG@5):");
        System.out.printf("%-18s | %-8s | %-8s | %-10s | %-10s%n", "Category", "BM25", "Semantic", "Delta", "Winner");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (EvaluationCategory cat : EvaluationCategory.values()) {
            double bNdcg = bm25Result.categorySummary().get(cat).meanNdcgAt5();
            double sNdcg = semanticResult.categorySummary().get(cat).meanNdcgAt5();
            double delta = sNdcg - bNdcg;
            String winner = Math.abs(delta) < 0.005 ? "TIE" : (delta > 0 ? "SEMANTIC" : "BM25");
            System.out.printf("%-18s | %-8.4f | %-8.4f | %-+10.4f | %-10s%n",
                    cat.name(), bNdcg, sNdcg, delta, winner);
        }
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("DATASET SPLIT BREAKDOWN (NDCG@5):");
        for (EvaluationDatasetSplit split : EvaluationDatasetSplit.values()) {
            double bNdcg = bm25Result.splitSummary().get(split).meanNdcgAt5();
            double sNdcg = semanticResult.splitSummary().get(split).meanNdcgAt5();
            System.out.printf("%-18s | BM25: %.4f | Semantic: %.4f | Delta: %-+8.4f%n",
                    split.name(), bNdcg, sNdcg, sNdcg - bNdcg);
        }
        System.out.println("=========================================================================================");
    }

    private void printMetricRow(String name, double bm25, double semantic) {
        double delta = semantic - bm25;
        System.out.printf("%-12s | %-10.4f | %-10.4f | %-+12.4f%n", name, bm25, semantic, delta);
    }
}
