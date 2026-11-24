package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.6: Ranking Optimization & Parameter Tuning Test Suite")
class RankingTuningExperimentRunnerTest {

    private static RankingTuningExperimentRunner experimentRunner;
    private static RankingTuningExperimentRunner.FullTuningExperimentReport experimentReport;

    @BeforeAll
    static void setUp() {
        experimentRunner = new RankingTuningExperimentRunner();
        experimentReport = experimentRunner.runFullTuningExperiment();
    }

    @Test
    @DisplayName("Should execute all BM25 and Hybrid parameter tuning trials")
    void shouldExecuteFullTuningGridSuccessfully() {
        assertNotNull(experimentReport);
        assertTrue(experimentReport.bm25Trials().size() >= 8, "Must execute at least 8 BM25 trials");
        assertTrue(experimentReport.hybridTrials().size() >= 9, "Must execute at least 9 Hybrid trials");
        assertNotNull(experimentReport.winningBm25());
        assertNotNull(experimentReport.winningHybrid());
        assertNotNull(experimentReport.evalBaseline());
        assertNotNull(experimentReport.evalOptimized());
    }

    @Test
    @DisplayName("Should maintain valid metric ranges across all experimental trials")
    void shouldValidateMetricBounds() {
        for (var trial : experimentReport.bm25Trials()) {
            var s = trial.summary();
            assertTrue(s.meanNdcgAt5() >= 0.0 && s.meanNdcgAt5() <= 1.0);
            assertTrue(s.mrr() >= 0.0 && s.mrr() <= 1.0);
            assertTrue(trial.elapsedMs() >= 0);
        }

        for (var trial : experimentReport.hybridTrials()) {
            var s = trial.summary();
            assertTrue(s.meanNdcgAt5() >= 0.0 && s.meanNdcgAt5() <= 1.0);
            assertTrue(s.mrr() >= 0.0 && s.mrr() <= 1.0);
            assertTrue(trial.elapsedMs() >= 0);
        }
    }

    @Test
    @DisplayName("Should verify Holdout EVAL split is evaluated properly")
    void shouldVerifyHoldoutEvaluationIntegrity() {
        var base = experimentReport.evalBaseline();
        var opt = experimentReport.evalOptimized();

        assertEquals(EvaluationDatasetSplit.EVAL, base.split());
        assertEquals(EvaluationDatasetSplit.EVAL, opt.split());
        assertEquals(12, base.summary().queryCount());
        assertEquals(12, opt.summary().queryCount());
    }

    @Test
    @DisplayName("Should print comprehensive parameter tuning experiment table")
    void shouldPrintFullTuningReport() {
        System.out.println("=========================================================================================");
        System.out.println("                SEARCHFORGE RANKING OPTIMIZATION & PARAMETER TUNING                      ");
        System.out.println("=========================================================================================");
        System.out.println("PART 1: BM25 PARAMETER GRID (Evaluated on TUNING split, N=12)");
        System.out.printf("%-14s | %-24s | %-6s | %-6s | %-6s | %-6s | %-6s | %-5s%n",
                "Experiment ID", "Configuration", "P@5", "R@5", "MRR", "MAP", "NDCG@5", "Time");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (var trial : experimentReport.bm25Trials()) {
            var s = trial.summary();
            System.out.printf("%-14s | %-24s | %-6.4f | %-6.4f | %-6.4f | %-6.4f | %-6.4f | %-4dms%n",
                    trial.experimentId(), trial.configDescription(),
                    s.meanPrecisionAt5(), s.meanRecallAt5(), s.mrr(), s.map(), s.meanNdcgAt5(), trial.elapsedMs());
        }
        System.out.printf(">>> BM25 Winning Configuration on TUNING split: %s (NDCG@5 = %.4f)%n",
                experimentReport.winningBm25().experimentId(), experimentReport.winningBm25().summary().meanNdcgAt5());

        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("PART 2: HYBRID RRF PARAMETER GRID (Evaluated on TUNING split, N=12)");
        System.out.printf("%-14s | %-46s | %-6s | %-6s | %-6s | %-6s%n",
                "Experiment ID", "Configuration Description", "P@5", "R@5", "MRR", "NDCG@5");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (var trial : experimentReport.hybridTrials()) {
            var s = trial.summary();
            System.out.printf("%-14s | %-46s | %-6.4f | %-6.4f | %-6.4f | %-6.4f%n",
                    trial.experimentId(), trial.configDescription(),
                    s.meanPrecisionAt5(), s.meanRecallAt5(), s.mrr(), s.meanNdcgAt5());
        }
        System.out.printf(">>> Hybrid Winning Configuration on TUNING split: %s (NDCG@5 = %.4f)%n",
                experimentReport.winningHybrid().experimentId(), experimentReport.winningHybrid().summary().meanNdcgAt5());

        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.println("PART 3: UNBIASED VALIDATION ON HOLDOUT EVAL SPLIT (N=12)");
        var base = experimentReport.evalBaseline().summary();
        var opt = experimentReport.evalOptimized().summary();
        System.out.printf("%-16s | %-10s | %-10s | %-10s | %-12s%n", "Metric", "Baseline", "Optimized", "Delta", "Observation");
        System.out.println("-----------------------------------------------------------------------------------------");
        printEvalRow("Precision@5", base.meanPrecisionAt5(), opt.meanPrecisionAt5());
        printEvalRow("Precision@10", base.meanPrecisionAt10(), opt.meanPrecisionAt10());
        printEvalRow("Recall@5", base.meanRecallAt5(), opt.meanRecallAt5());
        printEvalRow("Recall@10", base.meanRecallAt10(), opt.meanRecallAt10());
        printEvalRow("MRR", base.mrr(), opt.mrr());
        printEvalRow("MAP", base.map(), opt.map());
        printEvalRow("NDCG@5", base.meanNdcgAt5(), opt.meanNdcgAt5());
        printEvalRow("NDCG@10", base.meanNdcgAt10(), opt.meanNdcgAt10());
        System.out.println("=========================================================================================");
    }

    private void printEvalRow(String name, double b, double o) {
        double delta = o - b;
        String status = Math.abs(delta) < 0.001 ? "NEUTRAL" : (delta > 0 ? "IMPROVED" : "REGRESSED");
        System.out.printf("%-16s | %-10.4f | %-10.4f | %-+10.4f | %-12s%n", name, b, o, delta, status);
    }
}
