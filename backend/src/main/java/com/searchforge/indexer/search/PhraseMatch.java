package com.searchforge.indexer.search;

import java.util.List;

/**
 * Result representing a document that matches an exact phrase query or proximity search.
 *
 * @param documentId     Identifier of the matching document.
 * @param matchCount     Number of times the phrase occurs in this document.
 * @param startPositions List of 0-based word positions where the phrase begins.
 */
public record PhraseMatch(
        long documentId,
        int matchCount,
        List<Integer> startPositions
) implements Comparable<PhraseMatch> {

    public PhraseMatch {
        startPositions = (startPositions != null) ? List.copyOf(startPositions) : List.of();
    }

    public static PhraseMatch of(long documentId, List<Integer> startPositions) {
        int count = (startPositions != null) ? startPositions.size() : 0;
        return new PhraseMatch(documentId, count, startPositions);
    }

    @Override
    public int compareTo(PhraseMatch other) {
        // Sort by matchCount descending (higher frequency first), then documentId ascending
        int countCmp = Integer.compare(other.matchCount, this.matchCount);
        if (countCmp != 0) {
            return countCmp;
        }
        return Long.compare(this.documentId, other.documentId);
    }
}
