package com.searchforge.ranking.weight;

/**
 * Strategy interface for computing Inverse Document Frequency (IDF) weights across a corpus.
 */
@FunctionalInterface
public interface IdfCalculator {

    /**
     * Calculates the IDF weight of a term given its document frequency and corpus size.
     *
     * @param documentFrequency Number of documents in the collection containing the term (df >= 0).
     * @param totalDocuments    Total number of documents in the collection (N >= 0).
     * @return Computed non-negative IDF score.
     */
    double calculateIdf(long documentFrequency, long totalDocuments);
}
