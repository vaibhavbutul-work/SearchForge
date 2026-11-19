package com.searchforge.indexer.retrieval.ast;

/**
 * AST leaf node representing a single query term.
 */
public record TermNode(String term) implements QueryNode {
    public TermNode {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Term cannot be null or blank");
        }
    }
}
