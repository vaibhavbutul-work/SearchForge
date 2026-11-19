package com.searchforge.ranking.model;

import java.util.Map;

/**
 * Immutable representation of collection-wide corpus statistics required for BM25 scoring.
 *
 * @param totalDocuments      Total count of indexed documents in the corpus (N).
 * @param avgDocLength        Average document length across the indexed corpus (avgdl).
 * @param documentFrequencies Pre-fetched map of query term to document frequency (df).
 */
public record CollectionStats(
        long totalDocuments,
        double avgDocLength,
        Map<String, Long> documentFrequencies
) {
    public CollectionStats {
        if (totalDocuments < 0) {
            throw new IllegalArgumentException("totalDocuments cannot be negative: " + totalDocuments);
        }
        if (avgDocLength < 0.0) {
            throw new IllegalArgumentException("avgDocLength cannot be negative: " + avgDocLength);
        }
        documentFrequencies = documentFrequencies != null ? Map.copyOf(documentFrequencies) : Map.of();
    }

    /**
     * Returns the document frequency (df) for a given term, or 0 if not present in the corpus.
     */
    public long getDocumentFrequency(String term) {
        if (term == null) {
            return 0L;
        }
        return documentFrequencies.getOrDefault(term, 0L);
    }
}
