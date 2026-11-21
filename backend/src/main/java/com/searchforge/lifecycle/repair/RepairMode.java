package com.searchforge.lifecycle.repair;

/**
 * Execution mode for the Index Repair Service.
 */
public enum RepairMode {
    /**
     * Identifies and plans repair operations without applying any state changes.
     */
    DRY_RUN,

    /**
     * Executes corrective repairs across storage, lexical indexes, statistics, and embeddings.
     */
    REPAIR
}
