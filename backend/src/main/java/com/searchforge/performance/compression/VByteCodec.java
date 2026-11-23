package com.searchforge.performance.compression;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * High-performance first-principles Variable-Byte (VByte) Integer Codec.
 *
 * Implements 7-bit continuation framing where each byte encodes 7 payload data bits,
 * and the most significant bit (0x80) signals continuation:
 *   - MSB = 1: more bytes follow for this integer
 *   - MSB = 0: final terminating byte for this integer
 *
 * Compression Characteristics:
 *   - Numbers 0 to 127:               1 Byte  (vs 4B int / 8B long) -> 75% - 87.5% reduction
 *   - Numbers 128 to 16,383:          2 Bytes (vs 4B int / 8B long) -> 50% - 75.0% reduction
 *   - Numbers 16,384 to 2,097,151:    3 Bytes (vs 4B int / 8B long) -> 25% - 62.5% reduction
 *   - Numbers 2,097,152 to 268,435,455: 4 Bytes
 */
public final class VByteCodec {

    private VByteCodec() {
        // Utility class
    }

    /**
     * Encodes an array of positive integers into a compact byte array.
     */
    public static byte[] encode(int[] values) {
        if (values == null || values.length == 0) {
            return new byte[0];
        }

        // Preallocate estimated capacity (avg ~2 bytes per int for typical index gaps)
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(values.length * 2, 32));
        for (int v : values) {
            encodeSingle(v, out);
        }
        return out.toByteArray();
    }

    /**
     * Encodes an array of positive 64-bit longs into a compact byte array.
     */
    public static byte[] encode(long[] values) {
        if (values == null || values.length == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(values.length * 2, 32));
        for (long v : values) {
            encodeSingle(v, out);
        }
        return out.toByteArray();
    }

    /**
     * Encodes a single non-negative long into the output stream.
     */
    public static void encodeSingle(long value, ByteArrayOutputStream out) {
        if (value < 0) {
            throw new IllegalArgumentException("VByteCodec only supports non-negative values, got: " + value);
        }

        long n = value;
        while (n >= 128L) {
            out.write((int) ((n & 0x7FL) | 0x80L));
            n >>>= 7;
        }
        out.write((int) (n & 0x7FL));
    }

    /**
     * Decodes a byte array back into an array of 32-bit integers of known count.
     *
     * @param bytes Compressed byte buffer
     * @param count Total number of integers expected
     * @return Decoded integer array
     */
    public static int[] decodeInts(byte[] bytes, int count) {
        if (bytes == null || bytes.length == 0 || count <= 0) {
            return new int[0];
        }

        int[] result = new int[count];
        int byteIndex = 0;

        for (int i = 0; i < count; i++) {
            int val = 0;
            int shift = 0;

            while (true) {
                if (byteIndex >= bytes.length) {
                    throw new IllegalStateException("Corrupt VByte stream: unexpected end of buffer at element " + i);
                }
                byte b = bytes[byteIndex++];
                val |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }

            result[i] = val;
        }

        return result;
    }

    /**
     * Decodes a byte array back into an array of 64-bit longs of known count.
     *
     * @param bytes Compressed byte buffer
     * @param count Total number of longs expected
     * @return Decoded long array
     */
    public static long[] decodeLongs(byte[] bytes, int count) {
        if (bytes == null || bytes.length == 0 || count <= 0) {
            return new long[0];
        }

        long[] result = new long[count];
        int byteIndex = 0;

        for (int i = 0; i < count; i++) {
            long val = 0;
            int shift = 0;

            while (true) {
                if (byteIndex >= bytes.length) {
                    throw new IllegalStateException("Corrupt VByte stream: unexpected end of buffer at element " + i);
                }
                byte b = bytes[byteIndex++];
                val |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }

            result[i] = val;
        }

        return result;
    }

    /**
     * Helper to encode sorted document IDs using Delta + VByte compression in a single step.
     */
    public static byte[] encodeDeltaVByte(long[] sortedDocIds) {
        long[] deltas = DeltaCodec.encodeDeltas(sortedDocIds);
        return encode(deltas);
    }

    /**
     * Helper to decode Delta + VByte compressed bytes back into original sorted document IDs.
     */
    public static long[] decodeDeltaVByte(byte[] compressedBytes, int count) {
        long[] deltas = decodeLongs(compressedBytes, count);
        return DeltaCodec.decodeDeltas(deltas);
    }
}
