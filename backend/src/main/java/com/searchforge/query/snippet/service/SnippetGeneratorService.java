package com.searchforge.query.snippet.service;

import com.searchforge.query.snippet.model.HighlightedSnippet;

import java.util.Set;

/**
 * Service contract for generating dynamic, safe, query-relevant document snippets.
 */
public interface SnippetGeneratorService {

    /**
     * Generates a highlighted snippet for a document based on a raw user query string.
     *
     * @param documentText Raw document body text.
     * @param query        Search query string.
     * @return HighlightedSnippet containing safe text with <mark> tags and match metadata.
     */
    HighlightedSnippet generateSnippet(String documentText, String query);

    /**
     * Generates a highlighted snippet for a document based on pre-normalized query terms.
     *
     * @param documentText Raw document body text.
     * @param queryTerms   Set of normalized/stemmed query terms.
     * @return HighlightedSnippet.
     */
    HighlightedSnippet generateSnippet(String documentText, Set<String> queryTerms);

    /**
     * Generates a highlighted snippet with a custom target character length.
     *
     * @param documentText Raw document body text.
     * @param queryTerms   Set of normalized query terms.
     * @param targetLength Desired excerpt character length.
     * @return HighlightedSnippet.
     */
    HighlightedSnippet generateSnippet(String documentText, Set<String> queryTerms, int targetLength);

    /**
     * Returns the configured default snippet length (typically ~160 characters).
     */
    int getDefaultSnippetLength();
}
