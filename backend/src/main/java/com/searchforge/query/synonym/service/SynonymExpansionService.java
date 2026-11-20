package com.searchforge.query.synonym.service;

import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.model.ExpandedQuery;
import com.searchforge.query.synonym.model.ExpandedTerm;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Service contract for query normalization and synonym expansion.
 */
public interface SynonymExpansionService {

    /**
     * Expands an input query string into an ExpandedQuery containing original terms
     * and discounted synonym terms per the configured expansion mode and rules.
     *
     * @param query Raw user query string.
     * @return ExpandedQuery.
     */
    ExpandedQuery expandQuery(String query);

    /**
     * Expands a collection of normalized term strings into weighted ExpandedTerm objects.
     *
     * @param terms Collection of query term strings.
     * @return List of ExpandedTerms (original terms with weight 1.0, synonyms with discounted weight).
     */
    List<ExpandedTerm> expandTerms(Collection<String> terms);

    /**
     * Retrieves direct synonyms for an individual term.
     */
    Set<String> getSynonymsForTerm(String term);

    /**
     * Returns the active expansion mode (DISABLED, ONE_WAY, BIDIRECTIONAL).
     */
    SynonymExpansionMode getMode();

    /**
     * Returns the configured weight discount multiplier for expanded synonyms.
     */
    double getSynonymDiscount();
}
