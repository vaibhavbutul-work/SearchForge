package com.searchforge.ranking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Top-level REST response container for ranked search queries.
 *
 * @param query           Original input query string.
 * @param didYouMean      Suggested spelling correction query (omitted if no correction).
 * @param totalHits       Total number of candidate documents matching the query across the index.
 * @param returnedHits    Count of ranked results included in this response page.
 * @param executionTimeMs Total end-to-end execution latency in milliseconds.
 * @param results         List of ranked search hits ordered descending by relevance.
 */
public record RankedSearchResponse(
        String query,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String didYouMean,
        int totalHits,
        int returnedHits,
        long executionTimeMs,
        List<RankedSearchHitResponse> results
) {
    public RankedSearchResponse {
        results = results != null ? List.copyOf(results) : List.of();
    }

    /**
     * Backwards-compatible constructor for searches without a spelling correction suggestion.
     */
    public RankedSearchResponse(
            String query,
            int totalHits,
            int returnedHits,
            long executionTimeMs,
            List<RankedSearchHitResponse> results
    ) {
        this(query, null, totalHits, returnedHits, executionTimeMs, results);
    }
}
