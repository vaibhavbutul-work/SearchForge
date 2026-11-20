package com.searchforge.semantic.embedding;

import com.searchforge.semantic.embedding.exception.EmbeddingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, offline, zero-network embedding model implementation.
 *
 * Produces reproducible, normalized fixed-dimensional dense vectors (default 384-D)
 * using term hashing, positional sub-word projections, and L2 normalization.
 *
 * Guarantees hermetic test execution with sub-millisecond latency, zero external downloads,
 * and strict mathematical vector validation (no NaNs, no Infinities, unit Euclidean length).
 */
public class DeterministicEmbeddingModel implements EmbeddingModel {

    public static final int DEFAULT_DIMENSIONS = 384;
    public static final int DEFAULT_MAX_TOKENS = 256;
    public static final String MODEL_NAME = "deterministic-minilm-384d";

    private final int dimensions;
    private final int maxTokens;

    public DeterministicEmbeddingModel() {
        this(DEFAULT_DIMENSIONS, DEFAULT_MAX_TOKENS);
    }

    public DeterministicEmbeddingModel(int dimensions, int maxTokens) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Vector dimensions must be positive, got: " + dimensions);
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("Max tokens must be positive, got: " + maxTokens);
        }
        this.dimensions = dimensions;
        this.maxTokens = maxTokens;
    }

    @Override
    public float[] embed(String text) {
        validateInput(text);

        String[] tokens = text.trim().toLowerCase().split("\\s+");
        int tokenLimit = Math.min(tokens.length, maxTokens);

        float[] vector = new float[dimensions];

        for (int i = 0; i < tokenLimit; i++) {
            String token = tokens[i].replaceAll("[^a-z0-9]", "");
            if (token.isEmpty()) {
                continue;
            }

            // Factor in positional decay so leading terms contribute slightly more
            float positionWeight = 1.0f / (1.0f + 0.05f * i);

            // Whole token hash projection
            accumulateTokenHash(vector, token, positionWeight);

            // Sub-character 3-gram projections for morphological/subword awareness
            if (token.length() >= 3) {
                for (int j = 0; j <= token.length() - 3; j++) {
                    String sub = token.substring(j, j + 3);
                    accumulateTokenHash(vector, sub, positionWeight * 0.4f);
                }
            }
        }

        // L2 Unit Normalization
        normalizeL2(vector);

        // Sanity validation: check dimensions and verify no NaN or Infinite elements
        validateOutputVector(vector);

        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null) {
            throw new IllegalArgumentException("Batch input texts list cannot be null");
        }
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Batch element at index " + i + " cannot be null or blank");
            }
            results.add(embed(text));
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    private void accumulateTokenHash(float[] vector, String token, float weight) {
        int h1 = token.hashCode();
        int h2 = Integer.reverse(h1);

        // Disperse token energy across multiple dimensions using dual hash projections
        for (int d = 0; d < 4; d++) {
            int slot = Math.abs((h1 + d * 31 + h2 * 17) % dimensions);
            float sign = ((h1 >>> (d * 7)) & 1) == 0 ? 1.0f : -1.0f;
            vector[slot] += sign * weight;
        }
    }

    private void normalizeL2(float[] vector) {
        double sumSq = 0.0;
        for (float val : vector) {
            sumSq += (double) val * val;
        }

        double norm = Math.sqrt(sumSq);
        if (norm > 1e-9) {
            float invNorm = (float) (1.0 / norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= invNorm;
            }
        } else {
            // Uniform fallback vector if all inputs were stripped
            float uniformVal = (float) (1.0 / Math.sqrt(dimensions));
            for (int i = 0; i < vector.length; i++) {
                vector[i] = uniformVal;
            }
        }
    }

    private void validateInput(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text to embed cannot be null or blank");
        }
    }

    private void validateOutputVector(float[] vector) {
        if (vector.length != dimensions) {
            throw new EmbeddingException("Generated vector dimension mismatch: expected "
                    + dimensions + " but got " + vector.length);
        }
        for (int i = 0; i < vector.length; i++) {
            float val = vector[i];
            if (Float.isNaN(val) || Float.isInfinite(val)) {
                throw new EmbeddingException("Invalid numerical value at dimension index " + i + ": " + val);
            }
        }
    }
}
