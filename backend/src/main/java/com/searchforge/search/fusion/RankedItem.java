package com.searchforge.search.fusion;

import java.util.Objects;

/**
 * Represents an individual ranked candidate item originating from a specific retrieval system
 * (e.g. lexical BM25 or dense semantic retrieval) prior to rank fusion.
 *
 * @param documentId    unique document identifier
 * @param rank          1-based rank position within the source retrieval system (1 = best)
 * @param originalScore raw native score from the source engine (BM25 score or cosine similarity)
 * @param source        identifier of the retrieval engine (e.g. "bm25", "semantic")
 */
public record RankedItem(
        long documentId,
        int rank,
        double originalScore,
        String source
) {
    public RankedItem {
        if (documentId <= 0) {
            throw new IllegalArgumentException("Document ID must be positive, got: " + documentId);
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("Rank must be positive (1-based), got: " + rank);
        }
        source = source != null ? source.trim() : "unknown";
    }
}
