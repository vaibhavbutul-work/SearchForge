package com.searchforge.search.hybrid;

import java.util.Locale;

/**
 * Enumeration of supported search execution modes in SearchForge.
 *
 * <ul>
 *   <li><b>LEXICAL:</b> Traditional BM25 inverted index retrieval with exact phrase and Boolean capabilities.</li>
 *   <li><b>SEMANTIC:</b> Dense neural vector retrieval based on continuous cosine similarity.</li>
 *   <li><b>HYBRID:</b> Multi-modal retrieval fusing lexical BM25 and dense vectors via Reciprocal Rank Fusion (RRF).</li>
 * </ul>
 */
public enum SearchMode {
    LEXICAL,
    SEMANTIC,
    HYBRID;

    /**
     * Resolves a case-insensitive mode string to its corresponding {@link SearchMode}.
     *
     * @param modeStr raw input string (e.g. "lexical", "semantic", "hybrid", "ranked", "bm25")
     * @return resolved {@link SearchMode}
     * @throws IllegalArgumentException if the mode string is unparseable or unsupported
     */
    public static SearchMode fromString(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) {
            return LEXICAL;
        }
        String normalized = modeStr.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "lexical", "ranked", "bm25" -> LEXICAL;
            case "semantic", "dense", "vector" -> SEMANTIC;
            case "hybrid", "rrf", "combined" -> HYBRID;
            default -> throw new IllegalArgumentException(
                    "Invalid search mode: '" + modeStr + "'. Supported modes are: lexical, semantic, hybrid"
            );
        };
    }
}
