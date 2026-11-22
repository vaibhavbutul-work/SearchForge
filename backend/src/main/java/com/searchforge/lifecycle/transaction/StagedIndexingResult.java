package com.searchforge.lifecycle.transaction;

/**
 * Result of a staged two-phase indexing operation.
 */
public record StagedIndexingResult(
        Status status,
        Long documentId,
        int version,
        int tokenCount,
        long durationMs,
        String message
) {
    public enum Status {
        SUCCESS,
        UNCHANGED,
        PARTIAL_SUCCESS,
        FAILED
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.UNCHANGED;
    }

    public boolean isPartialSuccess() {
        return status == Status.PARTIAL_SUCCESS;
    }

    public boolean isUnchanged() {
        return status == Status.UNCHANGED;
    }

    public boolean isFailure() {
        return status == Status.FAILED;
    }

    public static StagedIndexingResult success(Long documentId, int version, int tokenCount, long durationMs) {
        return new StagedIndexingResult(Status.SUCCESS, documentId, version, tokenCount, durationMs, "Document indexed and committed successfully");
    }

    public static StagedIndexingResult partialSuccess(Long documentId, int version, int tokenCount, String message, long durationMs) {
        return new StagedIndexingResult(Status.PARTIAL_SUCCESS, documentId, version, tokenCount, durationMs, message);
    }

    public static StagedIndexingResult unchanged(Long documentId, int version, long durationMs) {
        return new StagedIndexingResult(Status.UNCHANGED, documentId, version, 0, durationMs, "Document content unchanged; staged indexing skipped");
    }

    public static StagedIndexingResult failure(Long documentId, String message, long durationMs) {
        return new StagedIndexingResult(Status.FAILED, documentId, 0, 0, durationMs, message);
    }
}
