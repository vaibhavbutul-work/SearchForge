package com.searchforge.semantic.vector;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable domain representation of a dense document embedding vector.
 *
 * Couples a document identifier with its dense numerical coordinates in latent semantic space.
 */
public final class DocumentVector {

    private final long documentId;
    private final float[] values;
    private final int dimensions;

    public DocumentVector(long documentId, float[] values) {
        if (documentId <= 0) {
            throw new IllegalArgumentException("Document ID must be positive, got: " + documentId);
        }
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Vector values cannot be null or empty");
        }

        this.documentId = documentId;
        this.dimensions = values.length;
        this.values = new float[this.dimensions];

        for (int i = 0; i < this.dimensions; i++) {
            float v = values[i];
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                throw new IllegalArgumentException("Vector contains invalid float (NaN or Infinite) at index " + i);
            }
            this.values[i] = v;
        }
    }

    public long getDocumentId() {
        return documentId;
    }

    public int getDimensions() {
        return dimensions;
    }

    /**
     * Returns a defensive copy of the raw float vector values.
     */
    public float[] getValues() {
        return values.clone();
    }

    /**
     * Direct read access at dimension index.
     */
    public float get(int index) {
        if (index < 0 || index >= dimensions) {
            throw new IndexOutOfBoundsException("Dimension index " + index + " out of bounds for size " + dimensions);
        }
        return values[index];
    }

    public double l2Norm() {
        return VectorMath.l2Norm(values);
    }

    public boolean isUnitNormalized() {
        return Math.abs(l2Norm() - 1.0) < 1e-3;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentVector that)) return false;
        return documentId == that.documentId
                && dimensions == that.dimensions
                && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(documentId, dimensions);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }

    @Override
    public String toString() {
        return "DocumentVector{" +
                "documentId=" + documentId +
                ", dimensions=" + dimensions +
                ", norm=" + String.format("%.4f", l2Norm()) +
                '}';
    }
}
