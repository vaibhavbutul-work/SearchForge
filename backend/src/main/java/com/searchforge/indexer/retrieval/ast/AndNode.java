package com.searchforge.indexer.retrieval.ast;

import java.util.Objects;

/**
 * AST composite node representing a logical AND intersection between two subqueries.
 */
public record AndNode(QueryNode left, QueryNode right) implements QueryNode {
    public AndNode {
        Objects.requireNonNull(left, "Left child of AND node cannot be null");
        Objects.requireNonNull(right, "Right child of AND node cannot be null");
    }
}
