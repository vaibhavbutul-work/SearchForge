package com.searchforge.semantic.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Configuration properties for semantic embedding generation, model assets, and local inference.
 */
@Component
@ConfigurationProperties(prefix = "semantic.embedding")
public class EmbeddingProperties {

    /**
     * Whether semantic retrieval and embedding generation are enabled.
     */
    private boolean enabled = true;

    /**
     * Model implementation type: "onnx" (real transformer inference) or "deterministic" (offline testing).
     */
    private String modelType = "deterministic";

    /**
     * Model name identifier.
     */
    private String modelName = "all-MiniLM-L6-v2";

    /**
     * Fixed vector dimensionality (384 for all-MiniLM-L6-v2).
     */
    private int dimensions = 384;

    /**
     * Maximum sequence length in tokens (defaults to 256).
     */
    private int maxSequenceLength = 256;

    /**
     * Batch size for bulk document embedding operations.
     */
    private int batchSize = 32;

    /**
     * File path to the local ONNX model file (*.onnx).
     */
    private String onnxModelPath = "";

    /**
     * File path or classpath location of the WordPiece vocabulary (defaults to classpath:models/vocab.txt).
     */
    private String tokenizerPath = "classpath:models/vocab.txt";

    /**
     * Whether LRU query embedding caching is enabled.
     */
    private boolean cacheEnabled = true;

    /**
     * Maximum capacity for the LRU query embedding cache.
     */
    private int cacheCapacity = 1000;

    /**
     * Whether to allow falling back to deterministic embeddings if ONNX model is missing.
     * Defaults to true for test/dev convenience with an explicit warning log.
     */
    private boolean allowFallbackToDeterministic = true;

    /**
     * Resolves the actual ONNX model file path checking:
     * 1. Explicit configured property {@code onnxModelPath}
     * 2. System property {@code searchforge.model.path}
     * 3. Environment variable {@code SEARCHFORGE_MODEL_PATH}
     */
    public String resolveEffectiveModelPath() {
        if (onnxModelPath != null && !onnxModelPath.isBlank()) {
            return onnxModelPath.trim();
        }
        String sysProp = System.getProperty("searchforge.model.path");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String envVar = System.getenv("SEARCHFORGE_MODEL_PATH");
        if (envVar != null && !envVar.isBlank()) {
            return envVar.trim();
        }
        return "";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public void setMaxSequenceLength(int maxSequenceLength) {
        this.maxSequenceLength = maxSequenceLength;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getOnnxModelPath() {
        return onnxModelPath;
    }

    public void setOnnxModelPath(String onnxModelPath) {
        this.onnxModelPath = onnxModelPath;
    }

    public String getTokenizerPath() {
        return tokenizerPath;
    }

    public void setTokenizerPath(String tokenizerPath) {
        this.tokenizerPath = tokenizerPath;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheCapacity() {
        return cacheCapacity;
    }

    public void setCacheCapacity(int cacheCapacity) {
        this.cacheCapacity = cacheCapacity;
    }

    public boolean isAllowFallbackToDeterministic() {
        return allowFallbackToDeterministic;
    }

    public void setAllowFallbackToDeterministic(boolean allowFallbackToDeterministic) {
        this.allowFallbackToDeterministic = allowFallbackToDeterministic;
    }
}
