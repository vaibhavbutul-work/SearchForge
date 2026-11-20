package com.searchforge.search.fusion;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for fusing rankings from multiple independent retrieval engines.
 */
public interface RankFusionEngine {

    /**
     * Combines multiple ranked candidate lists into a unified hybrid ranking.
     *
     * @param rankedLists map of source names to ranked candidate lists
     * @param topK maximum number of top results to return (topK &gt; 0)
     * @return ranked list of FusedDocumentResult sorted descending by fused score
     * @throws IllegalArgumentException if topK &lt;= 0
     */
    List<FusedDocumentResult> fuse(Map<String, List<RankedItem>> rankedLists, int topK);
}
