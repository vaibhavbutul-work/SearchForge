package com.searchforge.indexer.stemmer;

/**
 * Strategy interface for linguistic stemming algorithms.
 */
public interface Stemmer {

    /**
     * Reduces a normalized word to its morphological stem or root form.
     *
     * @param word The normalized, lowercase input word.
     * @return The stemmed root term, or original word if no rule applies.
     */
    String stem(String word);
}
