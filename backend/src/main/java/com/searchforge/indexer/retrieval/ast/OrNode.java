package com.searchforge.indexer.retrieval.ast;

import java.util.Objects;

/**
 * AST composite node representing a logical OR union between two subqueries.
 */
public record OrNode(QueryNode left, QueryNode right) implements QueryNode {
    public OrNode {
        Objects.requireNonNull(left, "Left child of OR node cannot be null");
        Objects.requireNonNull(right, "Right child of OR node cannot be null");
    }
}
