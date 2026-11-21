package com.searchforge.lifecycle.recovery;

import com.searchforge.lifecycle.model.DocumentLifecycleStatus;

/**
 * Result of a recovery attempt for a single document.
 */
public record DocumentRecoveryResult(
        Long documentId,
        boolean success,
        DocumentLifecycleStatus newStatus,
        int retryCount,
        String message
) {
}
