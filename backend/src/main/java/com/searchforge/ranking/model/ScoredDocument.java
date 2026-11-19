package com.searchforge.ranking.model;

import java.util.Map;

/**
 * Represents a document ranked and scored by the relevance engine.
 *
 * Implements {@link Comparable} to provide natural descending relevance ordering
 * with deterministic ascending docId tie-breaking.
 *
 * @param docId      Unique identifier of the document.
 * @param score      Total computed relevance score (score >= 0.0).
 * @param termScores Detailed explainability breakdown mapping query terms to individual score contributions.
 */
public record ScoredDocument(
        long docId,
        double score,
        Map<String, Double> termScores
) implements Comparable<ScoredDocument> {

    public ScoredDocument {
        if (docId <= 0) {
            throw new IllegalArgumentException("docId must be positive: " + docId);
        }
        if (score < 0.0) {
            throw new IllegalArgumentException("score cannot be negative: " + score);
        }
        termScores = termScores != null ? Map.copyOf(termScores) : Map.of();
    }

    /**
     * Orders documents primarily by score descending (highest relevance first).
     * If scores are identical, breaks ties deterministically by docId ascending.
     */
    @Override
    public int compareTo(ScoredDocument other) {
        int scoreCompare = Double.compare(other.score, this.score);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        return Long.compare(this.docId, other.docId);
    }
}
