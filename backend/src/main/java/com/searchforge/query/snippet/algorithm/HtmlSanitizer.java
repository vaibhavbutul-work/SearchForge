package com.searchforge.query.snippet.algorithm;

/**
 * High-performance HTML sanitizer utility.
 *
 * Escapes sensitive HTML control characters to prevent Cross-Site Scripting (XSS)
 * attacks when rendering untrusted crawled document text in search snippets.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
        // Utility class
    }

    /**
     * Escapes standard HTML entity characters (&, <, >, ", ').
     *
     * @param input Raw text extracted from documents.
     * @return Safe HTML-escaped text.
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
