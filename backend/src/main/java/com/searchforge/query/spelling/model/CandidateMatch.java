package com.searchforge.query.spelling.model;

import java.util.Objects;

/**
 * Internal candidate term evaluated by the Noisy Channel ranking model.
 *
 * @param term         The candidate vocabulary term.
 * @param editDistance Edit distance from the misspelled term.
 * @param score        Combined noisy channel score (error probability * language model prior).
 */
public record CandidateMatch(
        String term,
        int editDistance,
        double score
) implements Comparable<CandidateMatch> {

    public CandidateMatch {
        Objects.requireNonNull(term, "term cannot be null");
    }

    @Override
    public int compareTo(CandidateMatch other) {
        int scoreComp = Double.compare(other.score, this.score); // DESC (higher score first)
        if (scoreComp != 0) {
            return scoreComp;
        }
        return this.term.compareTo(other.term); // ASC (alphabetical tie-break)
    }
}
