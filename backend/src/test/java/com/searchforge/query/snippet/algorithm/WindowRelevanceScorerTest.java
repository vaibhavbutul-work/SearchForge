package com.searchforge.query.snippet.algorithm;

import com.searchforge.query.snippet.model.SnippetWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WindowRelevanceScorerTest {

    @Test
    @DisplayName("Should find all occurrences of query terms in text")
    void shouldFindOccurrences() {
        String doc = "PostgreSQL database indexes and PostgreSQL tables.";
        Set<String> queryTerms = Set.of("postgresql", "indexes");

        List<WindowRelevanceScorer.TermOccurrence> occurrences =
                WindowRelevanceScorer.findOccurrences(doc, queryTerms);

        assertThat(occurrences).hasSize(3);
        assertThat(occurrences.get(0).term()).isEqualTo("postgresql");
        assertThat(occurrences.get(1).term()).isEqualTo("indexes");
        assertThat(occurrences.get(2).term()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("Should return entire document when text is shorter than target length")
    void shouldReturnEntireDocumentForShortText() {
        String shortDoc = "Spring Boot microservice.";
        Set<String> terms = Set.of("spring", "boot");

        List<WindowRelevanceScorer.TermOccurrence> occurrences =
                WindowRelevanceScorer.findOccurrences(shortDoc, terms);

        SnippetWindow window = WindowRelevanceScorer.selectBestWindow(shortDoc, occurrences, 160);

        assertThat(window.startOffset()).isEqualTo(0);
        assertThat(window.endOffset()).isEqualTo(shortDoc.length());
        assertThat(window.hasLeadingEllipsis()).isFalse();
        assertThat(window.hasTrailingEllipsis()).isFalse();
        assertThat(window.uniqueMatchesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should fallback to beginning of document when no query matches exist")
    void shouldFallbackWhenNoMatches() {
        String doc = "This is introductory documentation about web development and cloud deployments.";
        List<WindowRelevanceScorer.TermOccurrence> occurrences = List.of();

        SnippetWindow window = WindowRelevanceScorer.selectBestWindow(doc, occurrences, 30);

        assertThat(window.startOffset()).isEqualTo(0);
        assertThat(window.hasLeadingEllipsis()).isFalse();
        assertThat(window.hasTrailingEllipsis()).isTrue();
        assertThat(window.score()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should select window with higher distinct query term coverage over duplicate term window")
    void shouldFavorDistinctTermCoverage() {
        // Window A has 3 occurrences of "index"
        // Window B has "index", "btree", and "postgresql"
        String doc = "Prefix preamble text. " +
                "Here is index index index repeated. " +
                "Middle text filling space between sections in documentation. " +
                "Here is postgresql btree index together in a dense sentence. " +
                "Trailing outro text.";

        Set<String> queryTerms = Set.of("postgresql", "btree", "index");
        List<WindowRelevanceScorer.TermOccurrence> occurrences =
                WindowRelevanceScorer.findOccurrences(doc, queryTerms);

        SnippetWindow bestWindow = WindowRelevanceScorer.selectBestWindow(doc, occurrences, 70);

        // Best window should cover the dense multi-term window
        assertThat(bestWindow.uniqueMatchesCount()).isEqualTo(3);
        String windowText = doc.substring(bestWindow.startOffset(), bestWindow.endOffset());
        assertThat(windowText).contains("postgresql").contains("btree").contains("index");
    }
}
