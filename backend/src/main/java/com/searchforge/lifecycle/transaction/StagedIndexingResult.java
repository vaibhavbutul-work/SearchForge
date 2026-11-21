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
        FAILED
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.UNCHANGED;
    }

    public static StagedIndexingResult success(Long documentId, int version, int tokenCount, long durationMs) {
        return new StagedIndexingResult(Status.SUCCESS, documentId, version, tokenCount, durationMs, "Document indexed and committed successfully");
    }

    public static StagedIndexingResult unchanged(Long documentId, int version, long durationMs) {
        return new StagedIndexingResult(Status.UNCHANGED, documentId, version, 0, durationMs, "Document content unchanged; staged indexing skipped");
    }

    public static StagedIndexingResult failure(Long documentId, String message, long durationMs) {
        return new StagedIndexingResult(Status.FAILED, documentId, 0, 0, durationMs, message);
    }
}
