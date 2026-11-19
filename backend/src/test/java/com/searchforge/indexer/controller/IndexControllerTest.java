package com.searchforge.indexer.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.retrieval.BooleanRetrievalEngine;
import com.searchforge.indexer.retrieval.BooleanSearchResult;
import com.searchforge.indexer.service.CorpusSyncResult;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IndexController.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexingEngine indexingEngine;

    @MockitoBean
    private BooleanRetrievalEngine retrievalEngine;

    @MockitoBean
    private InvertedIndex inMemoryIndex;

    @MockitoBean
    private CrawledDocumentRepository crawledDocumentRepository;

    @Test
    @DisplayName("POST /api/v1/index/build returns 200 OK with synchronization metrics")
    void buildIndex_shouldReturn200Ok() throws Exception {
        CorpusSyncResult syncResult = new CorpusSyncResult(10, 10, 0, 520L, 45L);
        given(indexingEngine.synchronizeCorpus(anyInt())).willReturn(syncResult);

        mockMvc.perform(post("/api/v1/index/build?batchSize=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsProcessed").value(10))
                .andExpect(jsonPath("$.documentsIndexed").value(10))
                .andExpect(jsonPath("$.totalTokensIndexed").value(520));

        verify(indexingEngine).synchronizeCorpus(50);
    }

    @Test
    @DisplayName("POST /api/v1/index/rebuild returns 200 OK after full rebuild")
    void rebuildIndex_shouldReturn200Ok() throws Exception {
        CorpusSyncResult rebuildResult = new CorpusSyncResult(25, 25, 0, 1500L, 120L);
        given(indexingEngine.rebuildIndex()).willReturn(rebuildResult);

        mockMvc.perform(post("/api/v1/index/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsProcessed").value(25))
                .andExpect(jsonPath("$.documentsIndexed").value(25));

        verify(indexingEngine).rebuildIndex();
    }

    @Test
    @DisplayName("POST /api/v1/index/warmup returns 200 OK and triggers in-memory warmup")
    void warmupIndex_shouldReturn200Ok() throws Exception {
        mockMvc.perform(post("/api/v1/index/warmup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").exists());

        verify(indexingEngine).warmupInMemoryIndex();
    }

    @Test
    @DisplayName("GET /api/v1/index/stats returns 200 OK with corpus and memory metrics")
    void getStats_shouldReturn200OkWithMetrics() throws Exception {
        IndexingStats stats = new IndexingStats(100L, 2500L, 15000L, 75.5);
        given(indexingEngine.getStats()).willReturn(stats);
        given(inMemoryIndex.getTotalDocuments()).willReturn(100);
        given(inMemoryIndex.getVocabularySize()).willReturn(2500);

        mockMvc.perform(get("/api/v1/index/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIndexedDocuments").value(100))
                .andExpect(jsonPath("$.vocabularySize").value(2500))
                .andExpect(jsonPath("$.totalPostings").value(15000))
                .andExpect(jsonPath("$.averageDocumentLength").value(75.5))
                .andExpect(jsonPath("$.inMemoryDocuments").value(100))
                .andExpect(jsonPath("$.inMemoryVocabularySize").value(2500));
    }

    @Test
    @DisplayName("GET /api/v1/search/boolean returns 200 OK with resolved document hits")
    void searchBoolean_shouldReturn200OkWithHits() throws Exception {
        BooleanSearchResult searchResult = new BooleanSearchResult(List.of(1L), 1, 12L);
        given(retrievalEngine.search("java AND spring")).willReturn(searchResult);

        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.dev/spring",
                "searchforge.dev",
                "Spring Boot Search Engine",
                "Comprehensive guide to building custom search engines in Spring Boot with inverted indices.",
                "h_spring",
                200,
                Instant.now()
        );
        doc.setId(1L);
        given(crawledDocumentRepository.findAllById(List.of(1L))).willReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/search/boolean?q=java AND spring&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java AND spring"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].url").value("https://searchforge.dev/spring"))
                .andExpect(jsonPath("$.hits[0].title").value("Spring Boot Search Engine"))
                .andExpect(jsonPath("$.hits[0].domain").value("searchforge.dev"));
    }

    @Test
    @DisplayName("GET /api/v1/search/boolean returns 400 Bad Request when query is empty")
    void searchBoolean_shouldReturn400BadRequest_whenQueryIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/search/boolean?q="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
