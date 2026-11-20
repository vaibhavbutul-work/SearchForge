package com.searchforge.semantic.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryVectorStorage Unit Tests")
class InMemoryVectorStorageTest {

    private InMemoryVectorStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryVectorStorage(3);
    }

    @Test
    @DisplayName("Should store and retrieve document vectors")
    void testPutAndGet() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        storage.put(1L, v1);

        assertEquals(1, storage.size());
        assertTrue(storage.contains(1L));

        Optional<DocumentVector> result = storage.get(1L);
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getDocumentId());
        assertEquals(3, result.get().getDimensions());
        assertTrue(result.get().isUnitNormalized());
    }

    @Test
    @DisplayName("Should normalize unnormalized vectors on storage")
    void testAutoNormalizeOnPut() {
        float[] unnorm = new float[]{3.0f, 0.0f, 4.0f}; // Norm = 5.0
        storage.put(2L, unnorm);

        Optional<DocumentVector> result = storage.get(2L);
        assertTrue(result.isPresent());
        assertEquals(1.0, result.get().l2Norm(), 1e-4);
        assertEquals(0.6f, result.get().get(0), 1e-4);
        assertEquals(0.8f, result.get().get(2), 1e-4);
    }

    @Test
    @DisplayName("Should enforce uniform dimension validation")
    void testDimensionMismatch() {
        float[] wrongDim = new float[]{1.0f, 2.0f}; // 2 instead of 3
        assertThrows(IllegalArgumentException.class, () -> storage.put(3L, wrongDim));
    }

    @Test
    @DisplayName("Should update existing vector when document ID is stored again")
    void testUpdateExisting() {
        storage.put(1L, new float[]{1.0f, 0.0f, 0.0f});
        assertEquals(1, storage.size());

        storage.put(1L, new float[]{0.0f, 1.0f, 0.0f});
        assertEquals(1, storage.size());

        Optional<DocumentVector> result = storage.get(1L);
        assertTrue(result.isPresent());
        assertEquals(1.0f, result.get().get(1), 1e-4);
    }

    @Test
    @DisplayName("Should remove and clear vectors correctly")
    void testRemoveAndClear() {
        storage.put(1L, new float[]{1.0f, 0.0f, 0.0f});
        storage.put(2L, new float[]{0.0f, 1.0f, 0.0f});
        assertEquals(2, storage.size());

        assertTrue(storage.remove(1L));
        assertFalse(storage.contains(1L));
        assertFalse(storage.remove(99L)); // Nonexistent
        assertEquals(1, storage.size());

        storage.clear();
        assertEquals(0, storage.size());
        assertTrue(storage.getAllDocumentIds().isEmpty());
    }

    @Test
    @DisplayName("Should return sorted list of all document IDs")
    void testGetAllDocumentIds() {
        storage.put(5L, new float[]{1.0f, 0.0f, 0.0f});
        storage.put(1L, new float[]{0.0f, 1.0f, 0.0f});
        storage.put(3L, new float[]{0.0f, 0.0f, 1.0f});

        List<Long> ids = storage.getAllDocumentIds();
        assertEquals(List.of(1L, 3L, 5L), ids);
    }

    @Test
    @DisplayName("Should estimate memory usage accurately")
    void testMemoryEstimation() {
        storage.put(1L, new float[]{1.0f, 0.0f, 0.0f});
        storage.put(2L, new float[]{0.0f, 1.0f, 0.0f});

        long bytes = storage.estimateMemoryUsageBytes();
        assertTrue(bytes > 0);
        // 2 vectors * (3 * 4 + 64) = 2 * 76 = 152 bytes
        assertEquals(152L, bytes);
    }
}
