package com.searchforge.semantic.vector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compact binary serialization utility for dense float vectors.
 *
 * Converts float arrays to and from raw IEEE 754 32-bit binary buffers,
 * avoiding expensive string parsing (JSON/CSV) and saving ~70% storage space.
 */
public final class VectorSerialization {

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private VectorSerialization() {
        // Utility
    }

    /**
     * Serializes a float array into a compact byte array (4 bytes per dimension).
     */
    public static byte[] toByteArray(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("Cannot serialize null vector");
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(BYTE_ORDER);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /**
     * Deserializes a raw byte array back into a float array.
     */
    public static float[] fromByteArray(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Cannot deserialize null byte array");
        }
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Invalid vector byte array length: " + bytes.length
                    + " (must be a multiple of 4 bytes)");
        }

        int count = bytes.length / Float.BYTES;
        float[] vector = new float[count];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(BYTE_ORDER);
        for (int i = 0; i < count; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
