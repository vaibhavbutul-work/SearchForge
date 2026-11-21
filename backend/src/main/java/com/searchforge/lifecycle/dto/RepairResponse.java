package com.searchforge.lifecycle.dto;

import com.searchforge.lifecycle.repair.RepairMode;

public record RepairResponse(
        RepairMode mode,
        boolean completelyRepaired,
        int missingDocumentsReindexed,
        int orphanedPostingsPurged,
        int embeddingsRegenerated,
        int orphanedEmbeddingsPurged,
        int embeddingsSynchronized,
        long durationMs,
        IntegrityCheckResponse postRepairStatus
) {
}
