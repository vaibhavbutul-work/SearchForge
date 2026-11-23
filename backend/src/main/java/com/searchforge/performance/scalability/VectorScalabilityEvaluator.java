package com.searchforge.performance.scalability;

import com.searchforge.semantic.vector.ExactVectorSimilarityEngine;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import com.searchforge.semantic.vector.VectorMath;
import com.searchforge.semantic.vector.VectorSearchResult;

import java.util.*;

/**
 * Empirical scalability evaluator for dense vector similarity retrieval.
 *
 * Benchmarks exhaustive brute-force exact vector retrieval across various corpus sizes N
 * and dimensions D, measuring latency percentiles (P50, P90, P95, P99), QPS throughput,
 * and memory footprint to identify the ANN transition inflection point.
 */
public class VectorScalabilityEvaluator {

    /**
     * Evaluates exact vector search performance for a given corpus size and dimension.
     *
     * @param corpusSize         number of documents N
     * @param dimensions         vector dimensionality D (e.g. 384 for MiniLM)
     * @param topK               top K neighbors to retrieve
     * @param warmupQueries      number of unmetered warm-up queries
     * @param measurementQueries number of timed measurement queries
     * @return {@link VectorScalabilityReport} containing comprehensive latency and throughput metrics
     */
    public VectorScalabilityReport evaluate(int corpusSize,
                                           int dimensions,
                                           int topK,
                                           int warmupQueries,
                                           int measurementQueries) {
        if (corpusSize <= 0) throw new IllegalArgumentException("corpusSize must be positive");
        if (dimensions <= 0) throw new IllegalArgumentException("dimensions must be positive");
        if (topK <= 0) throw new IllegalArgumentException("topK must be positive");

        InMemoryVectorStorage storage = new InMemoryVectorStorage(dimensions);
        SplittableRandom random = new SplittableRandom(42L + corpusSize);

        // 1. Populate corpus with deterministic normalized vectors
        for (int i = 0; i < corpusSize; i++) {
            float[] vec = new float[dimensions];
            for (int d = 0; d < dimensions; d++) {
                vec[d] = (float) random.nextDouble(-1.0, 1.0);
            }
            storage.put(i + 1L, vec);
        }

        ExactVectorSimilarityEngine engine = new ExactVectorSimilarityEngine(storage);

        // 2. Pre-generate deterministic query vectors
        int totalQueries = warmupQueries + measurementQueries;
        List<float[]> queries = new ArrayList<>(totalQueries);
        for (int q = 0; q < totalQueries; q++) {
            float[] qVec = new float[dimensions];
            for (int d = 0; d < dimensions; d++) {
                qVec[d] = (float) random.nextDouble(-1.0, 1.0);
            }
            queries.add(VectorMath.normalize(qVec));
        }

        // 3. JIT Warm-up Phase
        for (int i = 0; i < warmupQueries; i++) {
            engine.search(queries.get(i), topK);
        }

        // 4. Timed Measurement Phase
        long[] latenciesNanos = new long[measurementQueries];
        long totalNanos = 0;

        for (int i = 0; i < measurementQueries; i++) {
            float[] query = queries.get(warmupQueries + i);
            long start = System.nanoTime();
            List<VectorSearchResult> results = engine.search(query, topK);
            long elapsed = System.nanoTime() - start;
            latenciesNanos[i] = elapsed;
            totalNanos += elapsed;

            if (results.size() > Math.min(topK, corpusSize)) {
                throw new IllegalStateException("Returned more results than expected");
            }
        }

        // 5. Statistical Calculations
        Arrays.sort(latenciesNanos);

        double minMs = latenciesNanos[0] / 1_000_000.0;
        double maxMs = latenciesNanos[latenciesNanos.length - 1] / 1_000_000.0;
        double meanMs = (totalNanos / (double) measurementQueries) / 1_000_000.0;
        double p50Ms = percentile(latenciesNanos, 50.0) / 1_000_000.0;
        double p90Ms = percentile(latenciesNanos, 90.0) / 1_000_000.0;
        double p95Ms = percentile(latenciesNanos, 95.0) / 1_000_000.0;
        double p99Ms = percentile(latenciesNanos, 99.0) / 1_000_000.0;

        double totalDurationSec = totalNanos / 1_000_000_000.0;
        double qps = totalDurationSec > 0 ? measurementQueries / totalDurationSec : 0.0;

        // Memory estimation: 1,624 bytes per vector (4 * D + 48 byte DocumentVector + 40 byte Map node)
        long estimatedMemoryBytes = (long) corpusSize * ((long) dimensions * Float.BYTES + 48L + 40L);
        long floatOpsPerQuery = (long) corpusSize * dimensions;

        return new VectorScalabilityReport(
                corpusSize,
                dimensions,
                topK,
                measurementQueries,
                minMs,
                meanMs,
                p50Ms,
                p90Ms,
                p95Ms,
                p99Ms,
                maxMs,
                qps,
                estimatedMemoryBytes,
                floatOpsPerQuery,
                1.0 // Exact search always guarantees 100% recall
        );
    }

    private double percentile(long[] sortedNanos, double pct) {
        if (sortedNanos.length == 0) return 0.0;
        int index = (int) Math.ceil((pct / 100.0) * sortedNanos.length) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.length - 1));
        return sortedNanos[index];
    }
}
