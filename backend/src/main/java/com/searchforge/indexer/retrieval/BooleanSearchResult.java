package com.searchforge.indexer.retrieval;

import java.util.List;

/**
 * Result record for a Boolean retrieval search query.
 */
public record BooleanSearchResult(
        List<Long> documentIds,
        int totalHits,
        long durationMs
) {
    public static BooleanSearchResult empty(long durationMs) {
        return new BooleanSearchResult(List.of(), 0, durationMs);
    }
}
