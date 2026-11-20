package com.searchforge.search.fusion;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Result record produced by rank fusion.
 *
 * Encapsulates the fused RRF score, final 1-based rank position, and complete
 * provenance across each retrieval system for explainability.
 *
 * @param documentId       unique document identifier
 * @param rrfScore         computed Reciprocal Rank Fusion score
 * @param finalRank        1-based hybrid rank
 * @param sourceRanks      map of engine source to 1-based source rank
 * @param sourceScores     map of engine source to raw native score
 * @param rrfContributions map of engine source to reciprocal score contribution 1/(k + rank)
 */
public record FusedDocumentResult(
        long documentId,
        double rrfScore,
        int finalRank,
        Map<String, Integer> sourceRanks,
        Map<String, Double> sourceScores,
        Map<String, Double> rrfContributions
) implements Comparable<FusedDocumentResult> {

    public FusedDocumentResult {
        sourceRanks = sourceRanks != null ? Collections.unmodifiableMap(sourceRanks) : Map.of();
        sourceScores = sourceScores != null ? Collections.unmodifiableMap(sourceScores) : Map.of();
        rrfContributions = rrfContributions != null ? Collections.unmodifiableMap(rrfContributions) : Map.of();
    }

    /**
     * Deterministic natural ordering:
     * 1. Descending by RRF score.
     * 2. Ascending by document ID for identical tie-breaking.
     */
    public static final Comparator<FusedDocumentResult> COMPARATOR = Comparator
            .comparingDouble(FusedDocumentResult::rrfScore).reversed()
            .thenComparingLong(FusedDocumentResult::documentId);

    @Override
    public int compareTo(FusedDocumentResult o) {
        return COMPARATOR.compare(this, o);
    }

    public FusedDocumentResult withFinalRank(int rank) {
        return new FusedDocumentResult(
                this.documentId,
                this.rrfScore,
                rank,
                this.sourceRanks,
                this.sourceScores,
                this.rrfContributions
        );
    }
}
