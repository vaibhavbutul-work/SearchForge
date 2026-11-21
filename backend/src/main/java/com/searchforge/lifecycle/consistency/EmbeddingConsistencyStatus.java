package com.searchforge.lifecycle.consistency;

/**
 * Enumeration of possible consistency states between a document and its vector embedding.
 */
public enum EmbeddingConsistencyStatus {
    /**
     * Embedding exists, matches active document version, content hash, model name, and dimensions.
     */
    CURRENT,

    /**
     * Document is ACTIVE, but has no embedding in the repository or vector storage.
     */
    MISSING,

    /**
     * Embedding exists, but document content hash or version does not match.
     */
    OUTDATED,

    /**
     * Embedding exists, but model name or dimensions do not match the currently configured model.
     */
    INCOMPATIBLE,

    /**
     * Embedding exists in repository or vector storage, but document does not exist or is marked DELETED.
     */
    ORPHANED,

    /**
     * Embedding exists in relational DB but missing from in-memory VectorStorage, or vice versa.
     */
    DESYNCHRONIZED
}
