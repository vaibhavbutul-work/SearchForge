package com.searchforge.ranking.retrieval;

import java.util.List;

/**
 * Interface integrating Boolean query evaluation with relevance-based BM25 scoring and phrase boosting.
 */
public interface RankedBooleanQueryEngine {

    int DEFAULT_LIMIT = 10;
    double DEFAULT_PHRASE_BOOST = 2.0;

    /**
     * Parses and evaluates a Boolean query, scoring the matching documents using BM25 with default limit (10).
     *
     * @param booleanQuery Boolean query string (e.g. "(java OR spring) AND NOT legacy").
     * @return Ranked list of matching search results.
     */
    default List<RankedSearchResult> searchRankedBoolean(String booleanQuery) {
        return searchRankedBoolean(booleanQuery, DEFAULT_LIMIT);
    }

    /**
     * Parses and evaluates a Boolean query, scoring matching documents using BM25.
     *
     * @param booleanQuery Boolean query string.
     * @param limit        Maximum number of ranked results to return.
     * @return Ranked list of matching search results.
     */
    List<RankedSearchResult> searchRankedBoolean(String booleanQuery, int limit);

    /**
     * Executes a free-text ranked search restricted strictly to candidate documents that satisfy a Boolean filter.
     *
     * @param freeTextQuery Raw free-text query to rank by.
     * @param booleanFilter Boolean filter expression defining candidate eligibility.
     * @param limit         Maximum number of ranked results to return.
     * @return Ranked list of filtered search results.
     */
    List<RankedSearchResult> searchFiltered(String freeTextQuery, String booleanFilter, int limit);
}
