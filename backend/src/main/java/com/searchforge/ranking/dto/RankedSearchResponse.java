package com.searchforge.ranking.dto;

import java.util.List;

/**
 * Top-level REST response container for ranked search queries.
 *
 * @param query           Original input query string.
 * @param totalHits       Total number of candidate documents matching the query across the index.
 * @param returnedHits    Count of ranked results included in this response page.
 * @param executionTimeMs Total end-to-end execution latency in milliseconds.
 * @param results         List of ranked search hits ordered descending by relevance.
 */
public record RankedSearchResponse(
        String query,
        int totalHits,
        int returnedHits,
        long executionTimeMs,
        List<RankedSearchHitResponse> results
) {
    public RankedSearchResponse {
        results = results != null ? List.copyOf(results) : List.of();
    }
}
