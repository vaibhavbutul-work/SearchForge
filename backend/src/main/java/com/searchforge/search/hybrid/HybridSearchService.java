package com.searchforge.search.hybrid;

/**
 * Service orchestrating lexical BM25, dense semantic, and hybrid retrieval.
 */
public interface HybridSearchService {

    /**
     * Executes retrieval under the requested search mode, managing candidate generation,
     * score normalization, Reciprocal Rank Fusion, and fallback degradation.
     *
     * @param query raw search query string
     * @param mode  requested search mode (LEXICAL, SEMANTIC, or HYBRID)
     * @param limit maximum number of top results to return (1..100)
     * @return {@link HybridSearchResult} containing ranked candidates and execution metadata
     */
    HybridSearchResult search(String query, SearchMode mode, int limit);
}
