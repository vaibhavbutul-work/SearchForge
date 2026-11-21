package com.searchforge.lifecycle.recovery;

/**
 * Evaluated retry decision for a failed document operation.
 */
public record RetryDecision(
        FailureType failureType,
        boolean shouldRetry,
        long backoffMillis,
        String reason
) {
    public static RetryDecision retry(FailureType failureType, long backoffMillis, String reason) {
        return new RetryDecision(failureType, true, backoffMillis, reason);
    }

    public static RetryDecision deadLetter(FailureType failureType, String reason) {
        return new RetryDecision(failureType, false, 0, reason);
    }
}
