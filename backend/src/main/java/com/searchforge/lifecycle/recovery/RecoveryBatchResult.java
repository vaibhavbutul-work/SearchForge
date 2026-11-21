package com.searchforge.lifecycle.recovery;

import java.util.List;

/**
 * Result of a batch recovery attempt.
 */
public record RecoveryBatchResult(
        int totalAttempted,
        int totalSucceeded,
        int totalFailed,
        List<DocumentRecoveryResult> results
) {
}
