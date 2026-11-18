package com.searchforge.indexer.token;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linguistic Tokenizer for the SearchForge indexing engine.
 *
 * Scans text for alphanumeric word sequences, applies text normalization
 * (diacritic stripping, case folding, possessive removal), tracks sequential
 * 0-based word positions, and filters English stop words.
 */
@Component
public class Tokenizer {

    /**
     * Regex matching unicode letter/digit sequences, optionally including internal apostrophes/contractions.
     * Examples: "SearchForge", "SearchForge's", "100", "café".
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?");

    private final StopWordFilter stopWordFilter;

    public Tokenizer(StopWordFilter stopWordFilter) {
        this.stopWordFilter = stopWordFilter;
    }

    /**
     * Tokenizes input text into a list of normalized ProcessedToken objects,
     * tracking 0-based word positions and filtering out stop words.
     *
     * @param text Raw unstructured document text.
     * @return List of processed tokens with terms and positions.
     */
    public List<ProcessedToken> tokenize(String text) {
        return tokenize(text, true);
    }

    /**
     * Tokenizes input text with an option to enable or disable stop-word filtering.
     *
     * @param text            Raw document text.
     * @param filterStopWords If true, stop words are omitted from the output token list.
     * @return List of processed tokens.
     */
    public List<ProcessedToken> tokenize(String text, boolean filterStopWords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ProcessedToken> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        int wordPosition = 0;

        while (matcher.find()) {
            String rawMatch = matcher.group();
            String normalizedTerm = TextNormalizer.normalize(rawMatch);
            int currentPosition = wordPosition++;

            if (normalizedTerm.isEmpty()) {
                continue;
            }

            if (filterStopWords && stopWordFilter.isStopWord(normalizedTerm)) {
                // Word position is preserved (advanced), but the stop word is not emitted to the index
                continue;
            }

            tokens.add(new ProcessedToken(normalizedTerm, currentPosition));
        }

        return tokens;
    }
}
