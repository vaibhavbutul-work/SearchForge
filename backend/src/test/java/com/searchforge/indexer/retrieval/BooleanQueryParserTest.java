package com.searchforge.indexer.retrieval;

import com.searchforge.indexer.retrieval.ast.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BooleanQueryParserTest {

    private BooleanQueryParser parser;

    @BeforeEach
    void setUp() {
        parser = new BooleanQueryParser();
    }

    @Test
    @DisplayName("Should parse single term into TermNode")
    void shouldParseSingleTerm() {
        Optional<QueryNode> result = parser.parse("java");
        assertTrue(result.isPresent());
        assertInstanceOf(TermNode.class, result.get());
        assertEquals("java", ((TermNode) result.get()).term());
    }

    @Test
    @DisplayName("Should parse quoted phrase into PhraseNode")
    void shouldParseQuotedPhrase() {
        Optional<QueryNode> result = parser.parse("\"search engine\"");
        assertTrue(result.isPresent());
        assertInstanceOf(PhraseNode.class, result.get());
        assertEquals("search engine", ((PhraseNode) result.get()).phrase());
    }

    @Test
    @DisplayName("Should parse explicit binary operators AND and OR")
    void shouldParseExplicitAndOr() {
        Optional<QueryNode> andResult = parser.parse("java AND spring");
        assertTrue(andResult.isPresent());
        assertInstanceOf(AndNode.class, andResult.get());
        AndNode andNode = (AndNode) andResult.get();
        assertEquals("java", ((TermNode) andNode.left()).term());
        assertEquals("spring", ((TermNode) andNode.right()).term());

        Optional<QueryNode> orResult = parser.parse("java OR python");
        assertTrue(orResult.isPresent());
        assertInstanceOf(OrNode.class, orResult.get());
        OrNode orNode = (OrNode) orResult.get();
        assertEquals("java", ((TermNode) orNode.left()).term());
        assertEquals("python", ((TermNode) orNode.right()).term());
    }

    @Test
    @DisplayName("Should parse infix NOT and AND NOT into NotNode")
    void shouldParseNotExpressions() {
        Optional<QueryNode> notResult = parser.parse("java NOT python");
        assertTrue(notResult.isPresent());
        assertInstanceOf(NotNode.class, notResult.get());
        NotNode notNode = (NotNode) notResult.get();
        assertEquals("java", ((TermNode) notNode.positive()).term());
        assertEquals("python", ((TermNode) notNode.negative()).term());

        Optional<QueryNode> andNotResult = parser.parse("java AND NOT python");
        assertTrue(andNotResult.isPresent());
        assertInstanceOf(NotNode.class, andNotResult.get());
    }

    @Test
    @DisplayName("Should respect operator precedence: AND binds tighter than OR")
    void shouldRespectOperatorPrecedence() {
        // "java OR python AND rust" -> java OR (python AND rust)
        Optional<QueryNode> result = parser.parse("java OR python AND rust");
        assertTrue(result.isPresent());
        assertInstanceOf(OrNode.class, result.get());

        OrNode orNode = (OrNode) result.get();
        assertEquals("java", ((TermNode) orNode.left()).term());
        assertInstanceOf(AndNode.class, orNode.right());
        AndNode andNode = (AndNode) orNode.right();
        assertEquals("python", ((TermNode) andNode.left()).term());
        assertEquals("rust", ((TermNode) andNode.right()).term());
    }

    @Test
    @DisplayName("Should override precedence when parentheses are used")
    void shouldOverridePrecedenceWithParentheses() {
        // "(java OR python) AND rust" -> (java OR python) AND rust
        Optional<QueryNode> result = parser.parse("(java OR python) AND rust");
        assertTrue(result.isPresent());
        assertInstanceOf(AndNode.class, result.get());

        AndNode andNode = (AndNode) result.get();
        assertInstanceOf(OrNode.class, andNode.left());
        assertEquals("rust", ((TermNode) andNode.right()).term());
    }

    @Test
    @DisplayName("Should parse implicit AND between adjacent words")
    void shouldParseImplicitAnd() {
        // "java spring boot" -> (java AND spring) AND boot
        Optional<QueryNode> result = parser.parse("java spring boot");
        assertTrue(result.isPresent());
        assertInstanceOf(AndNode.class, result.get());

        AndNode rootAnd = (AndNode) result.get();
        assertEquals("boot", ((TermNode) rootAnd.right()).term());
        assertInstanceOf(AndNode.class, rootAnd.left());
    }

    @Test
    @DisplayName("Should parse minus prefix as negation")
    void shouldParseMinusPrefixAsNegation() {
        // "java -python" -> java NOT python
        Optional<QueryNode> result = parser.parse("java -python");
        assertTrue(result.isPresent());
        assertInstanceOf(NotNode.class, result.get());
        NotNode notNode = (NotNode) result.get();
        assertEquals("java", ((TermNode) notNode.positive()).term());
        assertEquals("python", ((TermNode) notNode.negative()).term());
    }

    @Test
    @DisplayName("Should parse pure standalone negation")
    void shouldParsePureStandaloneNegation() {
        Optional<QueryNode> result = parser.parse("NOT legacy");
        assertTrue(result.isPresent());
        assertInstanceOf(NotNode.class, result.get());
        NotNode notNode = (NotNode) result.get();
        assertNull(notNode.positive());
        assertEquals("legacy", ((TermNode) notNode.negative()).term());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on unclosed parenthesis")
    void shouldThrowOnUnclosedParenthesis() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("(java OR python"));
    }

    @Test
    @DisplayName("Should return empty Optional for blank queries")
    void shouldReturnEmptyForBlankQuery() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }
}
