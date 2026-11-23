package com.searchforge.performance.telemetry;

/**
 * Structured telemetry report encapsulating document indexing throughput,
 * token rates, and stage-by-stage latency percentiles.
 */
public record IndexingBenchmarkTelemetry(
        int totalDocumentsIndexed,
        long totalTokensIndexed,
        long totalDurationMs,
        double documentsPerSecond,
        double tokensPerSecond,
        LatencyHistogram.LatencySnapshot totalIndexingLatency,
        LatencyHistogram.LatencySnapshot stage1ComputeLatency,
        LatencyHistogram.LatencySnapshot stage2DatabaseLatency,
        LatencyHistogram.LatencySnapshot stage3MemoryLatency
) {
    public static IndexingBenchmarkTelemetry of(
            int totalDocuments,
            long totalTokens,
            long durationMs,
            LatencyHistogram.LatencySnapshot totalLatency,
            LatencyHistogram.LatencySnapshot stage1Latency,
            LatencyHistogram.LatencySnapshot stage2Latency,
            LatencyHistogram.LatencySnapshot stage3Latency
    ) {
        double dps = durationMs > 0 ? (totalDocuments / (durationMs / 1000.0)) : 0.0;
        double tps = durationMs > 0 ? (totalTokens / (durationMs / 1000.0)) : 0.0;

        return new IndexingBenchmarkTelemetry(
                totalDocuments,
                totalTokens,
                durationMs,
                dps,
                tps,
                totalLatency,
                stage1Latency,
                stage2Latency,
                stage3Latency
        );
    }
}
