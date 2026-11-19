package com.searchforge.ranking.retrieval;

import java.util.List;
import java.util.Map;

/**
 * Immutable search result representing a document ranked by relevance.
 *
 * @param docId        Unique identifier of the matching document.
 * @param score        Computed BM25 relevance score.
 * @param rank         1-based position in the search results (1 = highest relevance).
 * @param matchedTerms List of distinct query terms that appeared in this document.
 * @param termScores   Detailed explainability map of query term to individual BM25 score contribution.
 */
public record RankedSearchResult(
        long docId,
        double score,
        int rank,
        List<String> matchedTerms,
        Map<String, Double> termScores
) {
    public RankedSearchResult {
        if (docId <= 0) {
            throw new IllegalArgumentException("docId must be positive: " + docId);
        }
        if (score < 0.0) {
            throw new IllegalArgumentException("score cannot be negative: " + score);
        }
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be 1-based positive: " + rank);
        }
        matchedTerms = matchedTerms != null ? List.copyOf(matchedTerms) : List.of();
        termScores = termScores != null ? Map.copyOf(termScores) : Map.of();
    }
}
