package com.searchforge.lifecycle.model;

/**
 * Explicit lifecycle states representing a document's processing and indexing state
 * across the SearchForge crawler, inverted index, and vector store subsystems.
 */
public enum DocumentLifecycleStatus {

    /**
     * URL has been discovered and enqueued in the frontier, but HTML is not yet fetched.
     */
    DISCOVERED,

    /**
     * Raw HTML content has been downloaded and verified via HTTP 200.
     */
    FETCHED,

    /**
     * Content is currently being parsed, normalized, tokenized, or embedded.
     * Document is invisible to user search queries.
     */
    PROCESSING,

    /**
     * Lexical and positional inverted postings are being persisted to storage.
     */
    INDEXING,

    /**
     * Dense vector representation is being computed and persisted.
     */
    EMBEDDING,

    /**
     * Fully indexed, embedded, and synchronized across memory and database tiers.
     * Searchable across Lexical BM25, Dense Semantic, and Hybrid RRF modes.
     */
    ACTIVE,

    /**
     * Processing or indexing encountered a recoverable or unrecoverable error.
     */
    FAILED,

    /**
     * Scheduled for retry after a transient processing failure.
     */
    RETRY,

    /**
     * Document is undergoing deletion; immediately excluded from all active search queries.
     */
    DELETING,

    /**
     * Terminal state: all postings, positions, embeddings, and metadata have been purged.
     */
    DELETED;

    /**
     * Determines whether documents in this lifecycle state can be returned in search results.
     * Only ACTIVE documents are eligible for search visibility.
     */
    public boolean isSearchable() {
        return this == ACTIVE;
    }

    /**
     * Determines whether this is a terminal state.
     */
    public boolean isTerminal() {
        return this == DELETED;
    }
}
