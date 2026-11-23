package com.searchforge.indexer.model;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

/**
 * Represents the occurrence of a term in a specific document.
 *
 * Optimized for low memory footprint: internal positions are stored as a compact primitive int[] array,
 * eliminating boxed Integer object overhead and reference arrays while providing a backward-compatible
 * List<Integer> view for existing query contracts.
 */
public final class Posting implements Comparable<Posting> {

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private final long documentId;
    private final int termFrequency;
    private final int[] packedPositions;

    /**
     * Primary constructor taking primitive integer array.
     */
    public Posting(long documentId, int termFrequency, int[] positions) {
        if (termFrequency <= 0) {
            throw new IllegalArgumentException("Term frequency must be positive");
        }
        this.documentId = documentId;
        this.termFrequency = termFrequency;
        this.packedPositions = (positions != null && positions.length > 0)
                ? positions.clone()
                : EMPTY_INT_ARRAY;
    }

    /**
     * Backward-compatible constructor accepting boxed List<Integer>.
     */
    public Posting(long documentId, int termFrequency, List<Integer> positions) {
        if (termFrequency <= 0) {
            throw new IllegalArgumentException("Term frequency must be positive");
        }
        this.documentId = documentId;
        this.termFrequency = termFrequency;

        if (positions == null || positions.isEmpty()) {
            this.packedPositions = EMPTY_INT_ARRAY;
        } else {
            this.packedPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                this.packedPositions[i] = positions.get(i);
            }
        }
    }

    public static Posting of(long documentId, List<Integer> positions) {
        int tf = (positions != null && !positions.isEmpty()) ? positions.size() : 1;
        return new Posting(documentId, tf, positions);
    }

    public static Posting of(long documentId, int[] positions) {
        int tf = (positions != null && positions.length > 0) ? positions.length : 1;
        return new Posting(documentId, tf, positions);
    }

    public long documentId() {
        return documentId;
    }

    public int termFrequency() {
        return termFrequency;
    }

    /**
     * Returns an unmodifiable, random-access List view of the positions without boxing upfront.
     */
    public List<Integer> positions() {
        if (packedPositions.length == 0) {
            return Collections.emptyList();
        }
        return new IntArrayListView(packedPositions);
    }

    /**
     * Zero-allocation direct access to the underlying primitive position array.
     */
    public int[] rawPositions() {
        return packedPositions;
    }

    /**
     * Zero-allocation positional lookup by index.
     */
    public int positionAt(int index) {
        return packedPositions[index];
    }

    /**
     * Total number of stored positions.
     */
    public int positionCount() {
        return packedPositions.length;
    }

    @Override
    public int compareTo(Posting other) {
        return Long.compare(this.documentId, other.documentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Posting other)) return false;
        return this.documentId == other.documentId
                && this.termFrequency == other.termFrequency
                && Arrays.equals(this.packedPositions, other.packedPositions);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(documentId);
        result = 31 * result + Integer.hashCode(termFrequency);
        result = 31 * result + Arrays.hashCode(packedPositions);
        return result;
    }

    @Override
    public String toString() {
        return "Posting[documentId=" + documentId + ", termFrequency=" + termFrequency + ", positions=" + positions() + "]";
    }

    /**
     * Lightweight unmodifiable List view over a primitive int[] array.
     */
    public static final class IntArrayListView extends AbstractList<Integer> implements RandomAccess {
        private final int[] array;

        public IntArrayListView(int[] array) {
            this.array = (array != null) ? array : EMPTY_INT_ARRAY;
        }

        @Override
        public Integer get(int index) {
            return array[index];
        }

        @Override
        public int size() {
            return array.length;
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Integer val) {
                int target = val;
                for (int x : array) {
                    if (x == target) return true;
                }
            }
            return false;
        }

        @Override
        public int indexOf(Object o) {
            if (o instanceof Integer val) {
                int target = val;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == target) return i;
                }
            }
            return -1;
        }
    }
}
