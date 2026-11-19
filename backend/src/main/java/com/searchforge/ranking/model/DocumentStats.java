package com.searchforge.ranking.model;

import java.util.Map;

/**
 * Immutable representation of a document's statistics required for BM25 scoring.
 *
 * Encapsulates pre-fetched metrics so scoring can be performed with zero database I/O.
 *
 * @param docId           Unique identifier of the document.
 * @param docLength       Total token length of the document (|D|).
 * @param termFrequencies Map of normalized term to raw term frequency (tf >= 0).
 */
public record DocumentStats(
        long docId,
        int docLength,
        Map<String, Integer> termFrequencies
) {
    public DocumentStats {
        if (docId <= 0) {
            throw new IllegalArgumentException("docId must be positive: " + docId);
        }
        if (docLength < 0) {
            throw new IllegalArgumentException("docLength cannot be negative: " + docLength);
        }
        termFrequencies = termFrequencies != null ? Map.copyOf(termFrequencies) : Map.of();
    }

    /**
     * Returns the frequency of a specific term in this document, or 0 if not present.
     */
    public int getTermFrequency(String term) {
        if (term == null) {
            return 0;
        }
        return termFrequencies.getOrDefault(term, 0);
    }
}
