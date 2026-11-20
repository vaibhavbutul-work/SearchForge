package com.searchforge.semantic.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Top-level response container for dense semantic search queries.
 *
 * @param query           Original input query string.
 * @param mode            Search mode ("semantic").
 * @param totalHits       Total candidate documents matching similarity threshold.
 * @param returnedHits    Count of results included in this response page.
 * @param executionTimeMs Total query execution latency in milliseconds.
 * @param results         Ranked list of semantic search hits.
 */
public record SemanticSearchResponse(
        String query,
        String mode,
        int totalHits,
        int returnedHits,
        long executionTimeMs,
        List<SemanticSearchHitResponse> results
) {
    public SemanticSearchResponse {
        results = results != null ? List.copyOf(results) : List.of();
    }
}
