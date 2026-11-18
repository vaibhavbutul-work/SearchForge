package com.searchforge.indexer.token;

/**
 * Immutable token unit produced by the text processing pipeline.
 *
 * @param term     The normalized, lowercase lexical term.
 * @param position The 0-based sequential word position in the original document text.
 */
public record ProcessedToken(
        String term,
        int position
) {
    public ProcessedToken {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Token term cannot be null or blank");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Token position cannot be negative");
        }
    }
}
