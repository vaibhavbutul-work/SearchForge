package com.searchforge.ranking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Top-level REST response container for ranked, semantic, and hybrid search queries.
 *
 * @param query           Original input query string.
 * @param mode            Search execution mode ("lexical", "semantic", "hybrid").
 * @param didYouMean      Suggested spelling correction query (omitted if no correction).
 * @param totalHits       Total number of candidate documents matching the query across the index.
 * @param returnedHits    Count of ranked results included in this response page.
 * @param executionTimeMs Total end-to-end execution latency in milliseconds.
 * @param fallback        Whether graceful degradation occurred due to sub-system failure.
 * @param fallbackReason  Human-readable explanation of fallback trigger (omitted if no fallback).
 * @param results         List of ranked search hits ordered descending by relevance.
 */
public record RankedSearchResponse(
        String query,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String mode,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String didYouMean,
        int totalHits,
        int returnedHits,
        long executionTimeMs,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean fallback,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String fallbackReason,
        List<RankedSearchHitResponse> results
) {
    public RankedSearchResponse {
        results = results != null ? List.copyOf(results) : List.of();
    }

    /**
     * Constructor for multi-mode search with mode and optional didYouMean.
     */
    public RankedSearchResponse(
            String query,
            String mode,
            String didYouMean,
            int totalHits,
            int returnedHits,
            long executionTimeMs,
            List<RankedSearchHitResponse> results
    ) {
        this(query, mode, didYouMean, totalHits, returnedHits, executionTimeMs, null, null, results);
    }

    /**
     * Backwards-compatible constructor for searches with didYouMean but without explicit mode.
     */
    public RankedSearchResponse(
            String query,
            String didYouMean,
            int totalHits,
            int returnedHits,
            long executionTimeMs,
            List<RankedSearchHitResponse> results
    ) {
        this(query, "lexical", didYouMean, totalHits, returnedHits, executionTimeMs, null, null, results);
    }

    /**
     * Backwards-compatible constructor for searches without mode, fallback or spelling correction.
     */
    public RankedSearchResponse(
            String query,
            int totalHits,
            int returnedHits,
            long executionTimeMs,
            List<RankedSearchHitResponse> results
    ) {
        this(query, "lexical", null, totalHits, returnedHits, executionTimeMs, null, null, results);
    }
}
