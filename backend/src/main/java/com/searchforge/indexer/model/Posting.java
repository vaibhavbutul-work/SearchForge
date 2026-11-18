package com.searchforge.indexer.model;

import java.util.List;

/**
 * Represents the occurrence of a term in a specific document.
 *
 * @param documentId    Unique identifier of the containing document.
 * @param termFrequency Total occurrences of the term in this document (tf).
 * @param positions     Unmodifiable, sorted list of 0-based word offsets where the term appears.
 */
public record Posting(
        long documentId,
        int termFrequency,
        List<Integer> positions
) implements Comparable<Posting> {

    public Posting {
        if (termFrequency <= 0) {
            throw new IllegalArgumentException("Term frequency must be positive");
        }
        positions = (positions != null) ? List.copyOf(positions) : List.of();
    }

    public static Posting of(long documentId, List<Integer> positions) {
        int tf = (positions != null && !positions.isEmpty()) ? positions.size() : 1;
        return new Posting(documentId, tf, positions);
    }

    @Override
    public int compareTo(Posting other) {
        return Long.compare(this.documentId, other.documentId);
    }
}
