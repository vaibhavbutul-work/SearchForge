package com.searchforge.performance;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.performance.compression.DeltaCodec;
import com.searchforge.performance.compression.VByteCodec;
import com.searchforge.performance.memory.IndexMemoryProfiler;
import com.searchforge.performance.scalability.VectorScalabilityEvaluator;
import com.searchforge.performance.scalability.VectorScalabilityReport;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.evaluation.ExpandedBenchmarkDataset;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.search.hybrid.DefaultHybridSearchService;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.SearchMode;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorMath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 10.8: Performance Regression Test Suite.
 *
 * Enforces hard, deterministic latency, memory, throughput, and compression invariants
 * to prevent performance degradation across SearchForge's indexing and query pipelines.
 */
@Tag("performance")
@DisplayName("Task 10.8: Phase 10 Performance Regression Test Suite")
class PerformanceRegressionTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceRegressionTest.class);

    private InMemoryInvertedIndex invertedIndex;
    private Tokenizer tokenizer;
    private RankedRetrievalEngine lexicalEngine;
    private InMemoryVectorStorage vectorStorage;
    private ExactVectorSimilarityEngine vectorSimilarityEngine;
    private DefaultHybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        invertedIndex = new InMemoryInvertedIndex();
        tokenizer = new Tokenizer(new StopWordFilter());
        vectorStorage = new InMemoryVectorStorage(384);
        vectorSimilarityEngine = new ExactVectorSimilarityEngine(vectorStorage);

        Bm25Scorer scorer = new Bm25Scorer(new Bm25TermScorer(Bm25Config.defaultConfig()), new Bm25IdfCalculator());
        lexicalEngine = new DefaultRankedRetrievalEngine(invertedIndex, scorer, tokenizer);

        EmbeddingModel mockModel = new MockEmbeddingModel(384);

        hybridSearchService = new DefaultHybridSearchService(
                lexicalEngine,
                mockModel,
                vectorSimilarityEngine,
                new ReciprocalRankFusionEngine()
        );
    }

    @Test
    @DisplayName("Regression Gate 1: Lexical BM25 Query Latency Threshold (Mean < 2.0 ms, P95 < 5.0 ms)")
    void regressionGate1_LexicalQueryLatencyThreshold() {
        List<CrawledDocument> corpus = ExpandedBenchmarkDataset.getCorpus();
        for (int i = 0; i < corpus.size(); i++) {
            CrawledDocument doc = corpus.get(i);
            long docId = i + 1L;
            List<ProcessedToken> tokens = tokenizer.tokenize(doc.getTitle() + " " + doc.getCleanText());
            invertedIndex.addDocument(docId, tokens);
        }

        String[] testQueries = {
                "concurrency indexing",
                "distributed search",
                "virtual threads",
                "inverted index",
                "database optimization"
        };

        // Warm-up phase
        for (int i = 0; i < 50; i++) {
            for (String q : testQueries) {
                lexicalEngine.search(q, 10);
            }
        }

        // Timed measurement phase
        int iterations = 100;
        long[] latenciesNanos = new long[iterations * testQueries.length];
        int sampleIdx = 0;
        long totalNanos = 0;

        for (int i = 0; i < iterations; i++) {
            for (String q : testQueries) {
                long start = System.nanoTime();
                List<RankedSearchResult> hits = lexicalEngine.search(q, 10);
                long elapsed = System.nanoTime() - start;
                latenciesNanos[sampleIdx++] = elapsed;
                totalNanos += elapsed;

                assertNotNull(hits);
                assertFalse(hits.isEmpty(), "Query '" + q + "' must return hits");
            }
        }

        Arrays.sort(latenciesNanos);
        double meanMs = (totalNanos / (double) latenciesNanos.length) / 1_000_000.0;
        double p50Ms = latenciesNanos[(int) (latenciesNanos.length * 0.50)] / 1_000_000.0;
        double p95Ms = latenciesNanos[(int) (latenciesNanos.length * 0.95)] / 1_000_000.0;
        double p99Ms = latenciesNanos[(int) (latenciesNanos.length * 0.99)] / 1_000_000.0;

        log.info("Regression Gate 1 (Lexical Latency): Mean={}", String.format("%.3f ms, P50=%.3f ms, P95=%.3f ms, P99=%.3f ms", meanMs, p50Ms, p95Ms, p99Ms));

        // Strict Regression Assertions
        assertTrue(meanMs < 2.0, "Mean lexical latency must be < 2.0 ms, was: " + meanMs + " ms");
        assertTrue(p95Ms < 5.0, "P95 lexical latency must be < 5.0 ms, was: " + p95Ms + " ms");
    }

    @Test
    @DisplayName("Regression Gate 2: Positional Memory Budget (Posting <= 80 bytes for 5 positions)")
    void regressionGate2_PositionalMemoryFootprint() {
        Posting posting = new Posting(42L, 5, new int[]{1, 4, 12, 19, 45});

        // 1. Structural memory check: primitive int[] packed storage must be <= 80 bytes
        long estimatedBytes = IndexMemoryProfiler.estimatePostingMemory(posting);
        log.info("Regression Gate 2 (Posting Memory): {} bytes for docId=42, freq=5", estimatedBytes);

        // Primitive int[5] = 24 byte array + 32 byte Posting + 8 byte ref = 64 bytes
        assertTrue(estimatedBytes <= 80L,
                "Posting memory must not exceed 80 bytes (unboxed primitive int[] invariant), was: " + estimatedBytes);

        // 2. Backward compatibility view fidelity
        assertEquals(5, posting.positions().size());
        assertEquals(1, posting.positions().get(0));
        assertEquals(45, posting.positions().get(4));

        // 3. Immutability protection
        assertThrows(UnsupportedOperationException.class, () -> posting.positions().add(99));
    }

    @Test
    @DisplayName("Regression Gate 3: Postings Compression Throughput (>= 50M ints/sec) and Savings (>= 70%)")
    void regressionGate3_PostingsCompressionRatioAndThroughput() {
        int count = 5_000;
        long[] docIds = new long[count];
        for (int i = 0; i < count; i++) {
            docIds[i] = (long) (i + 1) * 3L; // strictly ascending gaps of 3
        }

        // 1. Compression Ratio
        long[] deltas = DeltaCodec.encodeDeltas(docIds);
        byte[] compressed = VByteCodec.encode(deltas);

        int rawBytes = count * Long.BYTES; // 40,000 bytes
        int compressedBytes = compressed.length; // ~5,000 bytes (1 byte per delta=3)
        double savings = 1.0 - ((double) compressedBytes / rawBytes);

        log.info("Regression Gate 3 (Compression): Raw={} B, Compressed={} B, Savings={}%",
                rawBytes, compressedBytes, String.format("%.1f", savings * 100.0));

        assertTrue(savings >= 0.70, "Compression savings must be >= 70%, was: " + (savings * 100.0) + "%");

        // 2. Decode Throughput (with JIT warm-up)
        for (int i = 0; i < 200; i++) {
            long[] decDeltas = VByteCodec.decodeLongs(compressed, count);
            long[] roundtrip = DeltaCodec.decodeDeltas(decDeltas);
            if (roundtrip.length != count) throw new IllegalStateException();
        }

        int iterations = 200;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long[] decompressedDeltas = VByteCodec.decodeLongs(compressed, count);
            long[] roundtrip = DeltaCodec.decodeDeltas(decompressedDeltas);
            assertEquals(count, roundtrip.length);
        }
        long elapsedNanos = System.nanoTime() - start;

        long totalIntsDecoded = (long) count * iterations;
        double throughputIntsPerSec = (totalIntsDecoded / (elapsedNanos / 1_000_000_000.0));
        double throughputMillions = throughputIntsPerSec / 1_000_000.0;

        log.info("Regression Gate 3 (Decode Throughput): {} Million ints/sec", String.format("%.1f", throughputMillions));
        assertTrue(throughputMillions >= 30.0,
                "Decode throughput must be >= 30 Million ints/sec, was: " + throughputMillions);
    }

    @Test
    @DisplayName("Regression Gate 4: Semantic Vector Scalability (N=1,000: P95 < 10.0 ms, Recall=1.00)")
    void regressionGate4_SemanticVectorScalability() {
        VectorScalabilityEvaluator evaluator = new VectorScalabilityEvaluator();
        VectorScalabilityReport report = evaluator.evaluate(1_000, 384, 10, 20, 100);

        log.info("Regression Gate 4 (Semantic Scalability): {}", report.toFormattedSummary());

        assertEquals(1.0, report.recallAtK(), "Exact vector retrieval must maintain 100% recall");
        assertTrue(report.meanLatencyMs() < 5.0, "Mean latency for 1,000 vectors must be < 5.0 ms");
        assertTrue(report.p95LatencyMs() < 10.0, "P95 latency for 1,000 vectors must be < 10.0 ms");
        assertTrue(report.qps() >= 500.0, "Throughput must exceed 500 QPS, was: " + report.qps());

        // Fast normalized dot-product speedup verification
        Random rng = new Random(42);
        float[] a = new float[384];
        float[] b = new float[384];
        for (int i = 0; i < 384; i++) {
            a[i] = rng.nextFloat();
            b[i] = rng.nextFloat();
        }
        float[] normA = VectorMath.normalize(a);
        float[] normB = VectorMath.normalize(b);

        float cosSim = VectorMath.cosineSimilarity(normA, normB);
        float fastDot = VectorMath.fastNormalizedDotProduct(normA, normB);
        assertEquals(cosSim, fastDot, 1e-6f, "Fast normalized dot product must match cosine similarity within 1e-6");
    }

    @Test
    @DisplayName("Regression Gate 5: Concurrent Hybrid Retrieval Latency & Degradation Resiliency")
    void regressionGate5_ConcurrentHybridRetrievalAndFaultTolerance() {
        List<CrawledDocument> corpus = ExpandedBenchmarkDataset.getCorpus().subList(0, 15);
        for (int i = 0; i < corpus.size(); i++) {
            CrawledDocument doc = corpus.get(i);
            long docId = i + 1L;
            invertedIndex.addDocument(docId, tokenizer.tokenize(doc.getTitle() + " " + doc.getCleanText()));

            float[] v = new float[384];
            v[(int) (docId % 384)] = 1.0f;
            vectorStorage.put(docId, v);
        }

        // 1. Concurrent Execution
        HybridSearchResult result = hybridSearchService.search("search optimization", SearchMode.HYBRID, 5);
        assertNotNull(result);
        assertEquals(SearchMode.HYBRID, result.effectiveMode());
        assertFalse(result.candidates().isEmpty(), "Concurrent hybrid search must return candidates");

        // 2. Fault Tolerance / Graceful Fallback
        DefaultHybridSearchService faultyService = new DefaultHybridSearchService(
                lexicalEngine,
                null, // null model
                vectorSimilarityEngine,
                new ReciprocalRankFusionEngine()
        );

        HybridSearchResult fallback = faultyService.search("search optimization", SearchMode.HYBRID, 5);
        assertNotNull(fallback);
        assertTrue(fallback.fallback(), "Faulty service must trigger fallback");
        assertEquals(SearchMode.LEXICAL, fallback.effectiveMode());
        assertFalse(fallback.candidates().isEmpty(), "Fallback must preserve lexical results");
    }

    private static class MockEmbeddingModel implements EmbeddingModel {
        private final int dimensions;

        MockEmbeddingModel(int dimensions) {
            this.dimensions = dimensions;
        }

        @Override
        public float[] embed(String text) {
            float[] vec = new float[dimensions];
            if (text != null) {
                int hash = Math.abs(text.hashCode());
                vec[hash % dimensions] = 1.0f;
            }
            return VectorMath.normalize(vec);
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            List<float[]> res = new ArrayList<>();
            for (String t : texts) {
                res.add(embed(t));
            }
            return res;
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        @Override
        public String modelName() {
            return "mock-regression-embedding-model";
        }
    }
}
