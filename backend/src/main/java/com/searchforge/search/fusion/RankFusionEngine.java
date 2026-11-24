package com.searchforge.search.fusion;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for fusing rankings from multiple independent retrieval engines.
 */
public interface RankFusionEngine {

    /**
     * Combines multiple ranked candidate lists into a unified hybrid ranking with uniform weights.
     *
     * @param rankedLists map of source names to ranked candidate lists
     * @param topK maximum number of top results to return (topK &gt; 0)
     * @return ranked list of FusedDocumentResult sorted descending by fused score
     * @throws IllegalArgumentException if topK &lt;= 0
     */
    default List<FusedDocumentResult> fuse(Map<String, List<RankedItem>> rankedLists, int topK) {
        return fuse(rankedLists, Map.of(), topK);
    }

    /**
     * Combines multiple ranked candidate lists into a unified hybrid ranking with optional per-source weights.
     *
     * @param rankedLists map of source names to ranked candidate lists
     * @param sourceWeights map of source names to weight multipliers (defaults to 1.0 if not present)
     * @param topK maximum number of top results to return (topK &gt; 0)
     * @return ranked list of FusedDocumentResult sorted descending by fused score
     * @throws IllegalArgumentException if topK &lt;= 0
     */
    List<FusedDocumentResult> fuse(Map<String, List<RankedItem>> rankedLists, Map<String, Double> sourceWeights, int topK);
}
