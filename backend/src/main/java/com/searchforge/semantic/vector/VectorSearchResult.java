package com.searchforge.semantic.vector;

import java.util.Comparator;
import java.util.Objects;

/**
 * Result record produced by vector similarity search.
 *
 * Encapsulates the matched document ID, its cosine similarity score, and its 1-based rank.
 */
public record VectorSearchResult(
        long documentId,
        float similarityScore,
        int rank
) implements Comparable<VectorSearchResult> {

    /**
     * Deterministic natural ordering:
     * 1. Descending by similarity score.
     * 2. Ascending by document ID for identical tie-breaking.
     */
    public static final Comparator<VectorSearchResult> RANKING_COMPARATOR = Comparator
            .comparingDouble(VectorSearchResult::similarityScore).reversed()
            .thenComparingLong(VectorSearchResult::documentId);

    @Override
    public int compareTo(VectorSearchResult other) {
        return RANKING_COMPARATOR.compare(this, other);
    }

    public VectorSearchResult withRank(int newRank) {
        return new VectorSearchResult(this.documentId, this.similarityScore, newRank);
    }
}
