package com.searchforge.indexer.retrieval.ast;

/**
 * AST leaf node representing an exact multi-word quoted phrase (e.g. "search engine").
 */
public record PhraseNode(String phrase) implements QueryNode {
    public PhraseNode {
        if (phrase == null || phrase.isBlank()) {
            throw new IllegalArgumentException("Phrase cannot be null or blank");
        }
    }
}
