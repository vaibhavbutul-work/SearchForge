package com.searchforge.query.snippet.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates the generated snippet excerpt and its safe highlighted HTML representation.
 *
 * @param rawSnippet      Plain-text excerpt (without HTML tags).
 * @param highlightedHtml XSS-safe HTML string with matching terms wrapped in <mark> tags.
 * @param matchedTerms    Set of unique normalized query terms matched in the snippet.
 * @param hasMatch        True if the snippet contains at least one highlighted query match.
 */
public record HighlightedSnippet(
        String rawSnippet,
        String highlightedHtml,
        Set<String> matchedTerms,
        boolean hasMatch
) {
    public HighlightedSnippet {
        Objects.requireNonNull(rawSnippet, "rawSnippet cannot be null");
        Objects.requireNonNull(highlightedHtml, "highlightedHtml cannot be null");
        matchedTerms = matchedTerms != null ? Collections.unmodifiableSet(matchedTerms) : Set.of();
    }

    public HighlightedSnippet(String rawSnippet, String highlightedHtml) {
        this(rawSnippet, highlightedHtml, Set.of(), !rawSnippet.isEmpty());
    }

    public HighlightedSnippet(String rawSnippet, String highlightedHtml, Set<String> matchedTerms) {
        this(rawSnippet, highlightedHtml, matchedTerms, matchedTerms != null && !matchedTerms.isEmpty());
    }

    public static HighlightedSnippet empty() {
        return new HighlightedSnippet("", "", Set.of(), false);
    }
}
