package com.searchforge.semantic.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance in-memory vector storage backed by a thread-safe {@link ConcurrentHashMap}.
 *
 * Provides sub-millisecond retrieval, atomic updates, and enforces uniform vector dimensionality.
 */
@Component
public class InMemoryVectorStorage implements VectorStorage {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStorage.class);
    public static final int DEFAULT_DIMENSIONS = 384;

    private final int dimensions;
    private final ConcurrentHashMap<Long, DocumentVector> vectorMap = new ConcurrentHashMap<>();

    public InMemoryVectorStorage() {
        this(DEFAULT_DIMENSIONS);
    }

    public InMemoryVectorStorage(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Vector storage dimensions must be positive, got: " + dimensions);
        }
        this.dimensions = dimensions;
    }

    @Override
    public void put(long documentId, float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("Vector cannot be null");
        }
        if (vector.length != dimensions) {
            throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: storage expects %d dimensions but got %d",
                    dimensions, vector.length));
        }

        // Ensure stored vector is strictly L2 normalized for dot-product similarity
        float[] normalized = VectorMath.normalize(vector);
        DocumentVector docVector = new DocumentVector(documentId, normalized);
        put(docVector);
    }

    @Override
    public void put(DocumentVector documentVector) {
        Objects.requireNonNull(documentVector, "DocumentVector cannot be null");
        if (documentVector.getDimensions() != dimensions) {
            throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: storage expects %d dimensions but got %d",
                    dimensions, documentVector.getDimensions()));
        }

        // If not already unit normalized, normalize it
        DocumentVector toStore = documentVector.isUnitNormalized()
                ? documentVector
                : new DocumentVector(documentVector.getDocumentId(), VectorMath.normalize(documentVector.getValues()));

        vectorMap.put(toStore.getDocumentId(), toStore);
    }

    @Override
    public Optional<DocumentVector> putAndGetPrevious(DocumentVector documentVector) {
        Objects.requireNonNull(documentVector, "DocumentVector cannot be null");
        if (documentVector.getDimensions() != dimensions) {
            throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: storage expects %d dimensions but got %d",
                    dimensions, documentVector.getDimensions()));
        }

        DocumentVector toStore = documentVector.isUnitNormalized()
                ? documentVector
                : new DocumentVector(documentVector.getDocumentId(), VectorMath.normalize(documentVector.getValues()));

        return Optional.ofNullable(vectorMap.put(toStore.getDocumentId(), toStore));
    }

    @Override
    public boolean replace(long documentId, DocumentVector expectedOld, DocumentVector newVector) {
        Objects.requireNonNull(newVector, "New DocumentVector cannot be null");
        if (newVector.getDimensions() != dimensions) {
            throw new IllegalArgumentException(String.format(
                    "Dimension mismatch: storage expects %d dimensions but got %d",
                    dimensions, newVector.getDimensions()));
        }

        DocumentVector toStore = newVector.isUnitNormalized()
                ? newVector
                : new DocumentVector(newVector.getDocumentId(), VectorMath.normalize(newVector.getValues()));

        if (expectedOld == null) {
            return vectorMap.putIfAbsent(documentId, toStore) == null;
        }
        return vectorMap.replace(documentId, expectedOld, toStore);
    }

    @Override
    public Optional<DocumentVector> get(long documentId) {
        return Optional.ofNullable(vectorMap.get(documentId));
    }

    @Override
    public boolean contains(long documentId) {
        return vectorMap.containsKey(documentId);
    }

    @Override
    public boolean remove(long documentId) {
        return vectorMap.remove(documentId) != null;
    }

    @Override
    public void clear() {
        vectorMap.clear();
    }

    @Override
    public int size() {
        return vectorMap.size();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<Long> getAllDocumentIds() {
        return vectorMap.keySet().stream().sorted().toList();
    }

    @Override
    public List<DocumentVector> getAllVectors() {
        return List.copyOf(vectorMap.values());
    }

    @Override
    public void forEachVector(java.util.function.Consumer<DocumentVector> consumer) {
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        vectorMap.values().forEach(consumer);
    }

    /**
     * Estimates RAM consumption of stored vectors in bytes.
     * Each float = 4 bytes, plus Java object headers and map entry overhead (~64 bytes).
     */
    public long estimateMemoryUsageBytes() {
        long perVectorBytes = (long) dimensions * Float.BYTES + 64L;
        return (long) vectorMap.size() * perVectorBytes;
    }
}
