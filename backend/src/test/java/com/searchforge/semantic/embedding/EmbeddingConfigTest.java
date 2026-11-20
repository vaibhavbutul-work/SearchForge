package com.searchforge.semantic.embedding;

import com.searchforge.semantic.embedding.exception.ModelInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmbeddingConfig & Properties Unit Tests")
class EmbeddingConfigTest {

    @Test
    @DisplayName("Should default to DeterministicEmbeddingModel when modelType is deterministic")
    void testDefaultConfiguration() {
        EmbeddingProperties properties = new EmbeddingProperties();
        EmbeddingConfig config = new EmbeddingConfig(properties);

        EmbeddingModel model = config.embeddingModel();
        assertNotNull(model);
        assertEquals(384, model.dimensions());
        assertInstanceOf(DeterministicEmbeddingModel.class, model);
    }

    @Test
    @DisplayName("Should fallback gracefully to deterministic model when ONNX model path is empty")
    void testOnnxFallbackOnMissingPath() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setModelType("onnx");
        properties.setOnnxModelPath(""); // empty path

        EmbeddingConfig config = new EmbeddingConfig(properties);
        EmbeddingModel model = config.embeddingModel();

        assertNotNull(model);
        assertInstanceOf(DeterministicEmbeddingModel.class, model);
    }

    @Test
    @DisplayName("Should throw ModelInitializationException when OnnxEmbeddingModel initialized with nonexistent file")
    void testOnnxInitializationFailure() {
        assertThrows(ModelInitializationException.class, () ->
                new OnnxEmbeddingModel("nonexistent-model.onnx", "", 384, 256));
    }

    @Test
    @DisplayName("Verify EmbeddingProperties getters and setters")
    void testPropertiesAccessors() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setModelType("onnx");
        properties.setModelName("test-model");
        properties.setDimensions(512);
        properties.setMaxSequenceLength(128);
        properties.setBatchSize(64);
        properties.setOnnxModelPath("/tmp/model.onnx");
        properties.setTokenizerPath("/tmp/vocab.txt");

        assertTrue(properties.isEnabled());
        assertEquals("onnx", properties.getModelType());
        assertEquals("test-model", properties.getModelName());
        assertEquals(512, properties.getDimensions());
        assertEquals(128, properties.getMaxSequenceLength());
        assertEquals(64, properties.getBatchSize());
        assertEquals("/tmp/model.onnx", properties.getOnnxModelPath());
        assertEquals("/tmp/vocab.txt", properties.getTokenizerPath());
    }
}
