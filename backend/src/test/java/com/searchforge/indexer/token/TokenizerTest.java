package com.searchforge.indexer.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    private Tokenizer tokenizer;
    private StopWordFilter stopWordFilter;

    @BeforeEach
    void setUp() {
        stopWordFilter = new StopWordFilter();
        tokenizer = new Tokenizer(stopWordFilter);
    }

    @Test
    @DisplayName("Should tokenize simple sentence and assign sequential 0-based positions")
    void shouldTokenizeSimpleSentence() {
        String input = "Fast search engine";
        List<ProcessedToken> tokens = tokenizer.tokenize(input);

        assertEquals(3, tokens.size());
        assertEquals(new ProcessedToken("fast", 0), tokens.get(0));
        assertEquals(new ProcessedToken("search", 1), tokens.get(1));
        assertEquals(new ProcessedToken("engine", 2), tokens.get(2));
    }

    @Test
    @DisplayName("Should normalize casing and strip diacritics / accents")
    void shouldNormalizeCaseAndStripAccents() {
        String input = "Café Naïve Résumé";
        List<ProcessedToken> tokens = tokenizer.tokenize(input);

        assertEquals(3, tokens.size());
        assertEquals("cafe", tokens.get(0).term());
        assertEquals("naive", tokens.get(1).term());
        assertEquals("resume", tokens.get(2).term());
    }

    @Test
    @DisplayName("Should handle punctuation, hyphens, and possessive suffixes correctly")
    void shouldHandlePunctuationAndPossessives() {
        String input = "SearchForge's web-crawler, indexing 100+ pages!";
        List<ProcessedToken> tokens = tokenizer.tokenize(input);

        List<String> terms = tokens.stream().map(ProcessedToken::term).toList();
        assertEquals(List.of("searchforge", "web", "crawler", "indexing", "100", "pages"), terms);

        // Verify sequential positions
        assertEquals(0, tokens.get(0).position()); // searchforge
        assertEquals(1, tokens.get(1).position()); // web
        assertEquals(2, tokens.get(2).position()); // crawler
        assertEquals(3, tokens.get(3).position()); // indexing
        assertEquals(4, tokens.get(4).position()); // 100
        assertEquals(5, tokens.get(5).position()); // pages
    }

    @Test
    @DisplayName("Should filter stop words while advancing word positions to preserve phrase gaps")
    void shouldFilterStopWordsWhilePreservingPositions() {
        String input = "The cat on the mat";
        List<ProcessedToken> tokens = tokenizer.tokenize(input);

        // "The" (pos 0) -> dropped
        // "cat" (pos 1) -> kept
        // "on"  (pos 2) -> dropped
        // "the" (pos 3) -> dropped
        // "mat" (pos 4) -> kept
        assertEquals(2, tokens.size());
        assertEquals("cat", tokens.get(0).term());
        assertEquals(1, tokens.get(0).position());

        assertEquals("mat", tokens.get(1).term());
        assertEquals(4, tokens.get(1).position());
    }

    @Test
    @DisplayName("Should retain stop words when filterStopWords flag is false")
    void shouldRetainStopWordsWhenFilterIsFalse() {
        String input = "To be or not to be";
        List<ProcessedToken> tokens = tokenizer.tokenize(input, false);

        assertEquals(6, tokens.size());
        assertEquals("to", tokens.get(0).term());
        assertEquals(0, tokens.get(0).position());
        assertEquals("be", tokens.get(1).term());
        assertEquals(1, tokens.get(1).position());
    }

    @Test
    @DisplayName("Should return empty list for null, empty, or whitespace-only input")
    void shouldHandleEmptyOrNullInput() {
        assertTrue(tokenizer.tokenize(null).isEmpty());
        assertTrue(tokenizer.tokenize("").isEmpty());
        assertTrue(tokenizer.tokenize("   \t\n  ").isEmpty());
        assertTrue(tokenizer.tokenize("!@#$%^&*()").isEmpty());
    }

    @Test
    @DisplayName("Should validate ProcessedToken invariants")
    void shouldValidateProcessedTokenInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessedToken(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessedToken("", 0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessedToken("   ", 0));
        assertThrows(IllegalArgumentException.class, () -> new ProcessedToken("test", -1));
    }

    @Test
    @DisplayName("Should verify StopWordFilter contains expected baseline words")
    void shouldVerifyStopWordFilterBaseline() {
        assertTrue(stopWordFilter.isStopWord("the"));
        assertTrue(stopWordFilter.isStopWord("is"));
        assertTrue(stopWordFilter.isStopWord("and"));
        assertFalse(stopWordFilter.isStopWord("algorithm"));
        assertFalse(stopWordFilter.isStopWord("search"));
        assertTrue(stopWordFilter.size() > 100);
    }
}
