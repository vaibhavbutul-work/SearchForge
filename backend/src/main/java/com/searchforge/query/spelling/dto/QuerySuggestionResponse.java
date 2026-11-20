package com.searchforge.query.spelling.dto;

import com.searchforge.query.spelling.model.SpellingCorrection;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * REST response payload for query spelling suggestions.
 *
 * @param originalQuery  The raw input query string.
 * @param suggestedQuery The reconstructed query with corrections applied.
 * @param hasCorrection  True if at least one term was corrected; false otherwise.
 * @param corrections    List of individual term corrections applied.
 */
public record QuerySuggestionResponse(
        String originalQuery,
        String suggestedQuery,
        boolean hasCorrection,
        List<SpellingCorrection> corrections
) {
    public QuerySuggestionResponse {
        Objects.requireNonNull(originalQuery, "originalQuery cannot be null");
        Objects.requireNonNull(suggestedQuery, "suggestedQuery cannot be null");
        corrections = corrections != null ? Collections.unmodifiableList(corrections) : List.of();
    }

    public static QuerySuggestionResponse noCorrection(String originalQuery) {
        return new QuerySuggestionResponse(originalQuery, originalQuery, false, List.of());
    }
}
