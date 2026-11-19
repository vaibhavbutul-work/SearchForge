package com.searchforge.indexer.dto;

import java.util.List;

/**
 * REST API response DTO for Boolean search queries.
 */
public record BooleanSearchResponse(
        String query,
        int totalHits,
        long durationMs,
        List<SearchHitResponse> hits
) {
}
