package com.searchforge.indexer.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lexical analyzer for Boolean retrieval queries.
 *
 * Tokenizes raw query strings into structured QueryTokens (terms, quoted phrases,
 * boolean operators AND/OR/NOT, parentheses, and '-' negation prefix).
 */
public class BooleanQueryLexer {

    public List<QueryToken> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<QueryToken> tokens = new ArrayList<>();
        char[] chars = query.toCharArray();
        int n = chars.length;
        int i = 0;

        while (i < n) {
            char c = chars[i];

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '(') {
                tokens.add(QueryToken.of(QueryToken.TokenType.LPAREN));
                i++;
                continue;
            }

            if (c == ')') {
                tokens.add(QueryToken.of(QueryToken.TokenType.RPAREN));
                i++;
                continue;
            }

            if (c == '"') {
                // Quoted phrase: read until closing quote or end of input
                i++; // skip opening quote
                int start = i;
                while (i < n && chars[i] != '"') {
                    i++;
                }
                String phrase = new String(chars, start, i - start).trim();
                if (i < n && chars[i] == '"') {
                    i++; // skip closing quote
                }
                if (!phrase.isEmpty()) {
                    tokens.add(QueryToken.of(QueryToken.TokenType.PHRASE, phrase));
                }
                continue;
            }

            // Minus negation prefix e.g. -python or -"web server"
            if (c == '-' && i + 1 < n && !Character.isWhitespace(chars[i + 1])) {
                tokens.add(QueryToken.of(QueryToken.TokenType.NOT));
                i++;
                continue;
            }

            // Word / Term / Operator
            int start = i;
            while (i < n && !Character.isWhitespace(chars[i]) && chars[i] != '(' && chars[i] != ')' && chars[i] != '"') {
                i++;
            }
            String word = new String(chars, start, i - start);

            if (word.equalsIgnoreCase("AND")) {
                tokens.add(QueryToken.of(QueryToken.TokenType.AND));
            } else if (word.equalsIgnoreCase("OR")) {
                tokens.add(QueryToken.of(QueryToken.TokenType.OR));
            } else if (word.equalsIgnoreCase("NOT")) {
                tokens.add(QueryToken.of(QueryToken.TokenType.NOT));
            } else {
                tokens.add(QueryToken.of(QueryToken.TokenType.TERM, word));
            }
        }

        return Collections.unmodifiableList(tokens);
    }
}
