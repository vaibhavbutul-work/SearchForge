package com.searchforge.indexer.retrieval;

import com.searchforge.indexer.retrieval.ast.QueryNode;

/**
 * Core interface for Boolean Information Retrieval in SearchForge.
 *
 * Evaluates parsed Boolean query ASTs and raw query strings against the inverted index,
 * executing two-pointer linear merges across terms, phrases, and operators.
 */
public interface BooleanRetrievalEngine {

    /**
     * Parses and evaluates a raw Boolean query string.
     *
     * @param queryString Boolean query (e.g. "(java OR python) AND backend NOT legacy").
     * @return BooleanSearchResult containing matching document IDs, total hits, and timing.
     */
    BooleanSearchResult search(String queryString);

    /**
     * Evaluates a pre-parsed QueryNode AST against the index.
     *
     * @param astRoot Root node of the Boolean query AST.
     * @return BooleanSearchResult containing matching document IDs, total hits, and timing.
     */
    BooleanSearchResult search(QueryNode astRoot);
}
