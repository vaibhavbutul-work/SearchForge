package com.searchforge.indexer.dto;

/**
 * REST API response DTO representing an individual search result hit.
 */
public record SearchHitResponse(
        Long documentId,
        String url,
        String domain,
        String title,
        String snippet
) {
}
