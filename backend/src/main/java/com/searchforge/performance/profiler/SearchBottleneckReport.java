package com.searchforge.performance.profiler;

import java.util.List;

/**
 * Immutable report structure capturing micro-profiling measurements across
 * SearchForge search execution pipelines.
 */
public record SearchBottleneckReport(
        List<SearchModeProfile> profiles,
        List<BottleneckFinding> majorFindings,
        long totalCorpusDocuments,
        long totalCorpusVectors,
        long estimatedHeapUsedBytes
) {
    public record PhaseTiming(
            String phaseName,
            long durationNanos,
            double durationMs,
            double percentageOfTotal,
            String description
    ) {}

    public record QueryProfileResult(
            String query,
            String searchMode,
            long totalDurationNanos,
            double totalDurationMs,
            List<PhaseTiming> phases,
            long memoryAllocatedBytes
    ) {}

    public record PhaseAggregate(
            String phaseName,
            double meanDurationMs,
            double p50DurationMs,
            double p95DurationMs,
            double meanPercentageOfTotal,
            String description
    ) {}

    public record SearchModeProfile(
            String searchMode,
            int queryCount,
            double meanTotalLatencyMs,
            double p50LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            List<PhaseAggregate> phaseAggregates,
            String primaryBottleneckPhase,
            double primaryBottleneckPercentage
    ) {}

    public record BottleneckFinding(
            String component,
            String searchMode,
            String measurementEvidence,
            String impact,
            String rootCause,
            String optimizationCandidate,
            String priority
    ) {}

    public String toFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("               SEARCHFORGE PHASE 10: SEARCH LATENCY BOTTLENECK REPORT                  \n");
        sb.append("========================================================================================\n");
        sb.append(String.format("Corpus Size: %d documents | Vectors: %d | Heap Used: %.2f MB\n\n",
                totalCorpusDocuments, totalCorpusVectors, (double) estimatedHeapUsedBytes / (1024 * 1024)));

        for (SearchModeProfile smp : profiles) {
            sb.append(String.format(">>> SEARCH MODE: %s (Queries: %d, Mean Latency: %.4f ms, P95: %.4f ms)\n",
                    smp.searchMode(), smp.queryCount(), smp.meanTotalLatencyMs(), smp.p95LatencyMs()));
            sb.append(String.format("%-35s | %-12s | %-12s | %-10s | %s\n", "Phase", "Mean (ms)", "P95 (ms)", "% of Total", "Description"));
            sb.append("----------------------------------------------------------------------------------------\n");
            for (PhaseAggregate pa : smp.phaseAggregates()) {
                sb.append(String.format("%-35s | %10.4f ms | %10.4f ms | %8.1f %% | %s\n",
                        pa.phaseName(), pa.meanDurationMs(), pa.p50DurationMs(), pa.meanPercentageOfTotal(), pa.description()));
            }
            sb.append(String.format("Primary Bottleneck: %s (%.1f%% of request time)\n\n",
                    smp.primaryBottleneckPhase(), smp.primaryBottleneckPercentage()));
        }

        sb.append("========================================================================================\n");
        sb.append("                               MAJOR BOTTLENECK FINDINGS                                \n");
        sb.append("========================================================================================\n");
        int idx = 1;
        for (BottleneckFinding bf : majorFindings) {
            sb.append(String.format("[%d] Priority: %s | Component: %s (%s)\n", idx++, bf.priority(), bf.component(), bf.searchMode()));
            sb.append(String.format("    - Evidence:     %s\n", bf.measurementEvidence()));
            sb.append(String.format("    - Root Cause:   %s\n", bf.rootCause()));
            sb.append(String.format("    - Impact:       %s\n", bf.impact()));
            sb.append(String.format("    - Remediation:  %s\n\n", bf.optimizationCandidate()));
        }
        sb.append("========================================================================================\n");
        return sb.toString();
    }
}
