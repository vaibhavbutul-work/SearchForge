package com.searchforge.ranking.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import com.searchforge.query.snippet.service.SnippetGeneratorService;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.model.SpellingCorrection;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import com.searchforge.query.synonym.model.ExpandedQuery;
import com.searchforge.query.synonym.model.ExpandedTerm;
import com.searchforge.query.synonym.service.SynonymExpansionService;
import com.searchforge.ranking.dto.RankedSearchHitResponse;
import com.searchforge.ranking.dto.RankedSearchResponse;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.retrieval.RankedSearchResult;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.IdfCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end REST integration tests for Phase 5 Task 5: Search Intelligence Integration.
 * Validates search response enrichment, didYouMean spelling suggestions, synonym expansion,
 * dynamic XSS-safe highlighted snippets, Boolean/filter preservation, and backwards compatibility.
 */
@WebMvcTest(SearchController.class)
class SearchIntelligenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankedRetrievalEngine rankedRetrievalEngine;

    @MockitoBean
    private RankedBooleanQueryEngine rankedBooleanQueryEngine;

    @MockitoBean
    private CrawledDocumentRepository crawledDocumentRepository;

    @MockitoBean
    private InvertedIndex invertedIndex;

    @MockitoBean(name = "bm25IdfCalculator")
    private IdfCalculator idfCalculator;

    @MockitoBean
    private Bm25TermScorer termScorer;

    @MockitoBean
    private SpellingCorrectionService spellingCorrectionService;

    @MockitoBean
    private SynonymExpansionService synonymExpansionService;

    @MockitoBean
    private SnippetGeneratorService snippetGeneratorService;

    private CrawledDocument createMockDoc(long id, String url, String title, String text) {
        CrawledDocument doc = new CrawledDocument(
                url,
                "searchforge.dev",
                title,
                text,
                "hash_" + id,
                200,
                Instant.now()
        );
        doc.setId(id);
        return doc;
    }

    @Test
    @DisplayName("GET /api/v1/search enriches response with dynamic snippet and <mark> highlighting")
    void searchFreeText_shouldReturnDynamicSnippetAndHighlighting() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                1L,
                4.85,
                1,
                List.of("postgresql", "btree"),
                Map.of("postgresql", 2.5, "btree", 2.35)
        );

        given(rankedRetrievalEngine.search("postgresql btree", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(
                1L,
                "https://searchforge.dev/postgres-btree",
                "PostgreSQL B-Tree Indexes",
                "PostgreSQL uses a B-tree index for high-performance equality and range queries."
        );
        given(crawledDocumentRepository.findAllById(List.of(1L))).willReturn(List.of(doc));

        // Mock snippet generator
        HighlightedSnippet hs = new HighlightedSnippet(
                "PostgreSQL uses a B-tree index for high-performance equality and range queries.",
                "<mark>PostgreSQL</mark> uses a <mark>B-tree</mark> index for high-performance equality and range queries.",
                Set.of("postgresql", "btree"),
                true
        );
        given(snippetGeneratorService.generateSnippet(eq(doc.getCleanText()), anySet())).willReturn(hs);

        // No spelling error
        given(spellingCorrectionService.suggest("postgresql btree"))
                .willReturn(new QuerySuggestionResponse("postgresql btree", "postgresql btree", false, List.of()));

        mockMvc.perform(get("/api/v1/search?q=postgresql btree&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("postgresql btree"))
                .andExpect(jsonPath("$.didYouMean").doesNotExist()) // Omitted when no correction
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].docId").value(1))
                .andExpect(jsonPath("$.results[0].title").value("PostgreSQL B-Tree Indexes"))
                .andExpect(jsonPath("$.results[0].score").value(4.85))
                .andExpect(jsonPath("$.results[0].rank").value(1))
                .andExpect(jsonPath("$.results[0].snippet").value(containsString("PostgreSQL uses a B-tree index")))
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("<mark>PostgreSQL</mark> uses a <mark>B-tree</mark> index for high-performance equality and range queries."));
    }

    @Test
    @DisplayName("GET /api/v1/search suggests didYouMean when query contains typos")
    void searchFreeText_withTypo_shouldIncludeDidYouMean() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                1L,
                3.12,
                1,
                List.of("postgresql"),
                Map.of("postgresql", 3.12)
        );

        given(rankedRetrievalEngine.search("postgreql btre", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(1L, "https://searchforge.dev/pg", "PostgreSQL", "Guide to PostgreSQL");
        given(crawledDocumentRepository.findAllById(List.of(1L))).willReturn(List.of(doc));

        // Mock spelling correction suggestion
        SpellingCorrection c1 = new SpellingCorrection("postgreql", "postgresql", 1);
        SpellingCorrection c2 = new SpellingCorrection("btre", "btree", 1);
        QuerySuggestionResponse suggestion = new QuerySuggestionResponse("postgreql btre", "postgresql btree", true, List.of(c1, c2));
        given(spellingCorrectionService.suggest("postgreql btre")).willReturn(suggestion);

        HighlightedSnippet hs = new HighlightedSnippet("Guide to PostgreSQL", "Guide to <mark>PostgreSQL</mark>");
        given(snippetGeneratorService.generateSnippet(anyString(), anySet())).willReturn(hs);

        mockMvc.perform(get("/api/v1/search?q=postgreql btre&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("postgreql btre"))
                .andExpect(jsonPath("$.didYouMean").value("postgresql btree"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].docId").value(1))
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("Guide to <mark>PostgreSQL</mark>"));
    }

    @Test
    @DisplayName("GET /api/v1/search with synonym expansion retrieves hits and highlights synonyms")
    void searchFreeText_withSynonyms_shouldIncludeExpandedTermsInSnippets() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                2L,
                2.75,
                1,
                List.of("database"),
                Map.of("database", 2.75)
        );

        given(rankedRetrievalEngine.search("db", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(2L, "https://searchforge.dev/db", "Database Architecture", "Deep dive into database storage engines.");
        given(crawledDocumentRepository.findAllById(List.of(2L))).willReturn(List.of(doc));

        // Mock synonym expansion
        ExpandedQuery expandedQuery = new ExpandedQuery(
                "db",
                List.of(new ExpandedTerm("db", 1.0, true), new ExpandedTerm("database", 0.5, false)),
                true
        );
        given(synonymExpansionService.expandQuery("db")).willReturn(expandedQuery);

        HighlightedSnippet hs = new HighlightedSnippet(
                "Deep dive into database storage engines.",
                "Deep dive into <mark>database</mark> storage engines."
        );
        given(snippetGeneratorService.generateSnippet(eq(doc.getCleanText()), anySet())).willReturn(hs);

        mockMvc.perform(get("/api/v1/search?q=db&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("db"))
                .andExpect(jsonPath("$.results[0].docId").value(2))
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("Deep dive into <mark>database</mark> storage engines."));
    }

    @Test
    @DisplayName("GET /api/v1/search preserves exact quoted phrases without spelling or synonym expansion")
    void searchFreeText_withExactQuotedPhrase_shouldPreservePhraseSemantics() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                3L,
                5.0,
                1,
                List.of("search", "engine"),
                Map.of("search", 2.0, "engine", 2.0, "PHRASE:\"search engine\"", 1.0)
        );

        given(rankedRetrievalEngine.search("\"search engine\"", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(3L, "https://searchforge.dev/se", "Search Engine", "Building a custom search engine from scratch.");
        given(crawledDocumentRepository.findAllById(List.of(3L))).willReturn(List.of(doc));

        HighlightedSnippet hs = new HighlightedSnippet(
                "Building a custom search engine from scratch.",
                "Building a custom <mark>search</mark> <mark>engine</mark> from scratch."
        );
        given(snippetGeneratorService.generateSnippet(anyString(), anySet())).willReturn(hs);

        mockMvc.perform(get("/api/v1/search?q=\"search engine\"&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("\"search engine\""))
                .andExpect(jsonPath("$.didYouMean").doesNotExist()) // No spelling suggestions on exact phrases
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("Building a custom <mark>search</mark> <mark>engine</mark> from scratch."));
    }

    @Test
    @DisplayName("GET /api/v1/search/ranked-boolean enriches results with highlighted snippets while preserving AST semantics")
    void searchRankedBoolean_shouldReturnEnrichedSnippets() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                4L,
                3.8,
                1,
                List.of("java", "spring"),
                Map.of("java", 1.9, "spring", 1.9)
        );

        given(rankedBooleanQueryEngine.searchRankedBoolean("java AND spring", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(4L, "https://searchforge.dev/js", "Java Spring Guide", "Tutorial on Java and Spring Boot.");
        given(crawledDocumentRepository.findAllById(List.of(4L))).willReturn(List.of(doc));

        HighlightedSnippet hs = new HighlightedSnippet(
                "Tutorial on Java and Spring Boot.",
                "Tutorial on <mark>Java</mark> and <mark>Spring</mark> Boot."
        );
        given(snippetGeneratorService.generateSnippet(anyString(), anySet())).willReturn(hs);

        mockMvc.perform(get("/api/v1/search/ranked-boolean?q=java AND spring&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java AND spring"))
                .andExpect(jsonPath("$.didYouMean").doesNotExist())
                .andExpect(jsonPath("$.results[0].docId").value(4))
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("Tutorial on <mark>Java</mark> and <mark>Spring</mark> Boot."));
    }

    @Test
    @DisplayName("GET /api/v1/search/filtered enriches results with highlighted snippets while preserving Boolean filter")
    void searchFiltered_shouldReturnEnrichedSnippets() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                5L,
                4.1,
                1,
                List.of("microservices"),
                Map.of("microservices", 4.1)
        );

        given(rankedBooleanQueryEngine.searchFiltered("microservices", "NOT legacy", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(5L, "https://searchforge.dev/ms", "Microservices", "Modern microservices architecture.");
        given(crawledDocumentRepository.findAllById(List.of(5L))).willReturn(List.of(doc));

        HighlightedSnippet hs = new HighlightedSnippet(
                "Modern microservices architecture.",
                "Modern <mark>microservices</mark> architecture."
        );
        given(snippetGeneratorService.generateSnippet(anyString(), anySet())).willReturn(hs);

        mockMvc.perform(get("/api/v1/search/filtered?q=microservices&filter=NOT legacy&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("microservices"))
                .andExpect(jsonPath("$.results[0].docId").value(5))
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("Modern <mark>microservices</mark> architecture."));
    }

    @Test
    @DisplayName("GET /api/v1/search handles 0 hits gracefully while retaining didYouMean suggestion")
    void searchFreeText_withZeroHits_shouldReturnEmptyListWithDidYouMean() throws Exception {
        given(rankedRetrievalEngine.search("postgreql btre", 10)).willReturn(List.of());

        SpellingCorrection c1 = new SpellingCorrection("postgreql", "postgresql", 1);
        SpellingCorrection c2 = new SpellingCorrection("btre", "btree", 1);
        QuerySuggestionResponse suggestion = new QuerySuggestionResponse("postgreql btre", "postgresql btree", true, List.of(c1, c2));
        given(spellingCorrectionService.suggest("postgreql btre")).willReturn(suggestion);

        mockMvc.perform(get("/api/v1/search?q=postgreql btre&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("postgreql btre"))
                .andExpect(jsonPath("$.didYouMean").value("postgresql btree"))
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.returnedHits").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/search ensures XSS sanitization in dynamic snippets")
    void searchFreeText_shouldSanitizeHtmlToPreventXss() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                6L,
                2.0,
                1,
                List.of("security"),
                Map.of("security", 2.0)
        );

        given(rankedRetrievalEngine.search("security", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(6L, "https://searchforge.dev/sec", "Security", "<script>alert(1)</script> security best practices");
        given(crawledDocumentRepository.findAllById(List.of(6L))).willReturn(List.of(doc));

        HighlightedSnippet hs = new HighlightedSnippet(
                "&lt;script&gt;alert(1)&lt;/script&gt; security best practices",
                "&lt;script&gt;alert(1)&lt;/script&gt; <mark>security</mark> best practices"
        );
        given(snippetGeneratorService.generateSnippet(anyString(), anySet())).willReturn(hs);

        mockMvc.perform(get("/api/v1/search?q=security&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].highlightedSnippet")
                        .value("&lt;script&gt;alert(1)&lt;/script&gt; <mark>security</mark> best practices"));
    }

    @Test
    @DisplayName("GET /api/v1/search returns 400 Bad Request when query is blank or limit invalid")
    void searchFreeText_validationErrors() throws Exception {
        mockMvc.perform(get("/api/v1/search?q=   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query parameter 'q' must not be blank"));

        mockMvc.perform(get("/api/v1/search?q=test&limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/search?q=test&limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));
    }
}
