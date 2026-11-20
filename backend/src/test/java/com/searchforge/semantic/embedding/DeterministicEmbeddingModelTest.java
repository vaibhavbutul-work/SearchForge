package com.searchforge.semantic.embedding;

import com.searchforge.semantic.embedding.exception.EmbeddingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeterministicEmbeddingModel Hermetic Unit Tests")
class DeterministicEmbeddingModelTest {

    private DeterministicEmbeddingModel model;

    @BeforeEach
    void setUp() {
        model = new DeterministicEmbeddingModel(384, 256);
    }

    @Test
    @DisplayName("Should initialize with valid dimensions and maxTokens")
    void testInitialization() {
        assertEquals(384, model.dimensions());
        assertEquals("deterministic-minilm-384d", model.modelName());

        DeterministicEmbeddingModel defaultModel = new DeterministicEmbeddingModel();
        assertEquals(384, defaultModel.dimensions());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on non-positive dimensions or maxTokens")
    void testInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new DeterministicEmbeddingModel(0, 256));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicEmbeddingModel(-1, 256));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicEmbeddingModel(384, 0));
        assertThrows(IllegalArgumentException.class, () -> new DeterministicEmbeddingModel(384, -10));
    }

    @Test
    @DisplayName("Should generate 384-dimensional normalized vector for standard text")
    void testEmbedStandardText() {
        float[] vector = model.embed("Spring Boot and Java 21 Virtual Threads");

        assertNotNull(vector);
        assertEquals(384, vector.length);

        // Verify Euclidean unit norm: ||v||_2 = 1.0
        double norm = calculateL2Norm(vector);
        assertEquals(1.0, norm, 1e-4, "Vector must be L2 normalized to unit length");
    }

    @Test
    @DisplayName("Should produce strictly deterministic vector for identical repeated calls")
    void testDeterminism() {
        String text = "PostgreSQL B-Tree indexing and query optimization";
        float[] v1 = model.embed(text);
        float[] v2 = model.embed(text);

        assertArrayEquals(v1, v2, "Repeated inference on identical text must yield identical vectors");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should reject null, empty, or blank text with IllegalArgumentException")
    void testInvalidInputs(String invalidText) {
        assertThrows(IllegalArgumentException.class, () -> model.embed(invalidText));
    }

    @Test
    @DisplayName("Should safely handle extremely long text without overflow or error")
    void testExtremelyLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("token").append(i).append(" ");
        }

        float[] vector = model.embed(sb.toString());
        assertNotNull(vector);
        assertEquals(384, vector.length);
        assertEquals(1.0, calculateL2Norm(vector), 1e-4);
    }

    @Test
    @DisplayName("Should execute batch embedding correctly")
    void testBatchEmbedding() {
        List<String> texts = List.of(
                "Java virtual threads",
                "Spring Boot microservices",
                "PostgreSQL indexing"
        );

        List<float[]> embeddings = model.embedBatch(texts);
        assertEquals(3, embeddings.size());

        for (int i = 0; i < texts.size(); i++) {
            assertEquals(384, embeddings.get(i).length);
            assertArrayEquals(model.embed(texts.get(i)), embeddings.get(i));
        }
    }

    @Test
    @DisplayName("Should return empty list when batch list is empty")
    void testEmptyBatch() {
        List<float[]> embeddings = model.embedBatch(List.of());
        assertNotNull(embeddings);
        assertTrue(embeddings.isEmpty());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when batch list is null or contains invalid elements")
    void testInvalidBatch() {
        assertThrows(IllegalArgumentException.class, () -> model.embedBatch(null));
        assertThrows(IllegalArgumentException.class, () -> model.embedBatch(java.util.Arrays.asList("valid", null)));
        assertThrows(IllegalArgumentException.class, () -> model.embedBatch(List.of("valid", "   ")));
    }

    @Test
    @DisplayName("Should yield higher cosine similarity for conceptually related queries than unrelated queries")
    void testSemanticCloseness() {
        float[] vDog = model.embed("domestic dog puppy canine pet");
        float[] vCanine = model.embed("puppy dog canine animal");
        float[] vDatabase = model.embed("relational database postgresql b-tree transaction");

        double simRelated = cosineSimilarity(vDog, vCanine);
        double simUnrelated = cosineSimilarity(vDog, vDatabase);

        assertTrue(simRelated > simUnrelated,
                String.format("Expected related similarity (%f) > unrelated similarity (%f)", simRelated, simUnrelated));
    }

    @Test
    @DisplayName("Verify vector elements contain no NaN or Infinities")
    void testNoNaNOrInfinities() {
        float[] vector = model.embed("Quantum entanglement and theoretical astrophysics");
        for (int i = 0; i < vector.length; i++) {
            assertFalse(Float.isNaN(vector[i]), "Vector element cannot be NaN at index " + i);
            assertFalse(Float.isInfinite(vector[i]), "Vector element cannot be Infinite at index " + i);
        }
    }

    private double calculateL2Norm(float[] vector) {
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += (double) v * v;
        }
        return Math.sqrt(sumSq);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }
}
