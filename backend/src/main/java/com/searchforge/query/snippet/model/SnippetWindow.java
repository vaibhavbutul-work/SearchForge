package com.searchforge.query.snippet.model;

/**
 * Represents a scored candidate excerpt window within a document.
 *
 * @param startOffset         Start character index in document text.
 * @param endOffset           End character index in document text.
 * @param uniqueMatchesCount  Number of distinct query terms found in this window.
 * @param totalMatchesCount   Total occurrences of query terms in this window.
 * @param score               Calculated relevance score for this window.
 * @param hasLeadingEllipsis  True if excerpt starts after document beginning.
 * @param hasTrailingEllipsis True if excerpt ends before document end.
 */
public record SnippetWindow(
        int startOffset,
        int endOffset,
        int uniqueMatchesCount,
        int totalMatchesCount,
        double score,
        boolean hasLeadingEllipsis,
        boolean hasTrailingEllipsis
) implements Comparable<SnippetWindow> {

    @Override
    public int compareTo(SnippetWindow other) {
        int scoreComp = Double.compare(other.score, this.score); // DESC (higher score first)
        if (scoreComp != 0) {
            return scoreComp;
        }
        // If scores are equal, prefer earlier position in document
        return Integer.compare(this.startOffset, other.startOffset);
    }
}
