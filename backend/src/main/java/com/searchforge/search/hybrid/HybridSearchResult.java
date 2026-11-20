package com.searchforge.search.hybrid;

import java.util.List;

/**
 * Encapsulates the outcome of a hybrid or multi-mode search operation.
 *
 * @param query          input query text
 * @param requestedMode  search mode requested by the caller
 * @param effectiveMode  search mode actually executed (may differ if fallback occurred)
 * @param fallback       true if fallback was triggered due to component failure
 * @param fallbackReason human-readable reason for fallback (null if no fallback)
 * @param candidates     ranked candidates ordered by relevance
 */
public record HybridSearchResult(
        String query,
        SearchMode requestedMode,
        SearchMode effectiveMode,
        boolean fallback,
        String fallbackReason,
        List<HybridCandidate> candidates
) {
    public HybridSearchResult {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }

    public static HybridSearchResult of(String query, SearchMode mode, List<HybridCandidate> candidates) {
        return new HybridSearchResult(query, mode, mode, false, null, candidates);
    }

    public static HybridSearchResult fallback(String query, SearchMode requestedMode, SearchMode effectiveMode,
                                              String reason, List<HybridCandidate> candidates) {
        return new HybridSearchResult(query, requestedMode, effectiveMode, true, reason, candidates);
    }
}
