package com.searchforge.query.snippet.service;

import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.TextNormalizer;
import com.searchforge.query.snippet.algorithm.HtmlSanitizer;
import com.searchforge.query.snippet.algorithm.WindowRelevanceScorer;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import com.searchforge.query.snippet.model.SnippetWindow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of SnippetGeneratorService.
 *
 * Implements dynamic window relevance selection, anti-XSS HTML sanitization,
 * word-boundary preservation, and safe <mark> tag injection.
 */
@Service
public class DefaultSnippetGeneratorService implements SnippetGeneratorService {

    private static final int DEFAULT_SNIPPET_LENGTH = 160;
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final StopWordFilter stopWordFilter;
    private final int defaultSnippetLength;

    @Autowired
    public DefaultSnippetGeneratorService(StopWordFilter stopWordFilter,
                                          @Value("${search.snippet.length:160}") int defaultSnippetLength) {
        this.stopWordFilter = stopWordFilter;
        this.defaultSnippetLength = defaultSnippetLength > 0 ? defaultSnippetLength : DEFAULT_SNIPPET_LENGTH;
    }

    public DefaultSnippetGeneratorService(StopWordFilter stopWordFilter) {
        this(stopWordFilter, DEFAULT_SNIPPET_LENGTH);
    }

    public DefaultSnippetGeneratorService() {
        this(new StopWordFilter(), DEFAULT_SNIPPET_LENGTH);
    }

    @Override
    public HighlightedSnippet generateSnippet(String documentText, String query) {
        Set<String> queryTerms = extractQueryTerms(query);
        return generateSnippet(documentText, queryTerms, defaultSnippetLength);
    }

    @Override
    public HighlightedSnippet generateSnippet(String documentText, Set<String> queryTerms) {
        return generateSnippet(documentText, queryTerms, defaultSnippetLength);
    }

    @Override
    public HighlightedSnippet generateSnippet(String documentText, Set<String> queryTerms, int targetLength) {
        if (documentText == null || documentText.isBlank()) {
            return HighlightedSnippet.empty();
        }

        int length = targetLength > 0 ? targetLength : defaultSnippetLength;
        Set<String> safeTerms = normalizeQueryTerms(queryTerms);

        List<WindowRelevanceScorer.TermOccurrence> occurrences =
                WindowRelevanceScorer.findOccurrences(documentText, safeTerms);

        SnippetWindow bestWindow = WindowRelevanceScorer.selectBestWindow(documentText, occurrences, length);

        int start = bestWindow.startOffset();
        int end = bestWindow.endOffset();
        String rawWindowText = documentText.substring(start, end);

        // Identify local match spans relative to the window substring
        List<MatchSpan> localSpans = new ArrayList<>();
        Set<String> matchedTerms = new HashSet<>();

        Matcher matcher = WORD_PATTERN.matcher(rawWindowText);
        while (matcher.find()) {
            String word = matcher.group();
            String norm = word.toLowerCase(Locale.ROOT);
            if (safeTerms.contains(norm)) {
                localSpans.add(new MatchSpan(matcher.start(), matcher.end()));
                matchedTerms.add(norm);
            }
        }

        // Construct HTML-safe highlighted snippet
        StringBuilder htmlBuilder = new StringBuilder();
        if (bestWindow.hasLeadingEllipsis()) {
            htmlBuilder.append("... ");
        }

        int cursor = 0;
        for (MatchSpan span : localSpans) {
            // Append and escape text between matches
            if (span.start > cursor) {
                String nonMatchText = rawWindowText.substring(cursor, span.start);
                htmlBuilder.append(HtmlSanitizer.escapeHtml(nonMatchText));
            }

            // Append safe <mark> wrapped matching term
            String matchedWord = rawWindowText.substring(span.start, span.end);
            htmlBuilder.append("<mark>")
                    .append(HtmlSanitizer.escapeHtml(matchedWord))
                    .append("</mark>");

            cursor = span.end;
        }

        // Append remaining text after last match
        if (cursor < rawWindowText.length()) {
            String trailingText = rawWindowText.substring(cursor);
            htmlBuilder.append(HtmlSanitizer.escapeHtml(trailingText));
        }

        if (bestWindow.hasTrailingEllipsis()) {
            htmlBuilder.append(" ...");
        }

        // Plain text version (with ellipsis, without HTML tags)
        StringBuilder rawBuilder = new StringBuilder();
        if (bestWindow.hasLeadingEllipsis()) {
            rawBuilder.append("... ");
        }
        rawBuilder.append(rawWindowText.trim());
        if (bestWindow.hasTrailingEllipsis()) {
            rawBuilder.append(" ...");
        }

        boolean hasMatch = !matchedTerms.isEmpty();
        return new HighlightedSnippet(rawBuilder.toString(), htmlBuilder.toString().trim(), matchedTerms, hasMatch);
    }

    @Override
    public int getDefaultSnippetLength() {
        return defaultSnippetLength;
    }

    private Set<String> extractQueryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> terms = new HashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(query);
        while (matcher.find()) {
            String word = matcher.group();
            String normalized = TextNormalizer.normalize(word);
            if (!normalized.isEmpty() && (stopWordFilter == null || !stopWordFilter.isStopWord(normalized))) {
                terms.add(normalized.toLowerCase(Locale.ROOT));
            }
        }
        return terms;
    }

    private Set<String> normalizeQueryTerms(Set<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String term : terms) {
            if (term != null && !term.isBlank()) {
                normalized.add(term.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private record MatchSpan(int start, int end) {
    }
}
