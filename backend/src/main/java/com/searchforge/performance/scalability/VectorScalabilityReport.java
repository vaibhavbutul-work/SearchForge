package com.searchforge.performance.scalability;

/**
 * Report containing empirical scalability metrics for vector similarity retrieval.
 *
 * @param corpusSize      number of indexed document vectors (N)
 * @param dimensions      dimensionality of vectors (D)
 * @param topK            number of nearest neighbors requested (K)
 * @param sampleCount     number of measured query executions
 * @param minLatencyMs    minimum query latency in milliseconds
 * @param meanLatencyMs   mean query latency in milliseconds
 * @param p50LatencyMs    50th percentile (median) latency in milliseconds
 * @param p90LatencyMs    90th percentile latency in milliseconds
 * @param p95LatencyMs    95th percentile latency in milliseconds
 * @param p99LatencyMs    99th percentile latency in milliseconds
 * @param maxLatencyMs    maximum query latency in milliseconds
 * @param qps             throughput in queries per second
 * @param estimatedMemoryBytes estimated memory occupied by vectors in RAM
 * @param floatOpsPerQuery total floating-point arithmetic operations per query (N * D)
 * @param recallAtK       recall rate against exact exhaustive search (1.0 = 100% exact)
 */
public record VectorScalabilityReport(
        int corpusSize,
        int dimensions,
        int topK,
        int sampleCount,
        double minLatencyMs,
        double meanLatencyMs,
        double p50LatencyMs,
        double p90LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        double maxLatencyMs,
        double qps,
        long estimatedMemoryBytes,
        long floatOpsPerQuery,
        double recallAtK
) {
    public String toFormattedSummary() {
        return String.format(
                "N=%-6d | D=%d | Mean=%6.2f ms | P50=%6.2f ms | P95=%6.2f ms | P99=%6.2f ms | QPS=%7.1f | RAM=%6.2f MB | Flops=%9d | Recall=%.2f",
                corpusSize,
                dimensions,
                meanLatencyMs,
                p50LatencyMs,
                p95LatencyMs,
                p99LatencyMs,
                qps,
                estimatedMemoryBytes / (1024.0 * 1024.0),
                floatOpsPerQuery,
                recallAtK
        );
    }
}
