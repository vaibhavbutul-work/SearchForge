package com.searchforge.query.spelling.service;

import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.model.SpellingCorrection;

import java.util.Optional;

/**
 * Service contract for spelling correction and query suggestions.
 */
public interface SpellingCorrectionService {

    /**
     * Evaluates a full query string (potentially multi-word), detects misspelled terms,
     * and recommends corrections using the indexed vocabulary.
     *
     * @param query User query string.
     * @return QuerySuggestionResponse containing original, suggested query, and corrections.
     */
    QuerySuggestionResponse suggest(String query);

    /**
     * Finds the best correction candidate for an individual word within the maximum edit distance threshold.
     *
     * @param term Individual word to correct.
     * @return Optional containing the best correction, or empty if term is already valid or no candidate within threshold.
     */
    Optional<SpellingCorrection> correctTerm(String term);

    /**
     * Returns the maximum allowable edit distance threshold (typically 2).
     */
    int getMaxEditDistance();
}
