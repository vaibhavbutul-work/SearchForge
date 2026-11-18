package com.searchforge.indexer.token;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility for linguistic normalization, case folding, and diacritic stripping.
 */
public final class TextNormalizer {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}");
    private static final Pattern POSSESSIVE_PATTERN = Pattern.compile("['’]s$");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]");

    private TextNormalizer() {
        // Private constructor for static utility class
    }

    /**
     * Normalizes a raw word token into a clean, lowercased, accent-free alphanumeric term.
     *
     * @param token The raw token string.
     * @return The normalized term, or empty string if input contains no alphanumeric characters.
     */
    public static String normalize(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }

        // 1. Unicode NFD decomposition (separates characters from their accents)
        String decomposed = Normalizer.normalize(token.trim(), Normalizer.Form.NFD);

        // 2. Strip diacritical marks (e.g. é -> e, ü -> u, ñ -> n)
        String accentFree = DIACRITICS_PATTERN.matcher(decomposed).replaceAll("");

        // 3. Strip possessive suffixes (e.g., "SearchForge's" -> "SearchForge")
        String possessiveFree = POSSESSIVE_PATTERN.matcher(accentFree).replaceAll("");

        // 4. Case folding to lowercase using root locale
        String lowercased = possessiveFree.toLowerCase(Locale.ROOT);

        // 5. Strip any non-alphanumeric noise remaining
        return NON_ALPHANUMERIC_PATTERN.matcher(lowercased).replaceAll("");
    }
}
