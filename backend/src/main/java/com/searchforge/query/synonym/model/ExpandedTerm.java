package com.searchforge.query.synonym.model;

import java.util.Objects;

/**
 * Represents an individual query term with its associated retrieval/scoring weight.
 *
 * @param term       Normalized lexical term string.
 * @param weight     Relative relevance weight multiplier (1.0 for original terms, discounted for synonyms).
 * @param isOriginal True if this was entered by the user; false if generated via synonym expansion.
 */
public record ExpandedTerm(
        String term,
        double weight,
        boolean isOriginal
) implements Comparable<ExpandedTerm> {

    public ExpandedTerm {
        Objects.requireNonNull(term, "term cannot be null");
        if (weight <= 0.0) {
            throw new IllegalArgumentException("weight must be strictly positive, got: " + weight);
        }
    }

    /**
     * Natural ordering:
     * 1. Higher weight first (Descending).
     * 2. Original terms before expanded synonyms.
     * 3. Alphabetical tie-breaking (Ascending).
     */
    @Override
    public int compareTo(ExpandedTerm other) {
        int wComp = Double.compare(other.weight, this.weight);
        if (wComp != 0) {
            return wComp;
        }
        if (this.isOriginal != other.isOriginal) {
            return this.isOriginal ? -1 : 1;
        }
        return this.term.compareTo(other.term);
    }
}
