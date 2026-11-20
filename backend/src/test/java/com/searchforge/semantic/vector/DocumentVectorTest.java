package com.searchforge.semantic.vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentVector Unit Tests")
class DocumentVectorTest {

    @Test
    @DisplayName("Should create valid DocumentVector and preserve values immutably")
    void testValidCreation() {
        float[] values = new float[]{0.6f, 0.8f};
        DocumentVector docVec = new DocumentVector(42L, values);

        assertEquals(42L, docVec.getDocumentId());
        assertEquals(2, docVec.getDimensions());
        assertEquals(0.6f, docVec.get(0));
        assertEquals(0.8f, docVec.get(1));
        assertEquals(1.0, docVec.l2Norm(), 1e-4);
        assertTrue(docVec.isUnitNormalized());

        // Verify defensive copy on read
        float[] clonedValues = docVec.getValues();
        clonedValues[0] = 999.0f;
        assertEquals(0.6f, docVec.get(0), "Internal values must remain immutable");
    }

    @Test
    @DisplayName("Should reject invalid constructor arguments")
    void testInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentVector(0L, new float[]{1.0f}));
        assertThrows(IllegalArgumentException.class, () -> new DocumentVector(-5L, new float[]{1.0f}));
        assertThrows(IllegalArgumentException.class, () -> new DocumentVector(1L, null));
        assertThrows(IllegalArgumentException.class, () -> new DocumentVector(1L, new float[]{}));
        assertThrows(IllegalArgumentException.class, () -> new DocumentVector(1L, new float[]{1.0f, Float.NaN}));
    }

    @Test
    @DisplayName("Should enforce boundary checks on get(index)")
    void testIndexBounds() {
        DocumentVector docVec = new DocumentVector(1L, new float[]{0.1f, 0.2f});
        assertThrows(IndexOutOfBoundsException.class, () -> docVec.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> docVec.get(2));
    }

    @Test
    @DisplayName("Verify equals, hashCode, and toString")
    void testEqualsAndHashCode() {
        DocumentVector v1 = new DocumentVector(10L, new float[]{0.5f, 0.5f});
        DocumentVector v2 = new DocumentVector(10L, new float[]{0.5f, 0.5f});
        DocumentVector v3 = new DocumentVector(11L, new float[]{0.5f, 0.5f});

        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertNotEquals(v1, v3);
        assertTrue(v1.toString().contains("documentId=10"));
    }
}
