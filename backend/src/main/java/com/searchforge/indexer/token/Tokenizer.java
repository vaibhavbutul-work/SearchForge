package com.searchforge.indexer.token;

import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.stemmer.Stemmer;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 0-based word positions, filters English stop words, and applies morphological
 * stemming (via PorterStemmer).
 */
@Component
public class Tokenizer {

    /**
     * Regex matching unicode letter/digit sequences, optionally including internal apostrophes/contractions.
     * Examples: "SearchForge", "SearchForge's", "100", "café".
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)?");

    private final StopWordFilter stopWordFilter;
    private final Stemmer stemmer;

    @Autowired
    public Tokenizer(StopWordFilter stopWordFilter, Stemmer stemmer) {
        this.stopWordFilter = stopWordFilter;
        this.stemmer = (stemmer != null) ? stemmer : new PorterStemmer();
    }

    public Tokenizer(StopWordFilter stopWordFilter) {
        this(stopWordFilter, new PorterStemmer());
    }

    /**
     * Tokenizes input text into a list of normalized and stemmed ProcessedToken objects,
     * tracking 0-based word positions and filtering out stop words.
     *
     * @param text Raw unstructured document text.
     * @return List of processed tokens with stemmed terms and positions.
     */
    public List<ProcessedToken> tokenize(String text) {
        return tokenize(text, true, true);
    }

    /**
     * Tokenizes input text without applying stemming (normalizing and filtering stop words only).
     *
     * @param text Raw document text.
     * @return List of normalized tokens without stemming.
     */
    public List<ProcessedToken> tokenizeUnstemmed(String text) {
        return tokenize(text, true, false);
    }

    /**
     * Tokenizes input text with an option to enable or disable stop-word filtering.
     *
     * @param text            Raw document text.
     * @param filterStopWords If true, stop words are omitted from the output token list.
     * @return List of processed tokens.
     */
    public List<ProcessedToken> tokenize(String text, boolean filterStopWords) {
        return tokenize(text, filterStopWords, true);
    }

    /**
     * Tokenizes input text with options to configure stop-word filtering and stemming.
     *
     * @param text            Raw document text.
     * @param filterStopWords If true, stop words are omitted from output.
     * @param applyStemming   If true, terms are reduced to morphological roots via stemmer.
     * @return List of processed tokens.
     */
    public List<ProcessedToken> tokenize(String text, boolean filterStopWords, boolean applyStemming) {
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

            String finalTerm = applyStemming
                    ? stemmer.stem(normalizedTerm)
                    : normalizedTerm;

            if (!finalTerm.isEmpty()) {
                tokens.add(new ProcessedToken(finalTerm, currentPosition));
            }
        }

        return tokens;
    }
}
