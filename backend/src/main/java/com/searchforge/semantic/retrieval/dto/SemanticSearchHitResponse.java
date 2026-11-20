package com.searchforge.semantic.retrieval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Individual document hit produced by dense semantic retrieval.
 *
 * @param documentId    Unique identifier of the matching document.
 * @param url           Original URL of the document.
 * @param title         Document title (or URL if missing).
 * @param domain        Host domain of the document.
 * @param snippet       Representative text snippet.
 * @param semanticScore Cosine similarity score in range [-1.0, 1.0].
 * @param rank          1-based position in semantic ranking (1 = most similar).
 */
public record SemanticSearchHitResponse(
        long documentId,
        String url,
        String title,
        String domain,
        String snippet,
        double semanticScore,
        int rank
) {
}
