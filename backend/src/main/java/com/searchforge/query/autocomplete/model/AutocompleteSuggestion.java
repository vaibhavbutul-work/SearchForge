package com.searchforge.query.autocomplete.model;

import java.util.Objects;

/**
 * Represents an autocomplete suggestion derived from indexed vocabulary metadata.
 *
 * @param term  The indexed vocabulary term.
 * @param ctf   Collection Term Frequency (total occurrences across all documents).
 * @param df    Document Frequency (distinct documents containing this term).
 * @param score Calculated suggestion ranking score.
 */
public record AutocompleteSuggestion(
        String term,
        long ctf,
        int df,
        double score
) implements Comparable<AutocompleteSuggestion> {

    public AutocompleteSuggestion {
        Objects.requireNonNull(term, "term cannot be null");
    }

    /**
     * Deterministic natural ordering:
     * 1. Higher score first (Descending).
     * 2. If scores are equal, alphabetical by term (Ascending) for stable tie-breaking.
     */
    @Override
    public int compareTo(AutocompleteSuggestion other) {
        int scoreComp = Double.compare(other.score, this.score);
        if (scoreComp != 0) {
            return scoreComp;
        }
        return this.term.compareTo(other.term);
    }
}
