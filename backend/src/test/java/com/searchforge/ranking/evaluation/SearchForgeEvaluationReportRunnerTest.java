package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 12.9: Test verification for the SearchForge Master Evaluation Report Runner.
 */
@Tag("evaluation")
@DisplayName("Task 12.9: Master Evaluation Report Runner Test")
class SearchForgeEvaluationReportRunnerTest {

    @Test
    @DisplayName("Should generate complete master evaluation report and pass production grade criteria")
    void shouldGenerateMasterEvaluationReport() {
        SearchForgeEvaluationReportRunner runner = new SearchForgeEvaluationReportRunner();
        SearchForgeEvaluationReportRunner.MasterEvaluationSummary summary = runner.generateMasterReport();

        assertNotNull(summary, "Master evaluation summary must not be null");
        assertNotNull(summary.results(), "Underlying results must not be null");

        // Verify query counts
        assertEquals(48, summary.results().bm25().overallSummary().queryCount(), "BM25 must evaluate all 48 queries");
        assertEquals(48, summary.results().semantic().overallSummary().queryCount(), "Semantic must evaluate all 48 queries");
        assertEquals(48, summary.results().hybrid().overallSummary().queryCount(), "Hybrid must evaluate all 48 queries");

        // Verify production grade certification
        assertTrue(summary.isProductionGrade(),
                "SearchForge hybrid search must satisfy production grade criteria (NDCG@5 >= 0.90, NDCG@10 >= 0.92, MRR >= 0.95)");

        // Verify Hybrid achieves the highest NDCG@5
        double hybNdcg5 = summary.results().hybrid().overallSummary().meanNdcgAt5();
        double bm25Ndcg5 = summary.results().bm25().overallSummary().meanNdcgAt5();
        double semNdcg5 = summary.results().semantic().overallSummary().meanNdcgAt5();

        if (summary.isNeural()) {
            assertTrue(hybNdcg5 >= bm25Ndcg5, "Hybrid NDCG@5 must be higher than or equal to BM25");
            assertTrue(hybNdcg5 >= semNdcg5, "Hybrid NDCG@5 must be higher than or equal to Semantic");
        } else {
            assertTrue(hybNdcg5 >= 0.80, "Hybrid NDCG@5 must be at least 0.80 in hermetic fallback mode");
        }
    }
}
