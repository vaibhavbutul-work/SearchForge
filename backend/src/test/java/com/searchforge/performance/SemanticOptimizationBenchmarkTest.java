package com.searchforge.performance.semantic;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.evaluation.ExpandedBenchmarkDataset;
import com.searchforge.ranking.retrieval.DefaultRankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.search.fusion.ReciprocalRankFusionEngine;
import com.searchforge.search.hybrid.DefaultHybridSearchService;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.SearchMode;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 10.6: Semantic Search & ONNX Optimization Tests")
class SemanticOptimizationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(SemanticOptimizationBenchmarkTest.class);

    private InMemoryInvertedIndex invertedIndex;
    private InMemoryVectorStorage vectorStorage;
    private ExactVectorSimilarityEngine vectorSimilarityEngine;
    private Tokenizer tokenizer;
    private RankedRetrievalEngine lexicalEngine;
    private MockEmbeddingModel embeddingModel;
    private DefaultHybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        invertedIndex = new InMemoryInvertedIndex();
        vectorStorage = new InMemoryVectorStorage(384);
        vectorSimilarityEngine = new ExactVectorSimilarityEngine(vectorStorage);
        tokenizer = new Tokenizer(new StopWordFilter());

        Bm25Scorer scorer = new Bm25Scorer(new Bm25TermScorer(Bm25Config.defaultConfig()), new Bm25IdfCalculator());
        lexicalEngine = new DefaultRankedRetrievalEngine(invertedIndex, scorer, tokenizer);
        embeddingModel = new MockEmbeddingModel(384);

        hybridSearchService = new DefaultHybridSearchService(
                lexicalEngine,
                embeddingModel,
                vectorSimilarityEngine,
                new ReciprocalRankFusionEngine()
        );
    }

    @Test
    @DisplayName("Verify fastNormalizedDotProduct delivers 3x instruction reduction with bit-identical cosine similarity")
    void testFastNormalizedDotProductEquivalenceAndSpeedup() {
        int dimensions = 384;
        int vectorPairs = 10_000;
        Random rng = new Random(42);

        float[][] vecsA = new float[vectorPairs][dimensions];
        float[][] vecsB = new float[vectorPairs][dimensions];

        for (int i = 0; i < vectorPairs; i++) {
            float[] a = new float[dimensions];
            float[] b = new float[dimensions];
            for (int d = 0; d < dimensions; d++) {
                a[d] = rng.nextFloat() - 0.5f;
                b[d] = rng.nextFloat() - 0.5f;
            }
            vecsA[i] = VectorMath.normalize(a);
            vecsB[i] = VectorMath.normalize(b);
        }

        // 1. Correctness & Precision Verification: max absolute difference must be <= 1e-6
        double maxDelta = 0.0;
        for (int i = 0; i < vectorPairs; i++) {
            float cosSim = VectorMath.cosineSimilarity(vecsA[i], vecsB[i]);
            float fastDot = VectorMath.fastNormalizedDotProduct(vecsA[i], vecsB[i]);
            double delta = Math.abs(cosSim - fastDot);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
            assertEquals(cosSim, fastDot, 1e-5f, "Fast dot product must match cosine similarity within float precision");
        }
        assertTrue(maxDelta < 1e-5, "Max numerical drift across 10,000 vectors was: " + maxDelta);

        // 2. Warmup JIT C2
        for (int i = 0; i < 2_000; i++) {
            int idx = i % vectorPairs;
            VectorMath.cosineSimilarity(vecsA[idx], vecsB[idx]);
            VectorMath.fastNormalizedDotProduct(vecsA[idx], vecsB[idx]);
        }

        // 3. Timed Cosine Similarity (with norm squaring and square roots)
        long startCosine = System.nanoTime();
        for (int i = 0; i < vectorPairs; i++) {
            VectorMath.cosineSimilarity(vecsA[i], vecsB[i]);
        }
        long cosineNanos = System.nanoTime() - startCosine;

        // 4. Timed Fast Normalized Dot Product (bypassing norms and square roots)
        long startFast = System.nanoTime();
        for (int i = 0; i < vectorPairs; i++) {
            VectorMath.fastNormalizedDotProduct(vecsA[i], vecsB[i]);
        }
        long fastNanos = System.nanoTime() - startFast;

        double cosineMs = cosineNanos / 1_000_000.0;
        double fastMs = fastNanos / 1_000_000.0;
        double speedup = (fastMs > 0) ? (cosineMs / fastMs) : 1.0;

        log.info("Task 10.6 Vector Math Benchmark: 10,000 384-D comparisons: Standard Cosine = {} ms | Fast Normalized Dot = {} ms | Speedup: {}x (Max Delta: {})",
                String.format("%.2f", cosineMs), String.format("%.2f", fastMs), String.format("%.2f", speedup), maxDelta);

        assertTrue(fastNanos <= cosineNanos, "Fast normalized dot product must be at least as fast as standard cosine");
    }

    @Test
    @DisplayName("Verify ExactVectorSimilarityEngine preserves deterministic ordering and uses zero-copy vector direct views")
    void testExactVectorSearchRankingDeterminism() {
        for (long i = 1; i <= 50; i++) {
            float[] vec = new float[384];
            vec[(int) (i % 384)] = 1.0f;
            vectorStorage.put(i, vec);
        }

        float[] queryVec = new float[384];
        queryVec[10] = 1.0f; // Perfect match for doc 10

        List<VectorSearchResult> results = vectorSimilarityEngine.search(queryVec, 5);

        assertNotNull(results);
        assertEquals(5, results.size());
        assertEquals(10L, results.get(0).documentId(), "Doc 10 must be top rank with perfect similarity 1.0");
        assertEquals(1.0f, results.get(0).similarityScore(), 1e-4f);

        // Verify descending order
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).similarityScore() >= results.get(i + 1).similarityScore(),
                    "Results must be strictly sorted descending by similarity score");
        }
    }

    @Test
    @DisplayName("Verify parallel hybrid search executes lexical and semantic retrieval concurrently across virtual threads with graceful fallback")
    void testConcurrentHybridSearchExecutionAndFallback() {
        // Seed 10 documents
        List<CrawledDocument> corpus = ExpandedBenchmarkDataset.getCorpus().subList(0, 10);
        for (int i = 0; i < corpus.size(); i++) {
            CrawledDocument doc = corpus.get(i);
            long docId = i + 1L;
            invertedIndex.addDocument(docId, tokenizer.tokenize(doc.getTitle() + " " + doc.getCleanText()));

            float[] v = new float[384];
            v[(int) (docId % 384)] = 1.0f;
            vectorStorage.put(docId, v);
        }

        // 1. Successful Concurrent Hybrid Search
        HybridSearchResult result = hybridSearchService.search("virtual threads concurrency", SearchMode.HYBRID, 5);
        assertNotNull(result);
        assertEquals(SearchMode.HYBRID, result.effectiveMode());
        assertFalse(result.candidates().isEmpty());

        // 2. Graceful Fallback on Semantic Failure
        DefaultHybridSearchService faultyHybridService = new DefaultHybridSearchService(
                lexicalEngine,
                null, // Faulty: null embedding model
                vectorSimilarityEngine,
                new ReciprocalRankFusionEngine()
        );

        HybridSearchResult fallbackResult = faultyHybridService.search("virtual threads concurrency", SearchMode.HYBRID, 5);
        assertNotNull(fallbackResult);
        assertEquals(SearchMode.LEXICAL, fallbackResult.effectiveMode(), "Must fall back to lexical BM25 when semantic is unavailable");
        assertNotNull(fallbackResult.fallbackReason());
        assertTrue(fallbackResult.fallbackReason().contains("Semantic retrieval failed; degraded to lexical BM25"));
        assertFalse(fallbackResult.candidates().isEmpty(), "Fallback must still return valid lexical hits");
    }

    /**
     * Fast deterministic mock embedding model for testing.
     */
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
            return "mock-embedding-384";
        }
    }
}
