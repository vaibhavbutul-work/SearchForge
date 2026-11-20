package com.searchforge.query.spelling.model;

import java.util.Objects;

/**
 * Encapsulates an individual term spelling correction and its edit distance.
 *
 * @param original     The original misspelled term.
 * @param suggestion   The recommended correction from the index vocabulary.
 * @param editDistance The Damerau-Levenshtein edit distance between original and suggestion.
 */
public record SpellingCorrection(
        String original,
        String suggestion,
        int editDistance
) {
    public SpellingCorrection {
        Objects.requireNonNull(original, "original cannot be null");
        Objects.requireNonNull(suggestion, "suggestion cannot be null");
    }
}
