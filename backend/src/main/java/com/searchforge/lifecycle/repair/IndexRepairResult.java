package com.searchforge.lifecycle.repair;

import com.searchforge.lifecycle.integrity.IndexIntegrityReport;

/**
 * Outcome of an index repair operation.
 */
public record IndexRepairResult(
        RepairMode mode,
        int missingDocumentsReindexed,
        int orphanedPostingsPurged,
        int embeddingsRegenerated,
        int orphanedEmbeddingsPurged,
        int embeddingsSynchronized,
        IndexIntegrityReport preRepairReport,
        IndexIntegrityReport postRepairReport,
        long durationMs
) {
    public boolean isCompletelyRepaired() {
        return postRepairReport != null && postRepairReport.isHealthy();
    }
}
