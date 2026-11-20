package com.searchforge.query.synonym.dictionary;

import com.searchforge.query.synonym.config.SynonymExpansionMode;

import java.util.Collection;
import java.util.Set;

/**
 * Contract for managing unidirectional and bidirectional synonym mappings.
 */
public interface SynonymDictionary {

    /**
     * Registers a one-way mapping: term -> synonym.
     */
    void addOneWay(String from, String to);

    /**
     * Registers a one-way mapping: term -> multiple synonyms.
     */
    void addOneWay(String from, Collection<String> synonyms);

    /**
     * Registers a bidirectional mapping: term1 <-> term2.
     */
    void addBidirectional(String term1, String term2);

    /**
     * Registers a bidirectional mapping between term and a collection of synonyms.
     */
    void addBidirectional(String term, Collection<String> synonyms);

    /**
     * Retrieves all configured synonyms for a term given the specified expansion mode.
     *
     * @param term Normalized term string.
     * @param mode Expansion mode (DISABLED, ONE_WAY, BIDIRECTIONAL).
     * @return Set of synonym term strings.
     */
    Set<String> getSynonyms(String term, SynonymExpansionMode mode);

    /**
     * Checks if any synonyms exist for the given term in the specified mode.
     */
    boolean hasSynonyms(String term, SynonymExpansionMode mode);

    /**
     * Returns total distinct head terms registered in the dictionary.
     */
    int size();

    /**
     * Clears all registered synonym mappings.
     */
    void clear();
}
