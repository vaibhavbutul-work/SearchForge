package com.searchforge.semantic.vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VectorSerialization Unit Tests")
class VectorSerializationTest {

    @Test
    @DisplayName("Should serialize and deserialize float vectors losslessly")
    void testSerializationRoundtrip() {
        float[] original = new float[]{0.12345f, -0.6789f, 3.14159f, 0.0f, -100.25f};

        byte[] bytes = VectorSerialization.toByteArray(original);
        assertNotNull(bytes);
        assertEquals(original.length * 4, bytes.length);

        float[] restored = VectorSerialization.fromByteArray(bytes);
        assertNotNull(restored);
        assertArrayEquals(original, restored);
    }

    @Test
    @DisplayName("Should handle 384-dimensional vector serialization correctly")
    void test384DimensionalSerialization() {
        float[] vec = new float[384];
        for (int i = 0; i < 384; i++) {
            vec[i] = (float) (Math.sin(i) * 0.5);
        }

        byte[] bytes = VectorSerialization.toByteArray(vec);
        assertEquals(384 * 4, bytes.length); // 1,536 bytes

        float[] restored = VectorSerialization.fromByteArray(bytes);
        assertArrayEquals(vec, restored);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on invalid byte array length")
    void testInvalidByteLength() {
        byte[] invalidBytes = new byte[7]; // Not a multiple of 4
        assertThrows(IllegalArgumentException.class, () -> VectorSerialization.fromByteArray(invalidBytes));
    }

    @Test
    @DisplayName("Should reject null arguments")
    void testNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> VectorSerialization.toByteArray(null));
        assertThrows(IllegalArgumentException.class, () -> VectorSerialization.fromByteArray(null));
    }
}
