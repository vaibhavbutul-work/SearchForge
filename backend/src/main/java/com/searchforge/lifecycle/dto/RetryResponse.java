package com.searchforge.lifecycle.dto;

import com.searchforge.lifecycle.recovery.DocumentRecoveryResult;
import java.util.List;

public record RetryResponse(
        int totalAttempted,
        int totalSucceeded,
        int totalFailed,
        List<DocumentRecoveryResult> details
) {
}
