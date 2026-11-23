package com.searchforge.performance.telemetry;

import java.util.Map;

/**
 * Structured telemetry report encapsulating search latency distributions,
 * percentiles across search paradigms, query archetypes, and sub-component timings.
 */
public record SearchBenchmarkTelemetry(
        int totalQueriesExecuted,
        int warmupIterations,
        long totalExecutionDurationMs,
        Map<String, LatencyHistogram.LatencySnapshot> modeLatencies,
        Map<String, LatencyHistogram.LatencySnapshot> queryArchetypeLatencies,
        Map<String, LatencyHistogram.LatencySnapshot> componentLatencies
) {
    public LatencyHistogram.LatencySnapshot getMode(String mode) {
        return modeLatencies.get(mode);
    }

    public LatencyHistogram.LatencySnapshot getArchetype(String archetype) {
        return queryArchetypeLatencies.get(archetype);
    }

    public LatencyHistogram.LatencySnapshot getComponent(String component) {
        return componentLatencies.get(component);
    }
}
