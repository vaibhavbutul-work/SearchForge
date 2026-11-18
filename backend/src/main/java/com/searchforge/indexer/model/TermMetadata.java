package com.searchforge.indexer.model;

/**
 * Global dictionary statistics for an indexed term.
 *
 * @param term                The normalized/stemmed lexical term string.
 * @param documentFrequency   Number of distinct documents containing this term (df).
 * @param totalTermFrequency  Total occurrences of this term across the entire corpus.
 */
public record TermMetadata(
        String term,
        int documentFrequency,
        long totalTermFrequency
) {
}
