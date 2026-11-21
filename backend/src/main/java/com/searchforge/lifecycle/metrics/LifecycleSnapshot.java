package com.searchforge.lifecycle.metrics;

import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable telemetry snapshot of the SearchForge document lifecycle system.
 */
public record LifecycleSnapshot(
        Instant timestamp,
        Map<DocumentLifecycleStatus, Long> countsByStatus,
        long documentsDiscovered,
        long documentsFetched,
        long documentsProcessed,
        long documentsUnchanged,
        long documentsUpdated,
        long documentsDeleted,
        long documentsFailed,
        long documentsRetried,
        long documentsRepaired,
        double averageLexicalDurationMs,
        double averageEmbeddingDurationMs,
        double averageCommitDurationMs,
        double averageTotalDurationMs,
        int activeDocumentCount,
        int vectorStorageSize,
        int vocabularySize
) {
}
