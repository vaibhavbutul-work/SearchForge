package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 12.8: Search Quality Regression Test Suite.
 *
 * Enforces hard ranking quality invariants across BM25 Lexical, Dense Semantic,
 * and Multi-Modal Hybrid retrieval pipelines.
 *
 * Validates that:
 * 1. Production Hybrid Search satisfies all quality floors (NDCG@10, NDCG@5, MAP, MRR, P@5).
 * 2. Lexical BM25 satisfies all quality floors (NDCG@10, MAP, MRR).
 * 3. Dense Semantic Retrieval satisfies all quality floors (NDCG@10, MRR).
 * 4. Critical query categories (Exact Keyword, Ambiguity) do not degrade.
 * 5. Intentional ranking corruption (reversed ranks, random shuffle, irrelevant docs)
 *    is reliably detected and halts the gate.
 */
@Tag("regression")
@Tag("evaluation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 12.8: Search Quality Regression Test Suite")
class SearchQualityRegressionSuiteTest {

    private static final Logger log = LoggerFactory.getLogger(SearchQualityRegressionSuiteTest.class);

    private static SearchQualityRegressionSuite regressionSuite;
    private static SearchQualityRegressionSuite.RegressionVerificationReport baselineReport;

    @BeforeAll
    static void setUpAll() {
        log.info("Initializing SearchQualityRegressionSuite and running baseline evaluation...");
        regressionSuite = new SearchQualityRegressionSuite();
        baselineReport = regressionSuite.runRegressionVerification();
        baselineReport.printSummary();
    }

    @Test
    @Order(1)
    @DisplayName("Gate 1: Verify Production Hybrid Search Quality Invariants")
    void verifyGate1HybridSearchQualityFloors() {
        var hyb = baselineReport.underlyingResults().hybrid().overallSummary();

        assertAll("Hybrid Quality Floors",
                () -> assertTrue(hyb.meanNdcgAt10() >= SearchQualityRegressionSuite.HYBRID_MIN_NDCG_10,
                        String.format("Hybrid NDCG@10 (%.4f) fell below floor (%.4f)",
                                hyb.meanNdcgAt10(), SearchQualityRegressionSuite.HYBRID_MIN_NDCG_10)),
                () -> assertTrue(hyb.meanNdcgAt5() >= SearchQualityRegressionSuite.HYBRID_MIN_NDCG_5,
                        String.format("Hybrid NDCG@5 (%.4f) fell below floor (%.4f)",
                                hyb.meanNdcgAt5(), SearchQualityRegressionSuite.HYBRID_MIN_NDCG_5)),
                () -> assertTrue(hyb.map() >= SearchQualityRegressionSuite.HYBRID_MIN_MAP,
                        String.format("Hybrid MAP (%.4f) fell below floor (%.4f)",
                                hyb.map(), SearchQualityRegressionSuite.HYBRID_MIN_MAP)),
                () -> assertTrue(hyb.mrr() >= SearchQualityRegressionSuite.HYBRID_MIN_MRR,
                        String.format("Hybrid MRR (%.4f) fell below floor (%.4f)",
                                hyb.mrr(), SearchQualityRegressionSuite.HYBRID_MIN_MRR)),
                () -> assertTrue(hyb.meanPrecisionAt5() >= SearchQualityRegressionSuite.HYBRID_MIN_PREC_5,
                        String.format("Hybrid P@5 (%.4f) fell below floor (%.4f)",
                                hyb.meanPrecisionAt5(), SearchQualityRegressionSuite.HYBRID_MIN_PREC_5))
        );
    }

    @Test
    @Order(2)
    @DisplayName("Gate 2: Verify Lexical BM25 Retrieval Quality Invariants")
    void verifyGate2Bm25QualityFloors() {
        var bm25 = baselineReport.underlyingResults().bm25().overallSummary();

        assertAll("BM25 Quality Floors",
                () -> assertTrue(bm25.meanNdcgAt10() >= SearchQualityRegressionSuite.BM25_MIN_NDCG_10,
                        String.format("BM25 NDCG@10 (%.4f) fell below floor (%.4f)",
                                bm25.meanNdcgAt10(), SearchQualityRegressionSuite.BM25_MIN_NDCG_10)),
                () -> assertTrue(bm25.map() >= SearchQualityRegressionSuite.BM25_MIN_MAP,
                        String.format("BM25 MAP (%.4f) fell below floor (%.4f)",
                                bm25.map(), SearchQualityRegressionSuite.BM25_MIN_MAP)),
                () -> assertTrue(bm25.mrr() >= SearchQualityRegressionSuite.BM25_MIN_MRR,
                        String.format("BM25 MRR (%.4f) fell below floor (%.4f)",
                                bm25.mrr(), SearchQualityRegressionSuite.BM25_MIN_MRR))
        );
    }

    @Test
    @Order(3)
    @DisplayName("Gate 3: Verify Dense Semantic Retrieval Quality Invariants")
    void verifyGate3SemanticQualityFloors() {
        var sem = baselineReport.underlyingResults().semantic().overallSummary();

        assertAll("Semantic Quality Floors",
                () -> assertTrue(sem.meanNdcgAt10() >= SearchQualityRegressionSuite.SEMANTIC_MIN_NDCG_10,
                        String.format("Semantic NDCG@10 (%.4f) fell below floor (%.4f)",
                                sem.meanNdcgAt10(), SearchQualityRegressionSuite.SEMANTIC_MIN_NDCG_10)),
                () -> assertTrue(sem.mrr() >= SearchQualityRegressionSuite.SEMANTIC_MIN_MRR,
                        String.format("Semantic MRR (%.4f) fell below floor (%.4f)",
                                sem.mrr(), SearchQualityRegressionSuite.SEMANTIC_MIN_MRR))
        );
    }

    @Test
    @Order(4)
    @DisplayName("Gate 4: Verify Category-Specific Quality Invariants (Exact Keyword & Ambiguity)")
    void verifyGate4CategorySpecificInvariants() {
        var catAgg = baselineReport.underlyingResults().hybrid().categorySummary();

        double exactKeywordNdcg5 = catAgg.containsKey(EvaluationCategory.EXACT_KEYWORD)
                ? catAgg.get(EvaluationCategory.EXACT_KEYWORD).meanNdcgAt5() : 0.0;
        double ambiguousNdcg5 = catAgg.containsKey(EvaluationCategory.AMBIGUOUS)
                ? catAgg.get(EvaluationCategory.AMBIGUOUS).meanNdcgAt5() : 0.0;

        assertAll("Category Invariants",
                () -> assertTrue(exactKeywordNdcg5 >= SearchQualityRegressionSuite.HYBRID_MIN_EXACT_KEYWORD_NDCG_5,
                        String.format("Hybrid EXACT_KEYWORD NDCG@5 (%.4f) fell below floor (%.4f)",
                                exactKeywordNdcg5, SearchQualityRegressionSuite.HYBRID_MIN_EXACT_KEYWORD_NDCG_5)),
                () -> assertTrue(ambiguousNdcg5 >= SearchQualityRegressionSuite.HYBRID_MIN_AMBIGUOUS_NDCG_5,
                        String.format("Hybrid AMBIGUOUS NDCG@5 (%.4f) fell below floor (%.4f)",
                                ambiguousNdcg5, SearchQualityRegressionSuite.HYBRID_MIN_AMBIGUOUS_NDCG_5))
        );
    }

    @Test
    @Order(5)
    @DisplayName("Gate 5: Verify Intentional Ranking Degradation Fails Regression Check (Mutation Testing)")
    void verifyGate5IntentionalCorruptionFailsRegressionGate() {
        // Test 5A: Reversed Rankings
        SearchQualityRegressionSuite.RegressionVerificationReport reversedReport =
                regressionSuite.evaluateCorruptedRankings(SearchQualityRegressionSuite.RankingCorruptionType.REVERSED_RANKINGS);
        assertFalse(reversedReport.allChecksPassed(),
                "Regression gate MUST fail when document rankings are intentionally reversed!");
        List<SearchQualityRegressionSuite.QualityCheckResult> failedReversed = reversedReport.failedChecks();
        assertFalse(failedReversed.isEmpty(), "Reversed rankings must produce failed checks");
        log.info("Reversed rankings triggered {} failed quality checks as expected.", failedReversed.size());

        // Test 5B: Shuffled Rankings
        SearchQualityRegressionSuite.RegressionVerificationReport shuffledReport =
                regressionSuite.evaluateCorruptedRankings(SearchQualityRegressionSuite.RankingCorruptionType.SHUFFLED_RANKINGS);
        assertFalse(shuffledReport.allChecksPassed(),
                "Regression gate MUST fail when document rankings are intentionally shuffled!");
        log.info("Shuffled rankings triggered {} failed quality checks as expected.", shuffledReport.failedChecks().size());

        // Test 5C: Arbitrary Irrelevant Documents
        SearchQualityRegressionSuite.RegressionVerificationReport irrelevantReport =
                regressionSuite.evaluateCorruptedRankings(SearchQualityRegressionSuite.RankingCorruptionType.ARBITRARY_IRRELEVANT);
        assertFalse(irrelevantReport.allChecksPassed(),
                "Regression gate MUST fail when arbitrary irrelevant documents are returned!");
        log.info("Arbitrary irrelevant documents triggered {} failed quality checks as expected.", irrelevantReport.failedChecks().size());
    }

    @Test
    @Order(6)
    @DisplayName("Overall: End-to-End Regression Verification Report Status")
    void verifyOverallRegressionReportStatus() {
        assertTrue(baselineReport.allChecksPassed(),
                () -> "Search quality regression verification failed on baseline system! Failed checks: "
                        + baselineReport.failedChecks());
        assertEquals(0, baselineReport.failedChecks().size(), "There should be zero failed quality checks on baseline");
    }
}
