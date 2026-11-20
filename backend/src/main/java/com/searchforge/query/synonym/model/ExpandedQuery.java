package com.searchforge.query.synonym.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encapsulates the results of query expansion, containing both original and expanded terms.
 *
 * @param originalQuery The raw query input.
 * @param terms         List of all terms (original and synonyms) with individual weights.
 * @param isExpanded    True if at least one synonym was added to the query; false otherwise.
 */
public record ExpandedQuery(
        String originalQuery,
        List<ExpandedTerm> terms,
        boolean isExpanded
) {
    public ExpandedQuery {
        Objects.requireNonNull(originalQuery, "originalQuery cannot be null");
        terms = terms != null ? Collections.unmodifiableList(terms) : List.of();
    }

    public static ExpandedQuery unexpanded(String originalQuery, List<String> originalTerms) {
        List<ExpandedTerm> weighted = originalTerms.stream()
                .map(t -> new ExpandedTerm(t, 1.0, true))
                .toList();
        return new ExpandedQuery(originalQuery, weighted, false);
    }

    /**
     * Returns the set of all unique term strings in the expanded query.
     */
    public Set<String> getUniqueTermStrings() {
        return terms.stream().map(ExpandedTerm::term).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns a map of term string to its weight.
     */
    public java.util.Map<String, Double> getTermWeights() {
        return terms.stream().collect(Collectors.toMap(
                ExpandedTerm::term,
                ExpandedTerm::weight,
                Math::max // In case of duplicate term, take highest weight
        ));
    }
}
