package com.searchforge.indexer.retrieval.ast;

import java.util.Objects;

/**
 * AST node representing logical negation / difference (positive AND NOT negative).
 * If positive is null, represents a pure negation against the entire document universe (U \ negative).
 */
public record NotNode(QueryNode positive, QueryNode negative) implements QueryNode {
    public NotNode {
        Objects.requireNonNull(negative, "Negative operand of NOT node cannot be null");
    }
}
