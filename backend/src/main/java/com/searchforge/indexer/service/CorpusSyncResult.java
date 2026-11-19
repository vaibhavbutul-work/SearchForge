package com.searchforge.indexer.service;

/**
 * Result record representing the outcome of synchronizing or rebuilding the corpus index.
 */
public record CorpusSyncResult(
        int documentsProcessed,
        int documentsIndexed,
        int documentsSkipped,
        long totalTokensIndexed,
        long durationMs
) {
}
