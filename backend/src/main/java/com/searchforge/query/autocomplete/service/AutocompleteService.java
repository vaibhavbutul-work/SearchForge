package com.searchforge.query.autocomplete.service;

import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;

import java.util.List;

/**
 * Service contract for prefix-based autocomplete suggestion retrieval.
 */
public interface AutocompleteService {

    /**
     * Retrieves ranked suggestions matching the given prefix string.
     *
     * @param prefix Query prefix.
     * @param limit  Maximum suggestions to return.
     * @return Ranked list of suggestions.
     */
    List<AutocompleteSuggestion> suggest(String prefix, int limit);

    /**
     * Synchronizes the in-memory Trie with the latest vocabulary from the inverted index.
     */
    void syncWithIndex();

    /**
     * Returns the total number of terms currently loaded in the autocomplete Trie.
     */
    int getVocabularySize();
}
