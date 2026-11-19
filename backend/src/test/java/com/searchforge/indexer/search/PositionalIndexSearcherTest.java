package com.searchforge.indexer.search;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PositionalIndexSearcherTest {

    private InMemoryInvertedIndex index;
    private Tokenizer tokenizer;
    private PositionalIndexSearcher searcher;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
        tokenizer = new Tokenizer(new StopWordFilter());
        searcher = new PositionalIndexSearcher(index, tokenizer);
    }

    @Test
    @DisplayName("Should match exact 2-word phrase and reject documents with non-adjacent occurrences")
    void shouldMatchExactPhraseAndRejectNonAdjacent() {
        // Doc 1: Exact adjacent match
        index.addDocument(1L, tokenizer.tokenize("Computer science is the study of computation"));

        // Doc 2: Only contains "computer"
        index.addDocument(2L, tokenizer.tokenize("I purchased a personal computer yesterday"));

        // Doc 3: Contains both "computer" and "science", but separated by 7 words (False Positive)
        index.addDocument(3L, tokenizer.tokenize("My computer crashed while reading an article about modern planetary science"));

        List<PhraseMatch> matches = searcher.searchPhrase("computer science");

        assertEquals(1, matches.size());
        assertEquals(1L, matches.get(0).documentId());
        assertEquals(1, matches.get(0).matchCount());

        List<Long> docIds = searcher.searchPhraseDocIds("computer science");
        assertEquals(List.of(1L), docIds);
    }

    @Test
    @DisplayName("Should match 3-word exact phrase in proper sequential order")
    void shouldMatchThreeWordPhrase() {
        // Doc 1: Exact match "fast search engine"
        index.addDocument(1L, tokenizer.tokenize("We built a fast search engine from scratch"));

        // Doc 2: Same words in scrambled order: "engine fast search"
        index.addDocument(2L, tokenizer.tokenize("This engine is fast for search queries"));

        List<PhraseMatch> matches = searcher.searchPhrase("fast search engine");

        assertEquals(1, matches.size());
        assertEquals(1L, matches.get(0).documentId());
        assertEquals(1, matches.get(0).matchCount());
    }

    @Test
    @DisplayName("Should detect multiple occurrences of a phrase within the same document")
    void shouldDetectMultipleOccurrencesInSingleDocument() {
        // Doc 1 contains "web crawler" twice
        index.addDocument(1L, tokenizer.tokenize("A web crawler is powerful. The web crawler collects links"));

        List<PhraseMatch> matches = searcher.searchPhrase("web crawler");

        assertEquals(1, matches.size());
        assertEquals(1L, matches.get(0).documentId());
        assertEquals(2, matches.get(0).matchCount());
        assertEquals(2, matches.get(0).startPositions().size());
    }

    @Test
    @DisplayName("Should evaluate proximity search within maxDistance window")
    void shouldEvaluateProximitySearch() {
        // Doc 1: "search" at pos 0, "engine" at pos 2 (distance = 2)
        index.addDocument(1L, tokenizer.tokenize("Search web engine"));

        // Doc 2: "search" and "engine" 10 words apart
        index.addDocument(2L, tokenizer.tokenize("Search for relevant documents across the entire web repository with our new high performance database engine"));

        // Proximity with maxDistance = 2: should match Doc 1 only
        List<PhraseMatch> matches = searcher.searchProximity("search", "engine", 2);
        assertEquals(1, matches.size());
        assertEquals(1L, matches.get(0).documentId());

        // Proximity with maxDistance = 15: should match both Doc 1 and Doc 2
        List<PhraseMatch> wideMatches = searcher.searchProximity("search", "engine", 15);
        assertEquals(2, wideMatches.size());
    }

    @Test
    @DisplayName("Should handle single-word query and empty inputs gracefully")
    void shouldHandleSingleWordAndEmptyQuery() {
        index.addDocument(1L, tokenizer.tokenize("SearchForge engine"));

        List<PhraseMatch> singleMatch = searcher.searchPhrase("engine");
        assertEquals(1, singleMatch.size());
        assertEquals(1L, singleMatch.get(0).documentId());

        assertTrue(searcher.searchPhrase("").isEmpty());
        assertTrue(searcher.searchPhrase(null).isEmpty());
        assertTrue(searcher.searchPhrase("nonexistent").isEmpty());
    }
}
