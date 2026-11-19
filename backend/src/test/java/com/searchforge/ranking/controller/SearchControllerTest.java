package com.searchforge.ranking.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

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
    @DisplayName("GET /api/v1/search returns 200 OK with ranked results")
    void searchFreeText_shouldReturn200OkWithRankedResults() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                1L,
                1.1964,
                1,
                List.of("java", "spring"),
                Map.of("java", 0.5982, "spring", 0.5982)
        );

        given(rankedRetrievalEngine.search("java spring", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(
                1L,
                "https://searchforge.dev/spring",
                "Spring Boot Masterclass",
                "Comprehensive guide to building custom search engines."
        );
        given(crawledDocumentRepository.findAllById(List.of(1L))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/search?q=java spring&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java spring"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.results[0].docId").value(1))
                .andExpect(jsonPath("$.results[0].title").value("Spring Boot Masterclass"))
                .andExpect(jsonPath("$.results[0].score").value(1.1964))
                .andExpect(jsonPath("$.results[0].rank").value(1))
                .andExpect(jsonPath("$.results[0].matchedTerms[0]").value("java"))
                .andExpect(jsonPath("$.results[0].explanation").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/search with explain=true includes score breakdown")
    void searchFreeText_withExplainTrue_shouldIncludeExplanation() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(
                1L,
                1.1964,
                1,
                List.of("java"),
                Map.of("java", 0.5982)
        );

        given(rankedRetrievalEngine.search("java", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(1L, "https://searchforge.dev/java", "Java Basics", "Java tutorial");
        given(crawledDocumentRepository.findAllById(List.of(1L))).willReturn(List.of(doc));

        // Mock explanation retrieval statistics
        given(invertedIndex.getDocLength(1L)).willReturn(6);
        given(invertedIndex.getAverageDocumentLength()).willReturn(4.67);
        given(invertedIndex.getTotalDocuments()).willReturn(3);

        PostingsList pl = new PostingsList();
        pl.addPosting(new Posting(1L, 2, List.of(0, 4)));
        given(invertedIndex.getPostings("java")).willReturn(Optional.of(pl));

        given(idfCalculator.calculateIdf(anyLong(), anyLong())).willReturn(0.47);
        given(termScorer.calculateSaturation(anyInt(), anyInt(), anyDouble())).willReturn(1.2727);

        mockMvc.perform(get("/api/v1/search?q=java&explain=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].explanation").exists())
                .andExpect(jsonPath("$.results[0].explanation.java.rawTf").value(2))
                .andExpect(jsonPath("$.results[0].explanation.java.idf").value(0.47))
                .andExpect(jsonPath("$.results[0].explanation.java.saturation").value(1.2727));
    }

    @Test
    @DisplayName("GET /api/v1/search/ranked-boolean returns 200 OK for valid Boolean query")
    void searchRankedBoolean_shouldReturn200Ok() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(2L, 2.5, 1, List.of("java"), Map.of("java", 2.5));
        given(rankedBooleanQueryEngine.searchRankedBoolean("java AND spring", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(2L, "https://searchforge.dev/spring", "Spring Guide", "Spring content");
        given(crawledDocumentRepository.findAllById(List.of(2L))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/search/ranked-boolean?q=java AND spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].docId").value(2))
                .andExpect(jsonPath("$.results[0].score").value(2.5));
    }

    @Test
    @DisplayName("GET /api/v1/search/filtered returns 200 OK for valid filtered query")
    void searchFiltered_shouldReturn200Ok() throws Exception {
        RankedSearchResult hit = new RankedSearchResult(3L, 3.1, 1, List.of("microservice"), Map.of("microservice", 3.1));
        given(rankedBooleanQueryEngine.searchFiltered("microservices", "NOT legacy", 10)).willReturn(List.of(hit));

        CrawledDocument doc = createMockDoc(3L, "https://searchforge.dev/micro", "Cloud Microservices", "Content");
        given(crawledDocumentRepository.findAllById(List.of(3L))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/search/filtered?q=microservices&filter=NOT legacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].docId").value(3))
                .andExpect(jsonPath("$.results[0].title").value("Cloud Microservices"));
    }

    @Test
    @DisplayName("GET /api/v1/search returns 400 Bad Request when query parameter 'q' is blank")
    void search_shouldReturn400BadRequest_whenQueryBlank() throws Exception {
        mockMvc.perform(get("/api/v1/search?q=   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query parameter 'q' must not be blank"));
    }

    @Test
    @DisplayName("GET /api/v1/search returns 400 Bad Request when limit is out of bounds")
    void search_shouldReturn400BadRequest_whenLimitOutOfBounds() throws Exception {
        mockMvc.perform(get("/api/v1/search?q=java&limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/search?q=java&limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));
    }

    @Test
    @DisplayName("GET /api/v1/search/filtered returns 400 Bad Request when filter is blank")
    void searchFiltered_shouldReturn400BadRequest_whenFilterBlank() throws Exception {
        mockMvc.perform(get("/api/v1/search/filtered?q=java&filter="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Filter parameter 'filter' must not be blank"));
    }

    @Test
    @DisplayName("GET /api/v1/search/ranked-boolean returns 400 Bad Request when syntax is invalid")
    void searchRankedBoolean_shouldReturn400BadRequest_onSyntaxError() throws Exception {
        given(rankedBooleanQueryEngine.searchRankedBoolean(eq("java AND"), anyInt()))
                .willThrow(new IllegalArgumentException("Unexpected end of query"));

        mockMvc.perform(get("/api/v1/search/ranked-boolean?q=java AND"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Boolean query syntax: Unexpected end of query"));
    }
}
