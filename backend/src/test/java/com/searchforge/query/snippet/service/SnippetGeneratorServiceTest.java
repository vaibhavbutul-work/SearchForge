package com.searchforge.query.snippet.service;

import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetGeneratorServiceTest {

    private DefaultSnippetGeneratorService snippetService;

    @BeforeEach
    void setUp() {
        snippetService = new DefaultSnippetGeneratorService(new StopWordFilter(), 120);
    }

    @Test
    @DisplayName("Should extract relevant window and wrap matching terms in <mark> tags")
    void shouldExtractRelevantWindowAndHighlight() {
        String doc = "This chapter introduces database systems. " +
                "PostgreSQL uses B-tree indexes to provide fast logarithmic lookups across table records. " +
                "Further topics include transaction isolation, multi-version concurrency control, and WAL logging.";

        HighlightedSnippet snippet = snippetService.generateSnippet(doc, "postgresql b-tree");

        assertThat(snippet.hasMatch()).isTrue();
        assertThat(snippet.matchedTerms()).contains("postgresql");
        assertThat(snippet.highlightedHtml()).contains("<mark>PostgreSQL</mark>");
        assertThat(snippet.highlightedHtml()).contains("<mark>tree</mark>");
        assertThat(snippet.rawSnippet()).contains("PostgreSQL");
    }

    @Test
    @DisplayName("Should preserve exact original word casing in highlighted tags")
    void shouldPreserveOriginalCasing() {
        String doc = "Learn how Spring Boot and Project Loom revolutionize Java Concurrency.";
        HighlightedSnippet snippet = snippetService.generateSnippet(doc, Set.of("spring", "loom", "java"), 100);

        assertThat(snippet.hasMatch()).isTrue();
        assertThat(snippet.highlightedHtml()).contains("<mark>Spring</mark>");
        assertThat(snippet.highlightedHtml()).contains("<mark>Loom</mark>");
        assertThat(snippet.highlightedHtml()).contains("<mark>Java</mark>");
    }

    @Test
    @DisplayName("Should neutralize malicious HTML injection in document while highlighting matches safely")
    void shouldNeutralizeHtmlInjection() {
        String maliciousDoc = "Documentation section. " +
                "<script>alert('pwned')</script> " +
                "PostgreSQL indexes are optimized for fast searching. " +
                "<img src=x onerror=alert(1)>";

        HighlightedSnippet snippet = snippetService.generateSnippet(maliciousDoc, Set.of("postgresql", "indexes"), 140);

        assertThat(snippet.hasMatch()).isTrue();
        // Crucial security invariant: raw script or img tags must not exist
        assertThat(snippet.highlightedHtml()).doesNotContain("<script>");
        assertThat(snippet.highlightedHtml()).doesNotContain("<img");
        // Escaped safe forms must exist
        assertThat(snippet.highlightedHtml()).contains("&lt;script&gt;");
        assertThat(snippet.highlightedHtml()).contains("<mark>PostgreSQL</mark>");
        assertThat(snippet.highlightedHtml()).contains("<mark>indexes</mark>");
    }

    @Test
    @DisplayName("Should format leading and trailing ellipsis appropriately based on window boundaries")
    void shouldFormatEllipsisCorrectly() {
        String longDoc = "A ".repeat(100) + "PostgreSQL is an advanced relational database. " + "B ".repeat(100);

        HighlightedSnippet snippet = snippetService.generateSnippet(longDoc, Set.of("postgresql", "relational"), 80);

        assertThat(snippet.hasMatch()).isTrue();
        assertThat(snippet.highlightedHtml()).startsWith("... ");
        assertThat(snippet.highlightedHtml()).endsWith(" ...");
    }

    @Test
    @DisplayName("Should not include unnecessary ellipsis for short documents")
    void shouldNotAddEllipsisForShortDocument() {
        String shortDoc = "Compact summary about PostgreSQL.";

        HighlightedSnippet snippet = snippetService.generateSnippet(shortDoc, Set.of("postgresql"), 160);

        assertThat(snippet.hasMatch()).isTrue();
        assertThat(snippet.highlightedHtml()).doesNotStartWith("... ");
        assertThat(snippet.highlightedHtml()).doesNotEndWith(" ...");
        assertThat(snippet.highlightedHtml()).isEqualTo("Compact summary about <mark>PostgreSQL</mark>.");
    }

    @Test
    @DisplayName("Should return safe non-highlighted excerpt when query matches nothing")
    void shouldHandleZeroMatches() {
        String doc = "Overview of distributed storage systems and consensus protocols.";

        HighlightedSnippet snippet = snippetService.generateSnippet(doc, Set.of("nonexistent"), 50);

        assertThat(snippet.hasMatch()).isFalse();
        assertThat(snippet.matchedTerms()).isEmpty();
        assertThat(snippet.highlightedHtml()).doesNotContain("<mark>");
        assertThat(snippet.highlightedHtml()).contains("Overview of distributed storage");
    }

    @Test
    @DisplayName("Should handle empty or blank document text gracefully")
    void shouldHandleEmptyDocument() {
        HighlightedSnippet emptySnippet = snippetService.generateSnippet("", "test");
        assertThat(emptySnippet.hasMatch()).isFalse();
        assertThat(emptySnippet.rawSnippet()).isEmpty();
        assertThat(emptySnippet.highlightedHtml()).isEmpty();

        HighlightedSnippet nullSnippet = snippetService.generateSnippet(null, "test");
        assertThat(nullSnippet.hasMatch()).isFalse();
    }
}
