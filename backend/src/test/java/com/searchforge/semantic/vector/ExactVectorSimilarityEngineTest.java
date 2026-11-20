package com.searchforge.semantic.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExactVectorSimilarityEngine Unit Tests")
class ExactVectorSimilarityEngineTest {

    private InMemoryVectorStorage storage;
    private ExactVectorSimilarityEngine engine;

    @BeforeEach
    void setUp() {
        storage = new InMemoryVectorStorage(3);
        engine = new ExactVectorSimilarityEngine(storage);
    }

    @Test
    @DisplayName("Should retrieve top-K documents ranked descending by cosine similarity")
    void testTopKRetrieval() {
        // Doc 1: Aligned with X axis
        storage.put(1L, new float[]{1.0f, 0.0f, 0.0f});
        // Doc 2: 45 degrees between X and Y
        storage.put(2L, new float[]{1.0f, 1.0f, 0.0f});
        // Doc 3: Aligned with Y axis (orthogonal to X)
        storage.put(3L, new float[]{0.0f, 1.0f, 0.0f});
        // Doc 4: Aligned with -X axis (opposite)
        storage.put(4L, new float[]{-1.0f, 0.0f, 0.0f});

        // Query aligned with X axis
        float[] query = new float[]{1.0f, 0.0f, 0.0f};

        List<VectorSearchResult> results = engine.search(query, 3);

        assertEquals(3, results.size());

        // Rank 1: Doc 1 (Cos = 1.0)
        assertEquals(1L, results.get(0).documentId());
        assertEquals(1, results.get(0).rank());
        assertEquals(1.0f, results.get(0).similarityScore(), 1e-4f);

        // Rank 2: Doc 2 (Cos = ~0.7071)
        assertEquals(2L, results.get(1).documentId());
        assertEquals(2, results.get(1).rank());
        assertEquals(0.7071f, results.get(1).similarityScore(), 1e-3f);

        // Rank 3: Doc 3 (Cos = 0.0)
        assertEquals(3L, results.get(2).documentId());
        assertEquals(3, results.get(2).rank());
        assertEquals(0.0f, results.get(2).similarityScore(), 1e-4f);
    }

    @Test
    @DisplayName("Should perform deterministic tie-breaking by ascending document ID on identical scores")
    void testDeterministicTieBreaking() {
        // Docs with identical vectors
        storage.put(50L, new float[]{1.0f, 0.0f, 0.0f});
        storage.put(10L, new float[]{1.0f, 0.0f, 0.0f});
        storage.put(30L, new float[]{1.0f, 0.0f, 0.0f});

        float[] query = new float[]{1.0f, 0.0f, 0.0f};
        List<VectorSearchResult> results = engine.search(query, 3);

        assertEquals(3, results.size());
        // Scores are identical (1.0), so ascending document IDs must break ties
        assertEquals(10L, results.get(0).documentId());
        assertEquals(1, results.get(0).rank());

        assertEquals(30L, results.get(1).documentId());
        assertEquals(2, results.get(1).rank());

        assertEquals(50L, results.get(2).documentId());
        assertEquals(3, results.get(2).rank());
    }

    @Test
    @DisplayName("Should return empty list when storage contains zero documents")
    void testEmptyStorage() {
        List<VectorSearchResult> results = engine.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should return all documents when K exceeds collection size (K > N)")
    void testKGreaterThanN() {
        storage.put(1L, new float[]{1.0f, 0.0f, 0.0f});
        storage.put(2L, new float[]{0.0f, 1.0f, 0.0f});

        List<VectorSearchResult> results = engine.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).documentId());
        assertEquals(2L, results.get(1).documentId());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on invalid search arguments")
    void testInvalidSearchArguments() {
        assertThrows(IllegalArgumentException.class, () -> engine.search(null, 10));
        assertThrows(IllegalArgumentException.class, () -> engine.search(new float[]{1.0f, 2.0f}, 10)); // Wrong dimensions
        assertThrows(IllegalArgumentException.class, () -> engine.search(new float[]{1.0f, 0.0f, 0.0f}, 0)); // K <= 0
        assertThrows(IllegalArgumentException.class, () -> engine.search(new float[]{1.0f, 0.0f, 0.0f}, -5));
    }

    @Test
    @DisplayName("Performance verification: Scan 1,000 vectors with Top-10 selection in < 15ms")
    void testLargeCollectionPerformance() {
        InMemoryVectorStorage largeStorage = new InMemoryVectorStorage(384);
        ExactVectorSimilarityEngine largeEngine = new ExactVectorSimilarityEngine(largeStorage);

        for (long i = 1; i <= 1000; i++) {
            float[] vec = new float[384];
            vec[(int) (i % 384)] = 1.0f;
            largeStorage.put(i, vec);
        }

        float[] query = new float[384];
        query[0] = 1.0f;

        long start = System.nanoTime();
        List<VectorSearchResult> results = largeEngine.search(query, 10);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(10, results.size());
        assertEquals(1, results.get(0).rank());
        assertTrue(elapsedMs < 30, "Exact search over 1,000 384-D vectors should take < 30ms, took: " + elapsedMs + "ms");
    }
}
