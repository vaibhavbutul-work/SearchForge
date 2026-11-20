package com.searchforge.query.synonym.config;

/**
 * Modes governing how synonym relationships are traversed during query processing.
 */
public enum SynonymExpansionMode {

    /**
     * Synonym expansion is disabled; queries are processed strictly with original terms.
     */
    DISABLED,

    /**
     * Expands terms using only unidirectional mappings (e.g. "k8s" -> "kubernetes").
     */
    ONE_WAY,

    /**
     * Expands terms in both directions (e.g. "db" <-> "database", plus one-way mappings).
     */
    BIDIRECTIONAL
}
