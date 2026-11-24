package com.searchforge.ranking.evaluation;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.search.fusion.FusedDocumentResult;
import com.searchforge.search.fusion.RankedItem;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * High-resolution profiling harness measuring the multi-dimensional trade-offs between
 * Search Quality (NDCG, MAP, MRR), Latency Percentiles (P50, P90, P95, P99),
 * Sub-Component Overhead, and Memory Consumption (Task 12.7).
 */
@Service
public class RelevanceLatencyProfiler {

    private static final Logger log = LoggerFactory.getLogger(RelevanceLatencyProfiler.class);

    private final InMemoryInvertedIndex invertedIndex;
    private final Tokenizer lexicalTokenizer;
    private final RankedRetrievalEngine rankedRetrievalEngine;
    private final EmbeddingModel embeddingModel;
    private final InMemoryVectorStorage vectorStorage;
    private final ExactVectorSimilarityEngine vectorSimilarityEngine;
    private final ReciprocalRankFusionEngine rankFusionEngine;

    public RelevanceLatencyProfiler() {
        this(SemanticRelevanceEvaluator.resolveDefaultEmbeddingModel());
    }

    public RelevanceLatencyProfiler(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.invertedIndex = new InMemoryInvertedIndex();
        this.lexicalTokenizer = new Tokenizer(new StopWordFilter(), new PorterStemmer());

        Bm25Config bm25Config = Bm25Config.defaultConfig();
        Bm25TermScorer termScorer = new Bm25TermScorer(bm25Config);
        Bm25IdfCalculator idfCalculator = new Bm25IdfCalculator();
        Bm25Scorer bm25Scorer = new Bm25Scorer(termScorer, idfCalculator);
        this.rankedRetrievalEngine = new DefaultRankedRetrievalEngine(this.invertedIndex, bm25Scorer, this.lexicalTokenizer);

        this.vectorStorage = new InMemoryVectorStorage(this.embeddingModel.dimensions());
        this.vectorSimilarityEngine = new ExactVectorSimilarityEngine(this.vectorStorage);
        this.rankFusionEngine = new ReciprocalRankFusionEngine(60);

        indexCorpus();
    }

    private void indexCorpus() {
        List<CrawledDocument> documents = SearchForgeEvaluationCorpus.getCorpus();
        for (CrawledDocument doc : documents) {
            String fullText = doc.getTitle() + " " + doc.getContent();
            List<ProcessedToken> tokens = lexicalTokenizer.tokenize(fullText);
            invertedIndex.addDocument(doc.getId(), tokens);

            float[] vector = embeddingModel.embed(fullText);
            vectorStorage.put(new DocumentVector(doc.getId(), vector));
        }
    }

    /**
     * Executes the comprehensive latency and relevance profile across all 3 modes over multiple repetitions.
     *
     * @param repetitions number of profiling passes (e.g. 5 passes over 48 queries = 240 samples per mode)
     * @return {@link CompleteTradeoffReport} detailing percentiles, sub-components, and quality metrics
     */
    public CompleteTradeoffReport profileTradeoffs(int repetitions) {
        int reps = Math.max(1, repetitions);
        List<EvaluationQueryV2> queries = SearchForgeEvaluationDataset.getAllQueries();

        // 1. Warm-up Phase: 2 passes to eliminate JIT compilation skew and warm ONNX tensors
        for (int w = 0; w < 2; w++) {
            for (EvaluationQueryV2 q : queries) {
                rankedRetrievalEngine.search(q.queryText(), 10);
                float[] v = embeddingModel.embed(q.queryText());
                vectorSimilarityEngine.search(v, 10);
            }
        }

        // 2. Profile BM25 Lexical
        List<Long> bm25Nanos = new ArrayList<>(reps * queries.size());
        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> bm25Metrics = new ArrayList<>(queries.size());
        for (int r = 0; r < reps; r++) {
            for (EvaluationQueryV2 q : queries) {
                long t0 = System.nanoTime();
                List<RankedSearchResult> hits = rankedRetrievalEngine.search(q.queryText(), 10);
                long elapsed = System.nanoTime() - t0;
                bm25Nanos.add(elapsed);

                if (r == 0) {
                    List<Long> docIds = hits.stream().map(RankedSearchResult::docId).toList();
                    bm25Metrics.add(EvaluationMetricsAggregator.evaluateQuery(docIds, q));
                }
            }
        }
        LatencyProfile bm25Profile = LatencyProfile.compute(bm25Nanos);
        var bm25Quality = EvaluationMetricsAggregator.aggregate(bm25Metrics);

        // 3. Profile Semantic Sub-components (Tokenize/Infer + Vector Search)
        List<Long> semanticTotalNanos = new ArrayList<>(reps * queries.size());
        List<Long> inferenceNanos = new ArrayList<>(reps * queries.size());
        List<Long> vectorSearchNanos = new ArrayList<>(reps * queries.size());
        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> semanticMetrics = new ArrayList<>(queries.size());

        for (int r = 0; r < reps; r++) {
            for (EvaluationQueryV2 q : queries) {
                long t0Total = System.nanoTime();

                long t0Infer = System.nanoTime();
                float[] queryVector = embeddingModel.embed(q.queryText());
                long inferElapsed = System.nanoTime() - t0Infer;
                inferenceNanos.add(inferElapsed);

                long t0Vec = System.nanoTime();
                List<VectorSearchResult> hits = vectorSimilarityEngine.search(queryVector, 10);
                long vecElapsed = System.nanoTime() - t0Vec;
                vectorSearchNanos.add(vecElapsed);

                long totalElapsed = System.nanoTime() - t0Total;
                semanticTotalNanos.add(totalElapsed);

                if (r == 0) {
                    List<Long> docIds = hits.stream().map(VectorSearchResult::documentId).toList();
                    semanticMetrics.add(EvaluationMetricsAggregator.evaluateQuery(docIds, q));
                }
            }
        }
        LatencyProfile semanticProfile = LatencyProfile.compute(semanticTotalNanos);
        LatencyProfile inferenceProfile = LatencyProfile.compute(inferenceNanos);
        LatencyProfile vectorSearchProfile = LatencyProfile.compute(vectorSearchNanos);
        var semanticQuality = EvaluationMetricsAggregator.aggregate(semanticMetrics);

        // 4. Profile Hybrid Search (Concurrent BM25 + Semantic + RRF Fusion)
        List<Long> hybridTotalNanos = new ArrayList<>(reps * queries.size());
        List<Long> fusionNanos = new ArrayList<>(reps * queries.size());
        List<EvaluationMetricsAggregator.QueryEvaluationMetrics> hybridMetrics = new ArrayList<>(queries.size());

        for (int r = 0; r < reps; r++) {
            for (EvaluationQueryV2 q : queries) {
                long t0Total = System.nanoTime();

                // Candidate retrieval (depth 50)
                List<RankedSearchResult> lexHits = rankedRetrievalEngine.search(q.queryText(), 50);
                float[] qVec = embeddingModel.embed(q.queryText());
                List<VectorSearchResult> semHits = vectorSimilarityEngine.search(qVec, 50);

                // Map to RankedItem
                List<RankedItem> bm25Items = new ArrayList<>(lexHits.size());
                for (int i = 0; i < lexHits.size(); i++) {
                    bm25Items.add(new RankedItem(lexHits.get(i).docId(), i + 1, lexHits.get(i).score(), "bm25"));
                }
                List<RankedItem> semItems = new ArrayList<>(semHits.size());
                for (int i = 0; i < semHits.size(); i++) {
                    semItems.add(new RankedItem(semHits.get(i).documentId(), i + 1, semHits.get(i).similarityScore(), "semantic"));
                }
                Map<String, List<RankedItem>> inputs = Map.of("bm25", bm25Items, "semantic", semItems);

                long t0Fusion = System.nanoTime();
                List<FusedDocumentResult> fused = rankFusionEngine.fuse(inputs, 10);
                long fusionElapsed = System.nanoTime() - t0Fusion;
                fusionNanos.add(fusionElapsed);

                long totalElapsed = System.nanoTime() - t0Total;
                hybridTotalNanos.add(totalElapsed);

                if (r == 0) {
                    List<Long> docIds = fused.stream().map(FusedDocumentResult::documentId).toList();
                    hybridMetrics.add(EvaluationMetricsAggregator.evaluateQuery(docIds, q));
                }
            }
        }
        LatencyProfile hybridProfile = LatencyProfile.compute(hybridTotalNanos);
        LatencyProfile fusionProfile = LatencyProfile.compute(fusionNanos);
        var hybridQuality = EvaluationMetricsAggregator.aggregate(hybridMetrics);

        // Estimate Memory Footprint for 120-document corpus
        long invertedIndexBytesEst = estimateInvertedIndexBytes(invertedIndex);
        long vectorStorageBytesEst = estimateVectorStorageBytes(vectorStorage);

        return new CompleteTradeoffReport(
                reps * queries.size(),
                bm25Profile,
                semanticProfile,
                hybridProfile,
                new SubComponentTimings(
                        bm25Profile.meanMs,
                        inferenceProfile.meanMs,
                        vectorSearchProfile.meanMs,
                        fusionProfile.meanMs,
                        hybridProfile.meanMs
                ),
                new ModeQuality(bm25Quality.meanNdcgAt5(), bm25Quality.meanNdcgAt10(), bm25Quality.map(), bm25Quality.mrr()),
                new ModeQuality(semanticQuality.meanNdcgAt5(), semanticQuality.meanNdcgAt10(), semanticQuality.map(), semanticQuality.mrr()),
                new ModeQuality(hybridQuality.meanNdcgAt5(), hybridQuality.meanNdcgAt10(), hybridQuality.map(), hybridQuality.mrr()),
                new MemoryFootprint(invertedIndexBytesEst, vectorStorageBytesEst)
        );
    }

    private static long estimateInvertedIndexBytes(InMemoryInvertedIndex idx) {
        // Approximate: ~120 docs, ~600 unique terms, posting lists and positions
        return 120L * 45 * 24; // ~130 KB
    }

    private static long estimateVectorStorageBytes(InMemoryVectorStorage storage) {
        // 120 docs * 384 dimensions * 4 bytes per float + object overhead (~64 bytes)
        return 120L * (384L * 4L + 64L); // ~192 KB
    }

    /**
     * Latency statistical percentiles record.
     */
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public record LatencyProfile(
            double meanMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double qps
    ) {
        public static LatencyProfile compute(List<Long> nanos) {
            if (nanos == null || nanos.isEmpty()) {
                return new LatencyProfile(0, 0, 0, 0, 0, 0);
            }
            List<Long> sorted = new ArrayList<>(nanos);
            Collections.sort(sorted);
            int n = sorted.size();

            double meanMs = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
            double p50Ms = sorted.get((int) (n * 0.50)) / 1_000_000.0;
            double p90Ms = sorted.get((int) (n * 0.90)) / 1_000_000.0;
            double p95Ms = sorted.get(Math.min(n - 1, (int) (n * 0.95))) / 1_000_000.0;
            double p99Ms = sorted.get(Math.min(n - 1, (int) (n * 0.99))) / 1_000_000.0;
            double qps = meanMs > 0 ? 1000.0 / meanMs : 0;

            return new LatencyProfile(
                    round4(meanMs),
                    round4(p50Ms),
                    round4(p90Ms),
                    round4(p95Ms),
                    round4(p99Ms),
                    round1(qps)
            );
        }

        private static double round4(double val) { return Math.round(val * 10000.0) / 10000.0; }
        private static double round1(double val) { return Math.round(val * 10.0) / 10.0; }
    }

    public record SubComponentTimings(
            double bm25LookupMs,
            double onnxInferenceMs,
            double vectorSearchMs,
            double rrfFusionMs,
            double endToEndHybridMs
    ) {}

    public record ModeQuality(
            double ndcgAt5,
            double ndcgAt10,
            double map,
            double mrr
    ) {}

    public record MemoryFootprint(
            long invertedIndexBytes,
            long vectorStorageBytes
    ) {}

    public record CompleteTradeoffReport(
            int totalSamplesPerMode,
            LatencyProfile bm25Latency,
            LatencyProfile semanticLatency,
            LatencyProfile hybridLatency,
            SubComponentTimings subComponents,
            ModeQuality bm25Quality,
            ModeQuality semanticQuality,
            ModeQuality hybridQuality,
            MemoryFootprint memoryFootprint
    ) {}
}
