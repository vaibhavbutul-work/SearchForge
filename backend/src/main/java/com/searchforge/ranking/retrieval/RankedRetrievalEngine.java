package com.searchforge.ranking.retrieval;

import com.searchforge.query.synonym.model.ExpandedQuery;

import java.util.List;

/**
 * Core interface for executing ranked information retrieval over an inverted index.
 */
public interface RankedRetrievalEngine {

    int DEFAULT_LIMIT = 10;

    /**
     * Executes ranked retrieval using BM25 with the default result limit (10).
     *
     * @param query Raw free-text query string.
     * @return Ordered list of top matching {@link RankedSearchResult}s.
     */
    default List<RankedSearchResult> search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    /**
     * Executes ranked retrieval using BM25 returning up to {@code limit} results.
     *
     * @param query Raw free-text query string.
     * @param limit Maximum number of ranked results to return (limit >= 1).
     * @return Ordered list of top matching {@link RankedSearchResult}s sorted descending by score.
     */
    List<RankedSearchResult> search(String query, int limit);

    /**
     * Executes ranked retrieval using an expanded query with weighted synonym terms.
     *
     * @param expandedQuery Expanded query containing original and synonym terms.
     * @param limit         Maximum number of ranked results to return (limit >= 1).
     * @return Ordered list of top matching {@link RankedSearchResult}s.
     */
    default List<RankedSearchResult> search(ExpandedQuery expandedQuery, int limit) {
        return search(expandedQuery != null ? expandedQuery.originalQuery() : "", limit);
    }
}
