package com.searchforge.ranking.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.query.snippet.model.HighlightedSnippet;
import com.searchforge.query.snippet.service.SnippetGeneratorService;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import com.searchforge.ranking.retrieval.RankedBooleanQueryEngine;
import com.searchforge.ranking.retrieval.RankedRetrievalEngine;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.IdfCalculator;
import com.searchforge.search.hybrid.HybridCandidate;
import com.searchforge.search.hybrid.HybridSearchResult;
import com.searchforge.search.hybrid.HybridSearchService;
import com.searchforge.search.hybrid.SearchMode;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@DisplayName("SearchController Hybrid and Semantic REST API Tests")
class SearchControllerHybridTest {

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
    private HybridSearchService hybridSearchService;

    @MockitoBean
    private SpellingCorrectionService spellingCorrectionService;

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
    @DisplayName("GET /api/v1/search?mode=hybrid returns 200 OK with enriched multi-modal ranking fields")
    void searchHybrid_shouldReturn200OkWithEnrichedScores() throws Exception {
        HybridCandidate candidate = new HybridCandidate(
                42L,
                0.0325,
                1,
                6.82,
                0.87,
                2,
                1,
                0.0325,
                List.of("memory", "management"),
                Map.of("memory", 3.5, "management", 3.32)
        );
        HybridSearchResult result = HybridSearchResult.of("memory management", SearchMode.HYBRID, List.of(candidate));
        given(hybridSearchService.search("memory management", SearchMode.HYBRID, 10)).willReturn(result);

        CrawledDocument doc = createMockDoc(
                42L,
                "https://searchforge.dev/memory",
                "Java Memory Management",
                "Deep dive into JVM garbage collection and heap memory management."
        );
        given(crawledDocumentRepository.findAllById(List.of(42L))).willReturn(List.of(doc));
        given(snippetGeneratorService.generateSnippet(anyString(), anySet()))
                .willReturn(new HighlightedSnippet(
                        "Deep dive into JVM garbage collection and heap memory management.",
                        "Deep dive into JVM garbage collection and heap <mark>memory</mark> <mark>management</mark>."
                ));

        mockMvc.perform(get("/api/v1/search?q=memory management&mode=hybrid&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("memory management"))
                .andExpect(jsonPath("$.mode").value("hybrid"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].docId").value(42))
                .andExpect(jsonPath("$.results[0].documentId").value(42))
                .andExpect(jsonPath("$.results[0].title").value("Java Memory Management"))
                .andExpect(jsonPath("$.results[0].score").value(0.0325))
                .andExpect(jsonPath("$.results[0].rank").value(1))
                .andExpect(jsonPath("$.results[0].finalRank").value(1))
                .andExpect(jsonPath("$.results[0].bm25Score").value(6.82))
                .andExpect(jsonPath("$.results[0].semanticScore").value(0.87))
                .andExpect(jsonPath("$.results[0].bm25Rank").value(2))
                .andExpect(jsonPath("$.results[0].semanticRank").value(1))
                .andExpect(jsonPath("$.results[0].rrfScore").value(0.0325))
                .andExpect(jsonPath("$.results[0].highlightedSnippet").value("Deep dive into JVM garbage collection and heap <mark>memory</mark> <mark>management</mark>."));
    }

    @Test
    @DisplayName("GET /api/v1/search?mode=semantic returns 200 OK with semantic mode metadata")
    void searchSemantic_shouldReturn200Ok() throws Exception {
        HybridCandidate candidate = new HybridCandidate(
                7L,
                0.9125,
                1,
                null,
                0.9125,
                null,
                1,
                null,
                List.of(),
                Map.of()
        );
        HybridSearchResult result = HybridSearchResult.of("software architecture", SearchMode.SEMANTIC, List.of(candidate));
        given(hybridSearchService.search("software architecture", SearchMode.SEMANTIC, 10)).willReturn(result);

        CrawledDocument doc = createMockDoc(7L, "https://searchforge.dev/arch", "Clean Architecture", "Architecture principles.");
        given(crawledDocumentRepository.findAllById(List.of(7L))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/search?q=software architecture&mode=semantic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("software architecture"))
                .andExpect(jsonPath("$.mode").value("semantic"))
                .andExpect(jsonPath("$.results[0].docId").value(7))
                .andExpect(jsonPath("$.results[0].score").value(0.9125))
                .andExpect(jsonPath("$.results[0].semanticScore").value(0.9125))
                .andExpect(jsonPath("$.results[0].bm25Score").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/search?mode=invalid returns 400 Bad Request with supported modes")
    void search_invalidMode_shouldReturn400BadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search?q=java&mode=quantum"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid search mode: 'quantum'. Supported modes are: lexical, semantic, hybrid"));
    }

    @Test
    @DisplayName("GET /api/v1/search transparently surfaces fallback status when degradation occurs")
    void search_withFallback_shouldExposeTelemetry() throws Exception {
        HybridCandidate candidate = new HybridCandidate(
                12L,
                3.14,
                1,
                3.14,
                null,
                1,
                null,
                null,
                List.of("java"),
                Map.of("java", 3.14)
        );
        HybridSearchResult result = HybridSearchResult.fallback(
                "java",
                SearchMode.HYBRID,
                SearchMode.LEXICAL,
                "Semantic retrieval failed; degraded to lexical BM25: ONNX runtime error",
                List.of(candidate)
        );
        given(hybridSearchService.search("java", SearchMode.HYBRID, 10)).willReturn(result);

        CrawledDocument doc = createMockDoc(12L, "https://searchforge.dev/fallback", "Java Doc", "Content");
        given(crawledDocumentRepository.findAllById(List.of(12L))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/search?q=java&mode=hybrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("lexical"))
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("Semantic retrieval failed; degraded to lexical BM25: ONNX runtime error"))
                .andExpect(jsonPath("$.results[0].docId").value(12));
    }

    @Test
    @DisplayName("GET /api/v1/search preserves spelling didYouMean transparently across hybrid search")
    void search_hybridWithSpellingCorrection_shouldIncludeDidYouMean() throws Exception {
        given(spellingCorrectionService.suggest("jvaa sprng"))
                .willReturn(new QuerySuggestionResponse("jvaa sprng", "java spring", true, List.of()));

        HybridSearchResult result = HybridSearchResult.of("jvaa sprng", SearchMode.HYBRID, List.of());
        given(hybridSearchService.search("jvaa sprng", SearchMode.HYBRID, 10)).willReturn(result);

        mockMvc.perform(get("/api/v1/search?q=jvaa sprng&mode=hybrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("jvaa sprng"))
                .andExpect(jsonPath("$.mode").value("hybrid"))
                .andExpect(jsonPath("$.didYouMean").value("java spring"));
    }
}
