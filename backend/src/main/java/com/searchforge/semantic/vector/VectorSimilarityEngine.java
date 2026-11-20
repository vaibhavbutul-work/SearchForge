package com.searchforge.semantic.vector;

import java.util.List;

/**
 * Contract for dense vector retrieval engines.
 *
 * Given a query vector, retrieves the top-K most similar documents based on vector similarity.
 */
public interface VectorSimilarityEngine {

    /**
     * Executes similarity search against stored document vectors.
     *
     * @param queryVector dense query vector coordinates
     * @param topK maximum number of top results to return (K &gt; 0)
     * @return ranked list of VectorSearchResult objects sorted descending by similarity
     * @throws IllegalArgumentException if queryVector is null, dimensions mismatch, or topK &lt;= 0
     */
    List<VectorSearchResult> search(float[] queryVector, int topK);
}
