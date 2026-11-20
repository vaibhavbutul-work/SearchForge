package com.searchforge.query.snippet.algorithm;

import com.searchforge.query.snippet.model.SnippetWindow;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Window generation and relevance scoring algorithm for document excerpts.
 *
 * Scans document text for query term occurrences, constructs candidate text
 * windows centered around matches, snaps window boundaries to word boundaries,
 * and scores each window using term density, distinct term coverage, and proximity.
 */
public final class WindowRelevanceScorer {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private WindowRelevanceScorer() {
        // Utility class
    }

    /**
     * Internal record capturing a single term match occurrence in text.
     */
    public record TermOccurrence(int start, int end, String term) {
    }

    /**
     * Identifies all occurrences of query terms in the document text.
     *
     * @param documentText Raw document content.
     * @param queryTerms   Set of normalized/stemmed query terms.
     * @return Ordered list of term occurrences found in the document.
     */
    public static List<TermOccurrence> findOccurrences(String documentText, Set<String> queryTerms) {
        if (documentText == null || documentText.isBlank() || queryTerms == null || queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        List<TermOccurrence> occurrences = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(documentText);

        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            if (queryTerms.contains(word)) {
                occurrences.add(new TermOccurrence(matcher.start(), matcher.end(), word));
            }
        }

        return occurrences;
    }

    /**
     * Selects the most relevant snippet window of approximately targetLength characters.
     *
     * @param documentText Raw document content.
     * @param occurrences  Identified term matches in the text.
     * @param targetLength Desired snippet character length (e.g., 160).
     * @return Best scored SnippetWindow.
     */
    public static SnippetWindow selectBestWindow(String documentText,
                                                 List<TermOccurrence> occurrences,
                                                 int targetLength) {
        int docLen = documentText.length();

        // If document is shorter than target length, return the entire document
        if (docLen <= targetLength) {
            Set<String> matched = new HashSet<>();
            for (TermOccurrence occ : occurrences) {
                matched.add(occ.term());
            }
            double score = computeScore(matched.size(), occurrences.size(), docLen);
            return new SnippetWindow(0, docLen, matched.size(), occurrences.size(), score, false, false);
        }

        // If no occurrences match query terms, fallback to the document beginning
        if (occurrences.isEmpty()) {
            int end = snapToWordBoundary(documentText, targetLength, docLen);
            return new SnippetWindow(0, end, 0, 0, 0.0, false, end < docLen);
        }

        List<SnippetWindow> candidateWindows = new ArrayList<>();

        for (TermOccurrence anchor : occurrences) {
            // Center the window around the anchor match
            int approxStart = Math.max(0, anchor.start() - (targetLength / 3));
            int approxEnd = Math.min(docLen, approxStart + targetLength);

            // Snap start and end to word boundaries (spaces/punctuation)
            int windowStart = (approxStart > 0)
                    ? snapForwardToWordStart(documentText, approxStart)
                    : 0;

            int windowEnd = (approxEnd < docLen)
                    ? snapBackwardToWordEnd(documentText, Math.min(docLen, windowStart + targetLength), windowStart)
                    : docLen;

            // Ensure window covers at least the anchor match
            if (windowStart > anchor.start()) {
                windowStart = Math.max(0, snapBackwardToWordEnd(documentText, anchor.start(), 0));
            }
            if (windowEnd < anchor.end()) {
                windowEnd = Math.min(docLen, snapForwardToWordStart(documentText, anchor.end()));
            }

            // Count distinct and total matches in this window
            Set<String> uniqueMatches = new HashSet<>();
            int totalMatches = 0;
            int firstMatchStart = -1;
            int lastMatchEnd = -1;

            for (TermOccurrence occ : occurrences) {
                if (occ.start() >= windowStart && occ.end() <= windowEnd) {
                    uniqueMatches.add(occ.term());
                    totalMatches++;
                    if (firstMatchStart == -1) {
                        firstMatchStart = occ.start();
                    }
                    lastMatchEnd = occ.end();
                }
            }

            int span = (firstMatchStart != -1 && lastMatchEnd != -1) ? (lastMatchEnd - firstMatchStart) : 0;
            double score = computeScore(uniqueMatches.size(), totalMatches, span);

            boolean leadingEllipsis = windowStart > 0;
            boolean trailingEllipsis = windowEnd < docLen;

            candidateWindows.add(new SnippetWindow(
                    windowStart,
                    windowEnd,
                    uniqueMatches.size(),
                    totalMatches,
                    score,
                    leadingEllipsis,
                    trailingEllipsis
            ));
        }

        // Return candidate with the highest score
        Collections.sort(candidateWindows);
        return candidateWindows.get(0);
    }

    /**
     * Relevance scoring formula:
     * Score = 100 * uniqueMatches + 10 * totalMatches + (100 / (1 + span))
     */
    public static double computeScore(int uniqueMatches, int totalMatches, int span) {
        double proximityBonus = 100.0 / (1.0 + Math.max(0, span));
        return (100.0 * uniqueMatches) + (10.0 * totalMatches) + proximityBonus;
    }

    private static int snapForwardToWordStart(String text, int index) {
        int len = text.length();
        int pos = index;
        while (pos < len && !Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        while (pos < len && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return Math.min(pos, len);
    }

    private static int snapBackwardToWordEnd(String text, int index, int minBound) {
        int pos = Math.min(index, text.length());
        while (pos > minBound && !Character.isWhitespace(text.charAt(pos - 1))) {
            pos--;
        }
        while (pos > minBound && Character.isWhitespace(text.charAt(pos - 1))) {
            pos--;
        }
        return Math.max(pos, minBound);
    }

    private static int snapToWordBoundary(String text, int targetLength, int maxBound) {
        int pos = Math.min(targetLength, maxBound);
        while (pos < maxBound && !Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return Math.min(pos, maxBound);
    }
}
