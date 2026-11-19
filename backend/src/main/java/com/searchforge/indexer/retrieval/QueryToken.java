package com.searchforge.indexer.retrieval;

/**
 * Token produced by the BooleanQueryLexer.
 */
public record QueryToken(TokenType type, String value) {

    public enum TokenType {
        TERM,
        PHRASE,
        AND,
        OR,
        NOT,
        LPAREN,
        RPAREN
    }

    public static QueryToken of(TokenType type) {
        return new QueryToken(type, null);
    }

    public static QueryToken of(TokenType type, String value) {
        return new QueryToken(type, value);
    }
}
