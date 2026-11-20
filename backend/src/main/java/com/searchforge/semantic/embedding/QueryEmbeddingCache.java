package com.searchforge.semantic.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Thread-safe, bounded Least Recently Used (LRU) cache for query vector embeddings.
 *
 * Avoids redundant neural model inference for repeated or frequent search queries.
 * Queries are normalized (trimmed and lowercased) before cache lookup.
 */
@Component
public class QueryEmbeddingCache {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingCache.class);
    public static final int DEFAULT_MAX_CAPACITY = 1000;

    private final int capacity;
    private final Map<String, float[]> lruMap;
    private final Object lock = new Object();

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    public QueryEmbeddingCache() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public QueryEmbeddingCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive, got: " + capacity);
        }
        this.capacity = capacity;
        this.lruMap = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                if (size() > QueryEmbeddingCache.this.capacity) {
                    evictionCount.incrementAndGet();
                    return true;
                }
                return false;
            }
        };
        log.info("Initialized QueryEmbeddingCache with capacity={}", capacity);
    }

    /**
     * Retrieves the cached embedding vector or computes it atomically via the provided supplier function.
     *
     * @param query raw input query
     * @param computeFn supplier function invoked on cache miss
     * @return defensive copy of the cached embedding vector
     */
    public float[] getOrCompute(String query, Function<String, float[]> computeFn) {
        if (query == null || query.isBlank()) {
            return computeFn.apply(query != null ? query : "");
        }

        String normalizedKey = normalizeKey(query);

        float[] cached;
        synchronized (lock) {
            cached = lruMap.get(normalizedKey);
        }

        if (cached != null) {
            hitCount.incrementAndGet();
            return Arrays.copyOf(cached, cached.length);
        }

        missCount.incrementAndGet();
        float[] computed = computeFn.apply(query);
        if (computed != null && computed.length > 0) {
            synchronized (lock) {
                lruMap.put(normalizedKey, Arrays.copyOf(computed, computed.length));
            }
            return Arrays.copyOf(computed, computed.length);
        }

        return computed;
    }

    public Optional<float[]> get(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalizedKey = normalizeKey(query);
        synchronized (lock) {
            float[] cached = lruMap.get(normalizedKey);
            if (cached != null) {
                hitCount.incrementAndGet();
                return Optional.of(Arrays.copyOf(cached, cached.length));
            }
        }
        missCount.incrementAndGet();
        return Optional.empty();
    }

    public void put(String query, float[] vector) {
        if (query == null || query.isBlank() || vector == null) {
            return;
        }
        String normalizedKey = normalizeKey(query);
        synchronized (lock) {
            lruMap.put(normalizedKey, Arrays.copyOf(vector, vector.length));
        }
    }

    public void clear() {
        synchronized (lock) {
            lruMap.clear();
        }
        log.info("Cleared QueryEmbeddingCache.");
    }

    public int size() {
        synchronized (lock) {
            return lruMap.size();
        }
    }

    public int capacity() {
        return capacity;
    }

    public long hitCount() {
        return hitCount.get();
    }

    public long missCount() {
        return missCount.get();
    }

    public long evictionCount() {
        return evictionCount.get();
    }

    public double hitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public CacheStats getStats() {
        return new CacheStats(size(), capacity, hitCount.get(), missCount.get(), evictionCount.get(), hitRate());
    }

    private String normalizeKey(String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }

    public record CacheStats(
            int size,
            int capacity,
            long hitCount,
            long missCount,
            long evictionCount,
            double hitRate
    ) {}
}
