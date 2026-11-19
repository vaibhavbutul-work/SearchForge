package com.searchforge.indexer.retrieval.ast;

/**
 * Sealed interface representing an Abstract Syntax Tree (AST) node for Boolean retrieval queries.
 */
public sealed interface QueryNode
        permits TermNode, PhraseNode, AndNode, OrNode, NotNode {
}
