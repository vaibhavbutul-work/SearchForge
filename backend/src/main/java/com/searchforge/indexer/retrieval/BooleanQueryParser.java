package com.searchforge.indexer.retrieval;

import com.searchforge.indexer.retrieval.ast.*;

import java.util.List;
import java.util.Optional;

/**
 * Recursive-descent parser for Boolean queries.
 *
 * Supports operator precedence (Parentheses > NOT > AND > OR), implicit AND
 * between adjacent terms/phrases, exact quoted phrases, and explicit negations.
 */
public class BooleanQueryParser {

    private final BooleanQueryLexer lexer;

    public BooleanQueryParser() {
        this.lexer = new BooleanQueryLexer();
    }

    public BooleanQueryParser(BooleanQueryLexer lexer) {
        this.lexer = lexer != null ? lexer : new BooleanQueryLexer();
    }

    /**
     * Parses a query string into a QueryNode AST root.
     *
     * @param query Raw query string.
     * @return Optional containing the root QueryNode, or empty if query is blank.
     */
    public Optional<QueryNode> parse(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        List<QueryToken> tokens = lexer.tokenize(query);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        ParserState state = new ParserState(tokens);
        QueryNode root = parseOr(state);

        if (state.hasNext()) {
            throw new IllegalArgumentException("Unexpected token after valid expression: " + state.peek());
        }

        return Optional.of(root);
    }

    private QueryNode parseOr(ParserState state) {
        QueryNode left = parseAnd(state);

        while (state.match(QueryToken.TokenType.OR)) {
            QueryNode right = parseAnd(state);
            left = new OrNode(left, right);
        }
        return left;
    }

    private QueryNode parseAnd(ParserState state) {
        QueryNode left = parseNot(state);

        while (state.hasNext() && !state.check(QueryToken.TokenType.OR) && !state.check(QueryToken.TokenType.RPAREN)) {
            if (state.match(QueryToken.TokenType.AND)) {
                if (state.match(QueryToken.TokenType.NOT)) {
                    QueryNode right = parsePrimary(state);
                    left = new NotNode(left, right);
                } else {
                    QueryNode right = parseNot(state);
                    left = new AndNode(left, right);
                }
            } else if (state.match(QueryToken.TokenType.NOT)) {
                // Infix "A NOT B" -> difference
                QueryNode right = parsePrimary(state);
                left = new NotNode(left, right);
            } else {
                // Implicit AND between adjacent terms/phrases/expressions
                QueryNode right = parseNot(state);
                left = new AndNode(left, right);
            }
        }
        return left;
    }

    private QueryNode parseNot(ParserState state) {
        if (state.match(QueryToken.TokenType.NOT)) {
            QueryNode operand = parsePrimary(state);
            return new NotNode(null, operand);
        }
        return parsePrimary(state);
    }

    private QueryNode parsePrimary(ParserState state) {
        if (!state.hasNext()) {
            throw new IllegalArgumentException("Unexpected end of query: expected term, phrase, or '('");
        }

        if (state.match(QueryToken.TokenType.LPAREN)) {
            QueryNode expr = parseOr(state);
            state.consume(QueryToken.TokenType.RPAREN, "Expected ')' to close group");
            return expr;
        }

        if (state.check(QueryToken.TokenType.PHRASE)) {
            QueryToken token = state.advance();
            return new PhraseNode(token.value());
        }

        if (state.check(QueryToken.TokenType.TERM)) {
            QueryToken token = state.advance();
            return new TermNode(token.value());
        }

        throw new IllegalArgumentException("Unexpected token in primary expression: " + state.peek());
    }

    private static class ParserState {
        private final List<QueryToken> tokens;
        private int current = 0;

        ParserState(List<QueryToken> tokens) {
            this.tokens = tokens;
        }

        boolean hasNext() {
            return current < tokens.size();
        }

        QueryToken peek() {
            if (!hasNext()) return null;
            return tokens.get(current);
        }

        QueryToken advance() {
            if (!hasNext()) return null;
            return tokens.get(current++);
        }

        boolean check(QueryToken.TokenType type) {
            if (!hasNext()) return false;
            return peek().type() == type;
        }

        boolean match(QueryToken.TokenType type) {
            if (check(type)) {
                advance();
                return true;
            }
            return false;
        }

        void consume(QueryToken.TokenType type, String errorMessage) {
            if (!match(type)) {
                throw new IllegalArgumentException(errorMessage + " but found: " + (hasNext() ? peek() : "end of query"));
            }
        }
    }
}
