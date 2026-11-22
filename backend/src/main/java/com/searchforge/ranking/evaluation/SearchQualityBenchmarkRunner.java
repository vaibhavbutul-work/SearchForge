package com.searchforge.ranking.evaluation;

import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.search.fusion.RankFusionEngine;
import com.searchforge.search.fusion.RankedItem;
import com.searchforge.search.hybrid.HybridCandidate;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.HybridSearchService;
import com.searchforge.search.hybrid.SearchMode;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.VectorSearchResult;
import com.searchforge.semantic.vector.VectorSimilarityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating automated Information Retrieval benchmarking and quality evaluation (Task 7B/7C/7D).
 *
 * Compares Lexical BM25, Dense Semantic Retrieval, and Hybrid RRF across:
 * - Relevance quality metrics: Precision@K, Recall@K, MRR, and NDCG@K
 * - Latency profiling: Mean latency, P95 latency, and sub-component time breakdown
 * - Query category breakdown: Identifies distinct query archetypes where each paradigm excels or struggles
 */
@Service
public class SearchQualityBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchQualityBenchmarkRunner.class);

    private final HybridSearchService hybridSearchService;
    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final EmbeddingModel embeddingModel;
    private final VectorSimilarityEngine vectorSimilarityEngine;
    private final RankFusionEngine rankFusionEngine;
    private final RelevanceEvaluationRunner evaluationRunner;

    public SearchQualityBenchmarkRunner(
            HybridSearchService hybridSearchService,
            RankedRetrievalEngine rankedRetrievalEngine,
            EmbeddingModel embeddingModel,
            VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine) {
        this(hybridSearchService, rankedRetrievalEngine, embeddingModel, vectorSimilarityEngine, rankFusionEngine,
                new RelevanceEvaluationRunner());
    }

    @Autowired
    public SearchQualityBenchmarkRunner(
            HybridSearchService hybridSearchService,
            RankedRetrievalEngine rankedRetrievalEngine,
            @Autowired(required = false) EmbeddingModel embeddingModel,
            @Autowired(required = false) VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine,
            RelevanceEvaluationRunner evaluationRunner) {
        this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "HybridSearchService cannot be null");
        this.rankedRetrievalEngine = Objects.requireNonNull(rankedRetrievalEngine, "RankedRetrievalEngine cannot be null");
        this.rankFusionEngine = Objects.requireNonNull(rankFusionEngine, "RankFusionEngine cannot be null");
        this.embeddingModel = embeddingModel;
        this.vectorSimilarityEngine = vectorSimilarityEngine;
        this.evaluationRunner = Objects.requireNonNull(evaluationRunner, "RelevanceEvaluationRunner cannot be null");
    }

    /**
     * Executes the complete benchmark across all queries at depth K.
     *
     * @param queries     list of categorized evaluation queries
     * @param k           evaluation depth (e.g. 3, 5, 10)
     * @param repetitions number of profiling runs for latency stabilization
     * @return {@link ModeComparisonReport} detailing metrics, latencies, and category takeaways
     */
    public ModeComparisonReport runBenchmark(List<CategorizedEvaluationQuery> queries, int k, int repetitions) {
        if (queries == null || queries.isEmpty()) {
            throw new IllegalArgumentException("Evaluation queries must not be null or empty");
        }
        int resolvedK = k > 0 ? k : 5;
        int resolvedReps = Math.max(1, repetitions);

        log.info("Starting Phase 7 Search Quality Benchmark: {} queries, K={}, repetitions={}",
                queries.size(), resolvedK, resolvedReps);

        // 1. Warm-up Phase (JIT compiler, OS page cache, vector memory)
        warmup(queries, resolvedK);

        // 2. Evaluate Lexical BM25
        List<QueryEvaluationResult> lexicalResults = new ArrayList<>(queries.size());
        Map<CategorizedEvaluationQuery, List<Long>> lexicalHitsMap = new LinkedHashMap<>();
        for (CategorizedEvaluationQuery query : queries) {
            HybridSearchResult res = hybridSearchService.search(query.query(), SearchMode.LEXICAL, resolvedK);
            List<Long> docIds = res.candidates().stream().map(HybridCandidate::docId).toList();
            lexicalHitsMap.put(query, docIds);
            lexicalResults.add(evaluationRunner.evaluateHits(docIds, query.toEvaluationQuery(), resolvedK));
        }
        EvaluationReport lexicalReport = aggregateReport(queries.size(), resolvedK, lexicalResults);

        // 3. Evaluate Dense Semantic Retrieval
        List<QueryEvaluationResult> semanticResults = new ArrayList<>(queries.size());
        Map<CategorizedEvaluationQuery, List<Long>> semanticHitsMap = new LinkedHashMap<>();
        for (CategorizedEvaluationQuery query : queries) {
            HybridSearchResult res = hybridSearchService.search(query.query(), SearchMode.SEMANTIC, resolvedK);
            List<Long> docIds = res.candidates().stream().map(HybridCandidate::docId).toList();
            semanticHitsMap.put(query, docIds);
            semanticResults.add(evaluationRunner.evaluateHits(docIds, query.toEvaluationQuery(), resolvedK));
        }
        EvaluationReport semanticReport = aggregateReport(queries.size(), resolvedK, semanticResults);

        // 4. Evaluate Hybrid RRF Retrieval
        List<QueryEvaluationResult> hybridResults = new ArrayList<>(queries.size());
        Map<CategorizedEvaluationQuery, List<Long>> hybridHitsMap = new LinkedHashMap<>();
        for (CategorizedEvaluationQuery query : queries) {
            HybridSearchResult res = hybridSearchService.search(query.query(), SearchMode.HYBRID, resolvedK);
            List<Long> docIds = res.candidates().stream().map(HybridCandidate::docId).toList();
            hybridHitsMap.put(query, docIds);
            hybridResults.add(evaluationRunner.evaluateHits(docIds, query.toEvaluationQuery(), resolvedK));
        }
        EvaluationReport hybridReport = aggregateReport(queries.size(), resolvedK, hybridResults);

        // 5. Component Sub-Millisecond Profiling
        ComponentTimingBreakdown timings = profileTimings(queries, resolvedK, resolvedReps);

        // 6. Category-Level Breakdown
        List<CategoryPerformance> categoryBreakdown = computeCategoryBreakdown(
                queries, resolvedK, lexicalResults, semanticResults, hybridResults
        );

        String methodologyNotes = String.format(
                "Benchmark evaluated on %d queries across corpus. " +
                "Model: %s (%d dimensions). Constant k: 60 (RRF). Cutoff K: %d.",
                queries.size(),
                embeddingModel != null ? embeddingModel.modelName() : "DeterministicEmbeddingModel",
                embeddingModel != null ? embeddingModel.dimensions() : 384,
                resolvedK
        );

        return new ModeComparisonReport(
                queries.size(),
                resolvedK,
                lexicalReport,
                semanticReport,
                hybridReport,
                timings,
                categoryBreakdown,
                methodologyNotes
        );
    }

    /**
     * Executes multi-depth Information Retrieval evaluation calculating NDCG@3, NDCG@5, MRR,
     * Precision@3, Precision@5, Recall@3, and Recall@5 across all 3 search paradigms.
     */
    public ComprehensiveMetricsReport runComprehensiveBenchmark(List<CategorizedEvaluationQuery> queries, int repetitions) {
        ModeComparisonReport repK3 = runBenchmark(queries, 3, repetitions);
        ModeComparisonReport repK5 = runBenchmark(queries, 5, 1);

        ComprehensiveMetricsReport.ModeMetrics bm25 = ComprehensiveMetricsReport.ModeMetrics.of(
                repK3.bm25Report().meanNdcgAtK(), repK5.bm25Report().meanNdcgAtK(),
                repK3.bm25Report().meanReciprocalRank(),
                repK3.bm25Report().meanPrecisionAtK(), repK5.bm25Report().meanPrecisionAtK(),
                repK3.bm25Report().meanRecallAtK(), repK5.bm25Report().meanRecallAtK()
        );

        ComprehensiveMetricsReport.ModeMetrics semantic = ComprehensiveMetricsReport.ModeMetrics.of(
                repK3.semanticReport().meanNdcgAtK(), repK5.semanticReport().meanNdcgAtK(),
                repK3.semanticReport().meanReciprocalRank(),
                repK3.semanticReport().meanPrecisionAtK(), repK5.semanticReport().meanPrecisionAtK(),
                repK3.semanticReport().meanRecallAtK(), repK5.semanticReport().meanRecallAtK()
        );

        ComprehensiveMetricsReport.ModeMetrics hybrid = ComprehensiveMetricsReport.ModeMetrics.of(
                repK3.hybridReport().meanNdcgAtK(), repK5.hybridReport().meanNdcgAtK(),
                repK3.hybridReport().meanReciprocalRank(),
                repK3.hybridReport().meanPrecisionAtK(), repK5.hybridReport().meanPrecisionAtK(),
                repK3.hybridReport().meanRecallAtK(), repK5.hybridReport().meanRecallAtK()
        );

        return new ComprehensiveMetricsReport(
                queries.size(),
                embeddingModel != null ? embeddingModel.modelName() : "DeterministicEmbeddingModel",
                bm25,
                semantic,
                hybrid,
                repK3.timings(),
                repK3.categoryBreakdown()
        );
    }

    private void warmup(List<CategorizedEvaluationQuery> queries, int k) {
        for (int i = 0; i < 2; i++) {
            for (CategorizedEvaluationQuery q : queries) {
                hybridSearchService.search(q.query(), SearchMode.HYBRID, k);
            }
        }
    }

    private ComponentTimingBreakdown profileTimings(List<CategorizedEvaluationQuery> queries, int k, int reps) {
        int candidatePoolSize = Math.max(k * 2, 50);

        List<Double> bm25Times = new ArrayList<>();
        List<Double> inferenceTimes = new ArrayList<>();
        List<Double> vectorSearchTimes = new ArrayList<>();
        List<Double> fusionTimes = new ArrayList<>();
        List<Double> totalTimes = new ArrayList<>();

        for (int r = 0; r < reps; r++) {
            for (CategorizedEvaluationQuery q : queries) {
                String queryString = q.query();

                // 1. BM25 time
                long t0 = System.nanoTime();
                List<RankedSearchResult> lexHits = rankedRetrievalEngine.search(queryString, candidatePoolSize);
                long bm25ElapsedNs = System.nanoTime() - t0;
                bm25Times.add(bm25ElapsedNs / 1_000_000.0);

                // 2. Embedding Inference time
                long t1 = System.nanoTime();
                float[] queryVec = embeddingModel != null ? embeddingModel.embed(queryString) : new float[384];
                long inferenceElapsedNs = System.nanoTime() - t1;
                inferenceTimes.add(inferenceElapsedNs / 1_000_000.0);

                // 3. Vector Similarity Search time
                long t2 = System.nanoTime();
                List<VectorSearchResult> vecHits = vectorSimilarityEngine != null
                        ? vectorSimilarityEngine.search(queryVec, candidatePoolSize)
                        : List.of();
                long vectorSearchElapsedNs = System.nanoTime() - t2;
                vectorSearchTimes.add(vectorSearchElapsedNs / 1_000_000.0);

                // 4. Fusion time
                List<RankedItem> bmItems = new ArrayList<>();
                for (int i = 0; i < lexHits.size(); i++) {
                    bmItems.add(new RankedItem(lexHits.get(i).docId(), i + 1, lexHits.get(i).score(), "bm25"));
                }
                List<RankedItem> semItems = new ArrayList<>();
                for (int i = 0; i < vecHits.size(); i++) {
                    semItems.add(new RankedItem(vecHits.get(i).documentId(), i + 1, vecHits.get(i).similarityScore(), "semantic"));
                }
                Map<String, List<RankedItem>> inputs = Map.of("bm25", bmItems, "semantic", semItems);

                long t3 = System.nanoTime();
                rankFusionEngine.fuse(inputs, k);
                long fusionElapsedNs = System.nanoTime() - t3;
                fusionTimes.add(fusionElapsedNs / 1_000_000.0);

                // Total end-to-end
                double totalMs = (bm25ElapsedNs + inferenceElapsedNs + vectorSearchElapsedNs + fusionElapsedNs) / 1_000_000.0;
                totalTimes.add(totalMs);
            }
        }

        double avgBm25 = average(bm25Times);
        double avgInference = average(inferenceTimes);
        double avgVec = average(vectorSearchTimes);
        double avgFusion = average(fusionTimes);
        double avgTotal = average(totalTimes);
        double p95Total = Math.max(percentile95(totalTimes), avgTotal);

        return ComponentTimingBreakdown.of(avgBm25, avgInference, avgVec, avgFusion, avgTotal, p95Total);
    }

    private List<CategoryPerformance> computeCategoryBreakdown(
            List<CategorizedEvaluationQuery> queries,
            int k,
            List<QueryEvaluationResult> lexicalResults,
            List<QueryEvaluationResult> semanticResults,
            List<QueryEvaluationResult> hybridResults) {

        Map<QueryCategory, List<Integer>> categoryIndices = new LinkedHashMap<>();
        for (int i = 0; i < queries.size(); i++) {
            categoryIndices
                    .computeIfAbsent(queries.get(i).category(), cat -> new ArrayList<>())
                    .add(i);
        }

        List<CategoryPerformance> categoryList = new ArrayList<>();
        for (Map.Entry<QueryCategory, List<Integer>> entry : categoryIndices.entrySet()) {
            QueryCategory category = entry.getKey();
            List<Integer> indices = entry.getValue();
            int count = indices.size();

            double bm25Ndcg = indices.stream().mapToDouble(i -> lexicalResults.get(i).ndcgAtK()).average().orElse(0.0);
            double semNdcg = indices.stream().mapToDouble(i -> semanticResults.get(i).ndcgAtK()).average().orElse(0.0);
            double hybNdcg = indices.stream().mapToDouble(i -> hybridResults.get(i).ndcgAtK()).average().orElse(0.0);

            double bm25Mrr = indices.stream().mapToDouble(i -> lexicalResults.get(i).reciprocalRank()).average().orElse(0.0);
            double semMrr = indices.stream().mapToDouble(i -> semanticResults.get(i).reciprocalRank()).average().orElse(0.0);
            double hybMrr = indices.stream().mapToDouble(i -> hybridResults.get(i).reciprocalRank()).average().orElse(0.0);

            String winner;
            if (hybNdcg >= bm25Ndcg && hybNdcg >= semNdcg) {
                winner = "Hybrid";
            } else if (bm25Ndcg > semNdcg) {
                winner = "BM25";
            } else {
                winner = "Semantic";
            }

            String takeaway = generateTakeaway(category, bm25Ndcg, semNdcg, hybNdcg);

            categoryList.add(new CategoryPerformance(
                    category,
                    count,
                    round4(bm25Ndcg),
                    round4(semNdcg),
                    round4(hybNdcg),
                    round4(bm25Mrr),
                    round4(semMrr),
                    round4(hybMrr),
                    winner,
                    takeaway
            ));
        }

        return categoryList;
    }

    private String generateTakeaway(QueryCategory cat, double bm25, double sem, double hyb) {
        return switch (cat) {
            case EXACT_KEYWORD -> "BM25 excels on exact terminology; Hybrid matches or preserves keyword precision via RRF.";
            case TECHNICAL -> "Technical acronyms and specific hardware tokens strongly favor inverted index exact matches.";
            case CONCEPTUAL -> "Semantic embeddings resolve intent where query and document share minimal lexical overlap.";
            case SYNONYM -> "Semantic vector closeness bridges vocabulary mismatch without needing manual synonym dictionaries.";
            case NATURAL_LANGUAGE -> "Conversational queries favor dense neural sentence-level semantic representations.";
            case LOW_OVERLAP -> "Descriptive paraphrasing fails under pure keyword matching but succeeds in dense latent space.";
            case PHRASE -> "Quoted adjacent positional matches favor inverted index phrase boosting.";
            case BOOLEAN -> "Structured Boolean constraints (AND/NOT) are strictly enforced by lexical set logic.";
        };
    }

    private EvaluationReport aggregateReport(int totalQueries, int k, List<QueryEvaluationResult> results) {
        double avgP = results.stream().mapToDouble(QueryEvaluationResult::precisionAtK).average().orElse(0.0);
        double avgR = results.stream().mapToDouble(QueryEvaluationResult::recallAtK).average().orElse(0.0);
        double avgMrr = results.stream().mapToDouble(QueryEvaluationResult::reciprocalRank).average().orElse(0.0);
        double avgNdcg = results.stream().mapToDouble(QueryEvaluationResult::ndcgAtK).average().orElse(0.0);

        return new EvaluationReport(
                totalQueries,
                k,
                round4(avgP),
                round4(avgR),
                round4(avgMrr),
                round4(avgNdcg),
                results
        );
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double percentile95(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
