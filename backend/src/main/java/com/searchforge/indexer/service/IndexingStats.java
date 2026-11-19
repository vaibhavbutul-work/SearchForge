package com.searchforge.indexer.service;

/**
 * Metric record representing corpus-wide inverted index statistics.
 */
public record IndexingStats(
        long totalIndexedDocuments,
        long totalTerms,
        long totalPostings,
        double averageDocumentLength
) {
}
