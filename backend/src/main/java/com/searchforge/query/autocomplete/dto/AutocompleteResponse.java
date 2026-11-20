package com.searchforge.query.autocomplete.dto;

import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;

import java.util.List;
import java.util.Objects;

/**
 * REST response payload for the autocomplete endpoint.
 *
 * @param prefix      The entered query prefix.
 * @param suggestions Ranked list of autocomplete suggestions.
 * @param count       Total number of suggestions returned.
 */
public record AutocompleteResponse(
        String prefix,
        List<AutocompleteSuggestion> suggestions,
        int count
) {
    public AutocompleteResponse(String prefix, List<AutocompleteSuggestion> suggestions) {
        this(prefix, Objects.requireNonNull(suggestions, "suggestions cannot be null"), suggestions.size());
    }
}
