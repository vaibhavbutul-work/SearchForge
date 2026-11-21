package com.searchforge.lifecycle.service;

/**
 * Result record of an incremental document indexing and embedding update operation.
 */
public record IncrementalUpdateResult(
        Status status,
        Long documentId,
        String url,
        int previousVersion,
        int newVersion,
        int tokenCount,
        int uniqueTermsCount,
        boolean embeddingUpdated,
        long durationMs,
        String message
) {
    public enum Status {
        SUCCESS,
        UNCHANGED,
        FAILED
    }

    public static IncrementalUpdateResult success(Long documentId, String url, int prevVer, int newVer,
                                                  int tokens, int uniqueTerms, boolean embeddingUpdated,
                                                  long durationMs) {
        return new IncrementalUpdateResult(
                Status.SUCCESS,
                documentId,
                url,
                prevVer,
                newVer,
                tokens,
                uniqueTerms,
                embeddingUpdated,
                durationMs,
                "Document updated and indexed successfully (version " + prevVer + " -> " + newVer + ")"
        );
    }

    public static IncrementalUpdateResult unchanged(Long documentId, String url, int version, long durationMs) {
        return new IncrementalUpdateResult(
                Status.UNCHANGED,
                documentId,
                url,
                version,
                version,
                0,
                0,
                false,
                durationMs,
                "Content unchanged; skipped re-indexing and embedding"
        );
    }

    public static IncrementalUpdateResult failure(Long documentId, String url, int version, String message, long durationMs) {
        return new IncrementalUpdateResult(
                Status.FAILED,
                documentId,
                url,
                version,
                version,
                0,
                0,
                false,
                durationMs,
                message
        );
    }
}
