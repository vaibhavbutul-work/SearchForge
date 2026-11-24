package com.searchforge.ranking.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Task 12.8: Search Quality Regression Suite.
 *
 * Enforces automated relevance quality invariants across Lexical (BM25),
 * Dense Semantic, and Multi-Modal Hybrid retrieval pipelines.
 *
 * Prevents silent ranking regressions by evaluating candidate results against
 * the standardized 48-query benchmark and comparing observed metrics against
 * established regression floor thresholds.
 */
public class SearchQualityRegressionSuite {

    private static final Logger log = LoggerFactory.getLogger(SearchQualityRegressionSuite.class);

    // =========================================================================
    // REGRESSION BASELINE THRESHOLDS (QUALITY GATES)
    // =========================================================================

    // Gate 1: Hybrid Retrieval Quality Floors (Production Engine)
    public static final double HYBRID_MIN_NDCG_10 = 0.9300;
    public static final double HYBRID_MIN_NDCG_5  = 0.9100;
    public static final double HYBRID_MIN_MAP      = 0.9000;
    public static final double HYBRID_MIN_MRR      = 0.9500;
    public static final double HYBRID_MIN_PREC_5   = 0.2800;

    // Gate 2: Lexical BM25 Retrieval Quality Floors
    public static final double BM25_MIN_NDCG_10   = 0.8900;
    public static final double BM25_MIN_MAP        = 0.8600;
    public static final double BM25_MIN_MRR        = 0.9400;

    // Gate 3: Dense Semantic Retrieval Quality Floors
    public static final double SEMANTIC_MIN_NDCG_10 = 0.9200;
    public static final double SEMANTIC_MIN_MRR     = 0.9500;

    // Gate 4: Category-Specific Quality Invariants
    public static final double HYBRID_MIN_EXACT_KEYWORD_NDCG_5 = 0.9500;
    public static final double HYBRID_MIN_AMBIGUOUS_NDCG_5     = 0.8800;

    private final HybridRelevanceEvaluator evaluator;

    public SearchQualityRegressionSuite() {
        this(new HybridRelevanceEvaluator());
    }

    public SearchQualityRegressionSuite(HybridRelevanceEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "HybridRelevanceEvaluator cannot be null");
    }

    /**
     * Executes the comprehensive regression evaluation across all 48 benchmark queries.
     *
     * @return RegressionVerificationReport containing status and metric comparisons.
     */
    public RegressionVerificationReport runRegressionVerification() {
        log.info("Starting Search Quality Regression Verification across all 48 queries...");

        HybridRelevanceEvaluator.ThreeWayEvaluationResult result = evaluator.evaluateAll();
        return verifyMetrics(result);
    }

    /**
     * Verifies evaluation metrics against established regression floors.
     */
    public RegressionVerificationReport verifyMetrics(HybridRelevanceEvaluator.ThreeWayEvaluationResult result) {
        List<QualityCheckResult> checks = new ArrayList<>();

        // Gate 1: Hybrid Search Checks
        EvaluationMetricsAggregator.AggregateMetricsSummary hyb = result.hybrid().overallSummary();
        checks.add(check("Hybrid NDCG@10", hyb.meanNdcgAt10(),     HYBRID_MIN_NDCG_10));
        checks.add(check("Hybrid NDCG@5",  hyb.meanNdcgAt5(),      HYBRID_MIN_NDCG_5));
        checks.add(check("Hybrid MAP",      hyb.map(),              HYBRID_MIN_MAP));
        checks.add(check("Hybrid MRR",      hyb.mrr(),              HYBRID_MIN_MRR));
        checks.add(check("Hybrid P@5",      hyb.meanPrecisionAt5(), HYBRID_MIN_PREC_5));

        // Gate 2: BM25 Lexical Checks
        EvaluationMetricsAggregator.AggregateMetricsSummary bm25 = result.bm25().overallSummary();
        checks.add(check("BM25 NDCG@10",   bm25.meanNdcgAt10(),     BM25_MIN_NDCG_10));
        checks.add(check("BM25 MAP",        bm25.map(),              BM25_MIN_MAP));
        checks.add(check("BM25 MRR",        bm25.mrr(),              BM25_MIN_MRR));

        // Gate 3: Dense Semantic Checks
        EvaluationMetricsAggregator.AggregateMetricsSummary sem = result.semantic().overallSummary();
        checks.add(check("Semantic NDCG@10", sem.meanNdcgAt10(),    SEMANTIC_MIN_NDCG_10));
        checks.add(check("Semantic MRR",     sem.mrr(),             SEMANTIC_MIN_MRR));

        // Gate 4: Category-Specific Checks (Hybrid on sensitive archetypes)
        Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> hybCat =
                result.hybrid().categorySummary();

        double exactKeywordNdcg5 = hybCat.containsKey(EvaluationCategory.EXACT_KEYWORD)
                ? hybCat.get(EvaluationCategory.EXACT_KEYWORD).meanNdcgAt5() : 0.0;
        double ambiguousNdcg5 = hybCat.containsKey(EvaluationCategory.AMBIGUOUS)
                ? hybCat.get(EvaluationCategory.AMBIGUOUS).meanNdcgAt5() : 0.0;

        checks.add(check("Hybrid EXACT_KEYWORD NDCG@5", exactKeywordNdcg5, HYBRID_MIN_EXACT_KEYWORD_NDCG_5));
        checks.add(check("Hybrid AMBIGUOUS NDCG@5",     ambiguousNdcg5,     HYBRID_MIN_AMBIGUOUS_NDCG_5));

        boolean overallPassed = checks.stream().allMatch(QualityCheckResult::passed);

        return new RegressionVerificationReport(overallPassed, checks, result);
    }

    /**
     * Simulates intentional ranking corruption (e.g. inverted ranks or shuffled IDs)
     * to verify that the regression test suite reliably catches degradations.
     */
    public RegressionVerificationReport evaluateCorruptedRankings(RankingCorruptionType corruptionType) {
        log.warn("Executing intentional ranking corruption verification: {}", corruptionType);
        List<EvaluationQueryV2> queries = SearchForgeEvaluationDataset.getAllQueries();

        // 1. Get real hybrid results
        HybridRelevanceEvaluator.ThreeWayEvaluationResult legitimateResult = evaluator.evaluateAll();
        Map<String, List<Long>> corruptedHybridHits = new LinkedHashMap<>();

        for (Map.Entry<String, List<Long>> entry : legitimateResult.hybrid().queryHitsMap().entrySet()) {
            List<Long> originalIds = new ArrayList<>(entry.getValue());
            List<Long> corruptedIds;

            switch (corruptionType) {
                case REVERSED_RANKINGS -> {
                    corruptedIds = new ArrayList<>(originalIds);
                    Collections.reverse(corruptedIds);
                }
                case SHUFFLED_RANKINGS -> {
                    corruptedIds = new ArrayList<>(originalIds);
                    Collections.shuffle(corruptedIds, new Random(42));
                }
                case ARBITRARY_IRRELEVANT -> {
                    // Populate with completely irrelevant documents outside corpus range
                    corruptedIds = List.of(99901L, 99902L, 99903L, 99904L, 99905L);
                }
                default -> corruptedIds = originalIds;
            }
            corruptedHybridHits.put(entry.getKey(), corruptedIds);
        }

        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> corruptedMetrics =
                getPerQueryMetrics(corruptedHybridHits, queries);
        EvaluationMetricsAggregator.AggregateMetricsSummary corruptedOverall =
                EvaluationMetricsAggregator.aggregate(corruptedMetrics);
        Map<EvaluationCategory, EvaluationMetricsAggregator.AggregateMetricsSummary> corruptedCat =
                EvaluationMetricsAggregator.aggregateByCategory(corruptedMetrics);
        Map<EvaluationDatasetSplit, EvaluationMetricsAggregator.AggregateMetricsSummary> corruptedSplit =
                EvaluationMetricsAggregator.aggregateBySplit(corruptedMetrics);

        HybridRelevanceEvaluator.ModeEvaluationResult corruptedHybridMode =
                new HybridRelevanceEvaluator.ModeEvaluationResult(
                        "HYBRID_CORRUPTED",
                        corruptedOverall,
                        corruptedCat,
                        corruptedSplit,
                        corruptedMetrics,
                        corruptedHybridHits
                );

        // Build a corrupted ThreeWayEvaluationResult
        HybridRelevanceEvaluator.ThreeWayEvaluationResult corruptedResult =
                new HybridRelevanceEvaluator.ThreeWayEvaluationResult(
                        legitimateResult.bm25(),
                        legitimateResult.semantic(),
                        corruptedHybridMode
                );

        return verifyMetrics(corruptedResult);
    }

    private static QualityCheckResult check(String name, double observed, double threshold) {
        boolean passed = observed >= threshold;
        return new QualityCheckResult(name, observed, threshold, passed);
    }

    private static List<EvaluationMetricsAggregator.QueryEvaluationMetrics> getPerQueryMetrics(
            Map<String, List<Long>> hits, List<EvaluationQueryV2> queries) {
        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> list = new ArrayList<>(queries.size());
        for (EvaluationQueryV2 q : queries) {
            List<Long> docIds = hits.getOrDefault(q.queryId(), List.of());
            list.add(EvaluationMetricsAggregator.evaluateQuery(docIds, q));
        }
        return list;
    }

    // =========================================================================
    // RECORDS & TYPES
    // =========================================================================

    public enum RankingCorruptionType {
        REVERSED_RANKINGS,
        SHUFFLED_RANKINGS,
        ARBITRARY_IRRELEVANT
    }

    public record QualityCheckResult(
            String metricName,
            double observedValue,
            double thresholdFloor,
            boolean passed
    ) {
        public double delta() {
            return observedValue - thresholdFloor;
        }

        @Override
        public String toString() {
            return String.format("[%s] %-28s Observed: %.4f | Floor: %.4f | Delta: %+.4f",
                    passed ? "PASS" : "FAIL", metricName, observedValue, thresholdFloor, delta());
        }
    }

    public record RegressionVerificationReport(
            boolean allChecksPassed,
            List<QualityCheckResult> checkResults,
            HybridRelevanceEvaluator.ThreeWayEvaluationResult underlyingResults
    ) {
        public List<QualityCheckResult> failedChecks() {
            return checkResults.stream().filter(c -> !c.passed()).toList();
        }

        public void printSummary() {
            System.out.println("=========================================================================================");
            System.out.println("                   SEARCHFORGE QUALITY REGRESSION VERIFICATION REPORT                    ");
            System.out.println("=========================================================================================");
            System.out.printf("OVERALL STATUS: %s (%d/%d checks passed)%n",
                    allChecksPassed ? "PASSED" : "FAILED",
                    checkResults.stream().filter(QualityCheckResult::passed).count(),
                    checkResults.size());
            System.out.println("-----------------------------------------------------------------------------------------");
            for (QualityCheckResult check : checkResults) {
                System.out.println(check);
            }
            System.out.println("=========================================================================================");
        }
    }
}
