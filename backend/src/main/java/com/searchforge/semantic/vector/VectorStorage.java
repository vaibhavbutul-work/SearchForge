package com.searchforge.semantic.vector;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for dense document vectors.
 *
 * Enforces uniform dimensionality across all stored document representations
 * and supports fast retrieval, bulk enumeration, and updates.
 */
public interface VectorStorage {

    /**
     * Stores or updates the vector representation for a document.
     *
     * @param documentId unique document identifier
     * @param vector raw float coordinates
     */
    void put(long documentId, float[] vector);

    /**
     * Stores or updates a validated DocumentVector instance.
     */
    void put(DocumentVector documentVector);

    /**
     * Stores or updates a DocumentVector and returns the previous vector if present.
     * Useful for transactional compensation and rollback tracking.
     */
    Optional<DocumentVector> putAndGetPrevious(DocumentVector documentVector);

    /**
     * Atomically replaces the vector for a document only if currently mapped to expectedOld.
     */
    boolean replace(long documentId, DocumentVector expectedOld, DocumentVector newVector);

    /**
     * Retrieves the DocumentVector for a document ID if present.
     */
    Optional<DocumentVector> get(long documentId);

    /**
     * Checks whether an embedding vector exists for the document ID.
     */
    boolean contains(long documentId);

    /**
     * Removes a document's vector representation.
     */
    boolean remove(long documentId);

    /**
     * Removes all vectors from storage.
     */
    void clear();

    /**
     * Returns the count of indexed document vectors.
     */
    int size();

    /**
     * Returns the uniform vector dimensionality expected by this storage (e.g. 384).
     */
    int dimensions();

    /**
     * Returns a list of all document IDs that have indexed vectors.
     */
    List<Long> getAllDocumentIds();

    /**
     * Returns all stored DocumentVector objects for exact similarity scans.
     */
    List<DocumentVector> getAllVectors();

    /**
     * Iterates over all stored document vectors without allocating an intermediate collection.
     *
     * @param consumer consumer receiving each stored DocumentVector
     */
    default void forEachVector(java.util.function.Consumer<DocumentVector> consumer) {
        java.util.Objects.requireNonNull(consumer, "Consumer cannot be null");
        getAllVectors().forEach(consumer);
    }
}
