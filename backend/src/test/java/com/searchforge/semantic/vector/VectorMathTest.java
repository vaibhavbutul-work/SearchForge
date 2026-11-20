package com.searchforge.semantic.vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VectorMath Unit Tests")
class VectorMathTest {

    private static final double EPSILON = 1e-5;

    @Test
    @DisplayName("Should calculate L2 norm accurately")
    void testL2Norm() {
        float[] v = new float[]{3.0f, 4.0f}; // 3-4-5 right triangle
        assertEquals(5.0, VectorMath.l2Norm(v), EPSILON);

        float[] unit = new float[]{0.0f, 1.0f, 0.0f};
        assertEquals(1.0, VectorMath.l2Norm(unit), EPSILON);
    }

    @Test
    @DisplayName("Should normalize vector to unit length ||v|| = 1.0")
    void testNormalize() {
        float[] v = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] norm = VectorMath.normalize(v);

        assertEquals(1.0, VectorMath.l2Norm(norm), EPSILON);
    }

    @Test
    @DisplayName("Should handle zero-vector safely without producing NaN or Infinity")
    void testZeroVectorNormalize() {
        float[] zero = new float[]{0.0f, 0.0f, 0.0f};
        float[] norm = VectorMath.normalize(zero);

        assertNotNull(norm);
        assertEquals(3, norm.length);
        for (float val : norm) {
            assertEquals(0.0f, val);
            assertFalse(Float.isNaN(val));
            assertFalse(Float.isInfinite(val));
        }
    }

    @Test
    @DisplayName("Should compute correct dot product")
    void testDotProduct() {
        float[] a = new float[]{1.0f, 2.0f, 3.0f};
        float[] b = new float[]{4.0f, 5.0f, 6.0f};

        // 1*4 + 2*5 + 3*6 = 4 + 10 + 18 = 32.0
        assertEquals(32.0, VectorMath.dotProduct(a, b), EPSILON);
    }

    @Test
    @DisplayName("Should compute cosine similarity for known geometric relationships")
    void testCosineSimilarityGeometry() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{0.0f, 1.0f, 0.0f}; // Orthogonal (90 degrees)
        float[] v3 = new float[]{-1.0f, 0.0f, 0.0f}; // Opposite (180 degrees)
        float[] v4 = new float[]{2.0f, 0.0f, 0.0f}; // Identical direction, different magnitude

        // Identical direction
        assertEquals(1.0f, VectorMath.cosineSimilarity(v1, v1), 1e-4f);
        assertEquals(1.0f, VectorMath.cosineSimilarity(v1, v4), 1e-4f);

        // Orthogonal
        assertEquals(0.0f, VectorMath.cosineSimilarity(v1, v2), 1e-4f);

        // Opposite
        assertEquals(-1.0f, VectorMath.cosineSimilarity(v1, v3), 1e-4f);
    }

    @Test
    @DisplayName("Should return 0.0 cosine similarity when one or both vectors are zero")
    void testCosineSimilarityZeroVectors() {
        float[] zero = new float[]{0.0f, 0.0f};
        float[] nonZero = new float[]{1.0f, 2.0f};

        assertEquals(0.0f, VectorMath.cosineSimilarity(zero, nonZero), EPSILON);
        assertEquals(0.0f, VectorMath.cosineSimilarity(nonZero, zero), EPSILON);
        assertEquals(0.0f, VectorMath.cosineSimilarity(zero, zero), EPSILON);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on dimension mismatch")
    void testDimensionMismatch() {
        float[] a = new float[]{1.0f, 2.0f};
        float[] b = new float[]{1.0f, 2.0f, 3.0f};

        assertThrows(IllegalArgumentException.class, () -> VectorMath.dotProduct(a, b));
        assertThrows(IllegalArgumentException.class, () -> VectorMath.cosineSimilarity(a, b));
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("Should throw IllegalArgumentException on null vector")
    void testNullVectors(float[] nullVector) {
        assertThrows(IllegalArgumentException.class, () -> VectorMath.l2Norm(nullVector));
        assertThrows(IllegalArgumentException.class, () -> VectorMath.normalize(nullVector));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on empty vector")
    void testEmptyVector() {
        float[] empty = new float[]{};
        assertThrows(IllegalArgumentException.class, () -> VectorMath.l2Norm(empty));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if vector contains NaN or Infinity")
    void testNaNOrInfinityValidation() {
        float[] nanVec = new float[]{1.0f, Float.NaN, 3.0f};
        float[] infVec = new float[]{1.0f, Float.POSITIVE_INFINITY, 3.0f};

        assertThrows(IllegalArgumentException.class, () -> VectorMath.l2Norm(nanVec));
        assertThrows(IllegalArgumentException.class, () -> VectorMath.l2Norm(infVec));
    }
}
