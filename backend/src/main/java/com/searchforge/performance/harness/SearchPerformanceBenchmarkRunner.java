package com.searchforge.performance.harness;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.performance.telemetry.IndexingBenchmarkTelemetry;
import com.searchforge.performance.telemetry.LatencyHistogram;
import com.searchforge.performance.telemetry.SearchBenchmarkTelemetry;
import com.searchforge.ranking.evaluation.CategorizedEvaluationQuery;
import com.searchforge.ranking.evaluation.ExpandedBenchmarkDataset;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.search.fusion.RankFusionEngine;
import com.searchforge.search.fusion.RankedItem;
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

/**
 * High-performance telemetry harness for Phase 10 empirical benchmarking.
 *
 * Measures fine-grained latency distributions (P50, P90, P95, P99, mean, min, max)
 * across search paradigms, query archetypes, sub-components, and indexing throughput.
 */
@Service
public class SearchPerformanceBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchPerformanceBenchmarkRunner.class);

    private final HybridSearchService hybridSearchService;
    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final RankedBooleanQueryEngine rankedBooleanQueryEngine;
    private final EmbeddingModel embeddingModel;
    private final VectorSimilarityEngine vectorSimilarityEngine;
    private final RankFusionEngine rankFusionEngine;
    private final Tokenizer tokenizer;

    @Autowired
    public SearchPerformanceBenchmarkRunner(
            HybridSearchService hybridSearchService,
            RankedRetrievalEngine rankedRetrievalEngine,
            RankedBooleanQueryEngine rankedBooleanQueryEngine,
            @Autowired(required = false) EmbeddingModel embeddingModel,
            @Autowired(required = false) VectorSimilarityEngine vectorSimilarityEngine,
            RankFusionEngine rankFusionEngine,
            Tokenizer tokenizer
    ) {
        this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "HybridSearchService cannot be null");
        this.rankedRetrievalEngine = Objects.requireNonNull(rankedRetrievalEngine, "RankedRetrievalEngine cannot be null");
        this.rankedBooleanQueryEngine = Objects.requireNonNull(rankedBooleanQueryEngine, "RankedBooleanQueryEngine cannot be null");
        this.rankFusionEngine = Objects.requireNonNull(rankFusionEngine, "RankFusionEngine cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
        this.embeddingModel = embeddingModel;
        this.vectorSimilarityEngine = vectorSimilarityEngine;
    }

    /**
     * Executes the comprehensive search latency benchmark across all search modes,
     * archetypes, and sub-components.
     *
     * @param queries           List of categorized queries.
     * @param warmupIterations  Number of pre-measurement warm-up iterations.
     * @param measurementReps   Number of measurement repetitions.
     * @param topK              Search result depth.
     * @return Complete {@link SearchBenchmarkTelemetry} report.
     */
    public SearchBenchmarkTelemetry runSearchBenchmark(
            List<CategorizedEvaluationQuery> queries,
            int warmupIterations,
            int measurementReps,
            int topK
    ) {
        if (queries == null || queries.isEmpty()) {
            queries = ExpandedBenchmarkDataset.getQueries();
        }

        log.info("Starting performance benchmark: {} queries, {} warmup reps, {} measurement reps, topK={}",
                queries.size(), warmupIterations, measurementReps, topK);

        // 1. Warm-Up Phase: Prime JVM JIT C1/C2 compilers and CPU caches
        for (int w = 0; w < warmupIterations; w++) {
            for (CategorizedEvaluationQuery q : queries) {
                rankedRetrievalEngine.search(q.query(), topK);
                if (embeddingModel != null && vectorSimilarityEngine != null) {
                    float[] v = embeddingModel.embed(q.query());
                    vectorSimilarityEngine.search(v, topK);
                }
                hybridSearchService.search(q.query(), SearchMode.HYBRID, topK);
            }
        }

        // 2. Telemetry Accumulators
        LatencyHistogram bm25Hist = new LatencyHistogram("BM25");
        LatencyHistogram booleanHist = new LatencyHistogram("Boolean");
        LatencyHistogram semanticHist = new LatencyHistogram("Semantic");
        LatencyHistogram hybridHist = new LatencyHistogram("Hybrid");

        Map<String, LatencyHistogram> archetypeHistograms = new LinkedHashMap<>();
        LatencyHistogram tokenizationHist = new LatencyHistogram("Tokenizer");
        LatencyHistogram bm25ScoringHist = new LatencyHistogram("BM25-Scoring");
        LatencyHistogram inferenceHist = new LatencyHistogram("Neural-Inference");
        LatencyHistogram vectorScanHist = new LatencyHistogram("Vector-Similarity-Scan");
        LatencyHistogram rrfHist = new LatencyHistogram("RRF-Fusion");

        long startBenchmarkTime = System.currentTimeMillis();
        int totalExecuted = 0;

        // 3. Measurement Phase
        for (int r = 0; r < measurementReps; r++) {
            for (CategorizedEvaluationQuery q : queries) {
                String queryText = q.query();
                String archetype = q.category() != null ? q.category().name() : "GENERAL";
                LatencyHistogram archetypeHist = archetypeHistograms.computeIfAbsent(archetype, LatencyHistogram::new);

                // --- Mode A: Pure Lexical BM25 ---
                long tBm25Start = System.nanoTime();
                List<RankedSearchResult> bm25Results = rankedRetrievalEngine.search(queryText, topK);
                long tBm25Elapsed = System.nanoTime() - tBm25Start;
                bm25Hist.recordNanos(tBm25Elapsed);
                archetypeHist.recordNanos(tBm25Elapsed);

                // --- Mode B: Ranked Boolean AST ---
                if (!queryText.contains("\"") && !queryText.contains("AND") && !queryText.contains("OR")) {
                    String boolQuery = queryText.trim().replaceAll("\\s+", " AND ");
                    long tBoolStart = System.nanoTime();
                    rankedBooleanQueryEngine.searchRankedBoolean(boolQuery, topK);
                    booleanHist.recordNanos(System.nanoTime() - tBoolStart);
                }

                // --- Mode C: Pure Dense Semantic ---
                float[] queryVector = null;
                if (embeddingModel != null && vectorSimilarityEngine != null) {
                    long tInferStart = System.nanoTime();
                    queryVector = embeddingModel.embed(queryText);
                    long tInferElapsed = System.nanoTime() - tInferStart;
                    inferenceHist.recordNanos(tInferElapsed);

                    long tVecStart = System.nanoTime();
                    List<VectorSearchResult> vecHits = vectorSimilarityEngine.search(queryVector, topK);
                    long tVecElapsed = System.nanoTime() - tVecStart;
                    vectorScanHist.recordNanos(tVecElapsed);

                    semanticHist.recordNanos(tInferElapsed + tVecElapsed);
                }

                // --- Mode D: End-to-End Hybrid RRF ---
                long tHybridStart = System.nanoTime();
                HybridSearchResult hybridResult = hybridSearchService.search(queryText, SearchMode.HYBRID, topK);
                hybridHist.recordNanos(System.nanoTime() - tHybridStart);

                // --- Sub-component: Tokenization profiling ---
                long tTokStart = System.nanoTime();
                tokenizer.tokenize(queryText);
                tokenizationHist.recordNanos(System.nanoTime() - tTokStart);

                // --- Sub-component: Standalone RRF Fusion profiling ---
                if (bm25Results != null && !bm25Results.isEmpty()) {
                    List<RankedItem> itemsA = new ArrayList<>();
                    for (int i = 0; i < bm25Results.size(); i++) {
                        itemsA.add(new RankedItem(bm25Results.get(i).docId(), i + 1, bm25Results.get(i).score(), "bm25"));
                    }
                    Map<String, List<RankedItem>> fusionInputs = Map.of("bm25", itemsA);
                    long tFuseStart = System.nanoTime();
                    rankFusionEngine.fuse(fusionInputs, topK);
                    rrfHist.recordNanos(System.nanoTime() - tFuseStart);
                }

                totalExecuted++;
            }
        }

        long totalDurationMs = System.currentTimeMillis() - startBenchmarkTime;

        // 4. Compile Telemetry Snapshots
        Map<String, LatencyHistogram.LatencySnapshot> modeSnapshots = new LinkedHashMap<>();
        modeSnapshots.put("BM25", bm25Hist.snapshot());
        modeSnapshots.put("Boolean", booleanHist.snapshot());
        modeSnapshots.put("Semantic", semanticHist.snapshot());
        modeSnapshots.put("Hybrid", hybridHist.snapshot());

        Map<String, LatencyHistogram.LatencySnapshot> archetypeSnapshots = new LinkedHashMap<>();
        for (Map.Entry<String, LatencyHistogram> entry : archetypeHistograms.entrySet()) {
            archetypeSnapshots.put(entry.getKey(), entry.getValue().snapshot());
        }

        Map<String, LatencyHistogram.LatencySnapshot> componentSnapshots = new LinkedHashMap<>();
        componentSnapshots.put("Tokenization", tokenizationHist.snapshot());
        componentSnapshots.put("BM25", bm25Hist.snapshot());
        componentSnapshots.put("Inference", inferenceHist.snapshot());
        componentSnapshots.put("VectorSimilarityScan", vectorScanHist.snapshot());
        componentSnapshots.put("RRFFusion", rrfHist.snapshot());

        return new SearchBenchmarkTelemetry(
                totalExecuted,
                warmupIterations,
                totalDurationMs,
                modeSnapshots,
                archetypeSnapshots,
                componentSnapshots
        );
    }

    /**
     * Measures document indexing throughput (DPS/TPS) and stage-by-stage latencies.
     */
    public IndexingBenchmarkTelemetry runIndexingBenchmark(
            List<CrawledDocument> corpus,
            com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator coordinator
    ) {
        if (corpus == null || corpus.isEmpty() || coordinator == null) {
            return new IndexingBenchmarkTelemetry(0, 0, 0, 0, 0,
                    new LatencyHistogram.LatencySnapshot("empty", 0, 0, 0, 0, 0, 0, 0, 0),
                    new LatencyHistogram.LatencySnapshot("empty", 0, 0, 0, 0, 0, 0, 0, 0),
                    new LatencyHistogram.LatencySnapshot("empty", 0, 0, 0, 0, 0, 0, 0, 0),
                    new LatencyHistogram.LatencySnapshot("empty", 0, 0, 0, 0, 0, 0, 0, 0));
        }

        LatencyHistogram totalHist = new LatencyHistogram("TotalIndexing");
        LatencyHistogram stage1Hist = new LatencyHistogram("Stage1Compute");
        LatencyHistogram stage2Hist = new LatencyHistogram("Stage2Database");
        LatencyHistogram stage3Hist = new LatencyHistogram("Stage3Memory");

        long startTotal = System.currentTimeMillis();
        long totalTokens = 0;

        for (CrawledDocument doc : corpus) {
            long t0 = System.nanoTime();
            var result = coordinator.processDocument(doc.getId(), doc.getTitle(), doc.getCleanText());
            long elapsed = System.nanoTime() - t0;

            totalHist.recordNanos(elapsed);
            if (result.tokenCount() > 0) {
                totalTokens += result.tokenCount();
            }
        }

        long totalDurationMs = Math.max(1, System.currentTimeMillis() - startTotal);

        return IndexingBenchmarkTelemetry.of(
                corpus.size(),
                totalTokens,
                totalDurationMs,
                totalHist.snapshot(),
                stage1Hist.snapshot(),
                stage2Hist.snapshot(),
                stage3Hist.snapshot()
        );
    }
}
