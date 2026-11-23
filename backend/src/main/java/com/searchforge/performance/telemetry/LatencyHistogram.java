package com.searchforge.performance.telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-resolution latency histogram and percentiles accumulator.
 *
 * Records elapsed execution timings in nanoseconds, converts to fractional milliseconds,
 * and calculates exact order-statistic percentiles (P50, P90, P95, P99) alongside
 * mean, min, and max.
 */
public class LatencyHistogram {

    private final List<Long> sampleNanos = new ArrayList<>();
    private final String label;

    public LatencyHistogram(String label) {
        this.label = label != null ? label : "default";
    }

    public synchronized void recordNanos(long durationNanos) {
        if (durationNanos >= 0) {
            sampleNanos.add(durationNanos);
        }
    }

    public synchronized void recordMillis(double durationMs) {
        if (durationMs >= 0) {
            sampleNanos.add((long) (durationMs * 1_000_000.0));
        }
    }

    public synchronized void reset() {
        sampleNanos.clear();
    }

    public synchronized int sampleCount() {
        return sampleNanos.size();
    }

    public synchronized LatencySnapshot snapshot() {
        if (sampleNanos.isEmpty()) {
            return new LatencySnapshot(label, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        List<Long> sorted = new ArrayList<>(sampleNanos);
        Collections.sort(sorted);

        int count = sorted.size();
        long sum = 0;
        for (long s : sorted) {
            sum += s;
        }

        double meanMs = (sum / (double) count) / 1_000_000.0;
        double minMs = sorted.get(0) / 1_000_000.0;
        double maxMs = sorted.get(count - 1) / 1_000_000.0;

        double p50Ms = getPercentile(sorted, 50.0) / 1_000_000.0;
        double p90Ms = getPercentile(sorted, 90.0) / 1_000_000.0;
        double p95Ms = getPercentile(sorted, 95.0) / 1_000_000.0;
        double p99Ms = getPercentile(sorted, 99.0) / 1_000_000.0;

        return new LatencySnapshot(label, count, minMs, maxMs, meanMs, p50Ms, p90Ms, p95Ms, p99Ms);
    }

    private static double getPercentile(List<Long> sorted, double percentile) {
        int n = sorted.size();
        if (n == 0) return 0.0;
        if (n == 1) return sorted.get(0);

        // Nearest-rank method with ceiling interpolation
        int index = (int) Math.ceil((percentile / 100.0) * n) - 1;
        index = Math.max(0, Math.min(n - 1, index));
        return sorted.get(index);
    }

    public record LatencySnapshot(
            String label,
            int sampleCount,
            double minMs,
            double maxMs,
            double meanMs,
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms
    ) {
        @Override
        public String toString() {
            return String.format("[%s: n=%d | min=%.3fms | mean=%.3fms | p50=%.3fms | p90=%.3fms | p95=%.3fms | p99=%.3fms | max=%.3fms]",
                    label, sampleCount, minMs, meanMs, p50Ms, p90Ms, p95Ms, p99Ms, maxMs);
        }
    }
}
