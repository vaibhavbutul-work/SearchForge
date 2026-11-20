package com.searchforge.semantic.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueryEmbeddingCache Unit Tests")
class QueryEmbeddingCacheTest {

    @Test
    @DisplayName("Caches vector on miss and retrieves on subsequent query hit")
    void getOrCompute_cachesOnMissAndHitsOnSubsequent() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache(10);
        AtomicInteger computeCount = new AtomicInteger(0);

        float[] v1 = cache.getOrCompute("java concurrency", q -> {
            computeCount.incrementAndGet();
            return new float[]{0.1f, 0.2f, 0.3f};
        });

        assertEquals(1, computeCount.get());
        assertEquals(0, cache.hitCount());
        assertEquals(1, cache.missCount());

        // Subsequent query with different casing/whitespace hits cache
        float[] v2 = cache.getOrCompute("  JAVA CONCURRENCY  ", q -> {
            computeCount.incrementAndGet();
            return new float[]{0.9f, 0.9f, 0.9f};
        });

        assertEquals(1, computeCount.get(), "Supplier should not be called on cache hit");
        assertEquals(1, cache.hitCount());
        assertArrayEquals(v1, v2);
        assertEquals(0.5, cache.hitRate());
    }

    @Test
    @DisplayName("LRU eviction respects maximum capacity bounds")
    void eviction_respectsCapacity() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache(3);

        cache.put("q1", new float[]{1.0f});
        cache.put("q2", new float[]{2.0f});
        cache.put("q3", new float[]{3.0f});
        assertEquals(3, cache.size());

        // Add 4th entry -> evicts eldest (q1)
        cache.put("q4", new float[]{4.0f});
        assertEquals(3, cache.size());
        assertEquals(1, cache.evictionCount());

        assertTrue(cache.get("q1").isEmpty());
        assertTrue(cache.get("q2").isPresent());
        assertTrue(cache.get("q3").isPresent());
        assertTrue(cache.get("q4").isPresent());
    }

    @Test
    @DisplayName("Cache returns defensive copies preventing external mutation")
    void defensiveCopies_preventMutation() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache(5);
        float[] original = new float[]{1.0f, 2.0f};
        cache.put("mutation_test", original);

        float[] retrieved = cache.get("mutation_test").orElseThrow();
        retrieved[0] = 999.0f; // Mutate copy

        float[] retrievedAgain = cache.get("mutation_test").orElseThrow();
        assertEquals(1.0f, retrievedAgain[0], "Cached array must remain immutable");
    }

    @Test
    @DisplayName("Clear removes all entries and resets size")
    void clear_resetsState() {
        QueryEmbeddingCache cache = new QueryEmbeddingCache(5);
        cache.put("k1", new float[]{1.0f});
        cache.put("k2", new float[]{2.0f});
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.get("k1").isEmpty());
    }
}
