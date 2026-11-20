package com.searchforge.semantic.embedding;

import java.util.List;

/**
 * Foundational abstraction for generating dense numerical embedding vectors from text.
 *
 * Transforms variable-length natural language text into a fixed-dimensional unit vector
 * in a continuous semantic latent space:
 *
 * <pre>
 *   f(text) -> float[] vector of size D
 * </pre>
 *
 * Texts with similar semantic intent or conceptual meaning will map to proximate vector
 * representations (high cosine similarity), overcoming lexical vocabulary mismatch in BM25.
 */
public interface EmbeddingModel {

    /**
     * Generates a dense floating-point embedding vector for the provided text.
     *
     * @param text input natural language string (must not be null or blank)
     * @return float array representing the dense embedding vector
     * @throws com.searchforge.semantic.embedding.exception.EmbeddingException if inference fails or output is invalid
     * @throws IllegalArgumentException if text is null or blank
     */
    float[] embed(String text);

    /**
     * Generates dense embedding vectors for a batch of input texts.
     * Batch processing amortizes inference dispatch overhead across multiple documents.
     *
     * @param texts list of input strings to embed
     * @return list of float arrays corresponding to each input text
     * @throws com.searchforge.semantic.embedding.exception.EmbeddingException if inference fails
     * @throws IllegalArgumentException if texts list is null or contains invalid elements
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Returns the fixed dimensionality of vectors produced by this model (e.g., 384 for all-MiniLM-L6-v2).
     *
     * @return vector dimension count
     */
    int dimensions();

    /**
     * Returns the canonical name or identifier of this embedding model.
     *
     * @return model name string
     */
    String modelName();
}
