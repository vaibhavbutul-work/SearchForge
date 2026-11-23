package com.searchforge.performance.compression;

import java.util.Arrays;
import java.util.Objects;

/**
 * First-principles Delta Gap Encoder and Decoder for sorted sequence arrays.
 *
 * In an inverted index, document IDs and positional offsets are strictly monotonic ascending:
 *   d_0 < d_1 < d_2 < ... < d_k
 *
 * By replacing absolute IDs with relative gaps:
 *   Δ_0 = d_0,  Δ_i = d_i - d_{i-1}  (for i >= 1)
 *
 * Large document identifiers (e.g. 100,500 -> 100,503 -> 100,507) are compressed into tiny numbers
 * (100,500 -> 3 -> 4), allowing subsequent Variable-Byte compression to pack each gap into 1 or 2 bytes.
 */
public final class DeltaCodec {

    private DeltaCodec() {
        // Utility class
    }

    /**
     * Encodes a strictly ascending sorted array of document IDs into delta gaps.
     *
     * @param sortedValues Strictly sorted ascending array
     * @return Array of delta gaps of identical length
     */
    public static long[] encodeDeltas(long[] sortedValues) {
        if (sortedValues == null || sortedValues.length == 0) {
            return new long[0];
        }

        long[] deltas = new long[sortedValues.length];
        deltas[0] = sortedValues[0];

        for (int i = 1; i < sortedValues.length; i++) {
            long diff = sortedValues[i] - sortedValues[i - 1];
            if (diff < 0) {
                throw new IllegalArgumentException(String.format(
                        "Values must be strictly monotonic ascending, but element %d (%d) < element %d (%d)",
                        i, sortedValues[i], i - 1, sortedValues[i - 1]));
            }
            deltas[i] = diff;
        }

        return deltas;
    }

    /**
     * Decodes an array of delta gaps back into the original strictly ascending sorted array.
     *
     * @param deltas Delta gaps array
     * @return Reconstructed strictly ascending array
     */
    public static long[] decodeDeltas(long[] deltas) {
        if (deltas == null || deltas.length == 0) {
            return new long[0];
        }

        long[] values = new long[deltas.length];
        long accumulator = 0;

        for (int i = 0; i < deltas.length; i++) {
            accumulator += deltas[i];
            values[i] = accumulator;
        }

        return values;
    }

    /**
     * In-place delta encoding for integer position arrays.
     */
    public static int[] encodeDeltas(int[] sortedValues) {
        if (sortedValues == null || sortedValues.length == 0) {
            return new int[0];
        }

        int[] deltas = new int[sortedValues.length];
        deltas[0] = sortedValues[0];

        for (int i = 1; i < sortedValues.length; i++) {
            int diff = sortedValues[i] - sortedValues[i - 1];
            if (diff < 0) {
                throw new IllegalArgumentException("Values must be non-decreasing for delta encoding");
            }
            deltas[i] = diff;
        }

        return deltas;
    }

    /**
     * In-place delta decoding for integer position arrays.
     */
    public static int[] decodeDeltas(int[] deltas) {
        if (deltas == null || deltas.length == 0) {
            return new int[0];
        }

        int[] values = new int[deltas.length];
        int accumulator = 0;

        for (int i = 0; i < deltas.length; i++) {
            accumulator += deltas[i];
            values[i] = accumulator;
        }

        return values;
    }
}
