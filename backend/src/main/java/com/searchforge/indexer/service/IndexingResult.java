package com.searchforge.indexer.service;

/**
 * Result record representing the outcome of indexing an individual document.
 */
public record IndexingResult(
        Long documentId,
        int tokenCount,
        int uniqueTerms,
        long durationMs,
        boolean success,
        String errorMessage
) {
    public static IndexingResult success(Long documentId, int tokenCount, int uniqueTerms, long durationMs) {
        return new IndexingResult(documentId, tokenCount, uniqueTerms, durationMs, true, null);
    }

    public static IndexingResult failure(Long documentId, String errorMessage, long durationMs) {
        return new IndexingResult(documentId, 0, 0, durationMs, false, errorMessage);
    }
}
