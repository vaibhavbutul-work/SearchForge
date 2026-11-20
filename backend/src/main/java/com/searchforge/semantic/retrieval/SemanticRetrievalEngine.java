package com.searchforge.semantic.retrieval;

import com.searchforge.semantic.retrieval.dto.SemanticSearchResponse;

/**
 * High-level contract for dense semantic search retrieval.
 *
 * Encapsulates query vectorization, exact vector similarity search, document metadata hydration,
 * and result ranking.
 */
public interface SemanticRetrievalEngine {

    /**
     * Executes dense semantic retrieval for the query string.
     *
     * @param query natural language search query
     * @param limit maximum number of Top-K results (limit &gt; 0)
     * @return SemanticSearchResponse DTO
     */
    SemanticSearchResponse search(String query, int limit);
}
