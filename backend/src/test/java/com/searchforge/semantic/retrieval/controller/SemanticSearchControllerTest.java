package com.searchforge.semantic.retrieval.controller;

import com.searchforge.semantic.retrieval.SemanticRetrievalEngine;
import com.searchforge.semantic.retrieval.dto.SemanticSearchHitResponse;
import com.searchforge.semantic.retrieval.dto.SemanticSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SemanticSearchController.class)
@DisplayName("SemanticSearchController REST API Unit Tests")
class SemanticSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SemanticRetrievalEngine semanticRetrievalEngine;

    @Test
    @DisplayName("GET /api/v1/search/semantic returns 200 OK with semantic results")
    void testSemanticSearchEndpointSuccess() throws Exception {
        SemanticSearchHitResponse hit = new SemanticSearchHitResponse(
                42L,
                "https://oracle.com/java",
                "Java 21 Virtual Threads",
                "oracle.com",
                "Virtual threads reduce overhead...",
                0.8842,
                1
        );

        SemanticSearchResponse mockResponse = new SemanticSearchResponse(
                "concurrency",
                "semantic",
                1,
                1,
                12L,
                List.of(hit)
        );

        when(semanticRetrievalEngine.search(eq("concurrency"), eq(5))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/search/semantic")
                        .param("q", "concurrency")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("concurrency"))
                .andExpect(jsonPath("$.mode").value("semantic"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.returnedHits").value(1))
                .andExpect(jsonPath("$.results[0].documentId").value(42))
                .andExpect(jsonPath("$.results[0].title").value("Java 21 Virtual Threads"))
                .andExpect(jsonPath("$.results[0].semanticScore").value(0.8842))
                .andExpect(jsonPath("$.results[0].rank").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/search/semantic handles empty query with default limit")
    void testSemanticSearchDefaultQuery() throws Exception {
        SemanticSearchResponse emptyResponse = new SemanticSearchResponse("", "semantic", 0, 0, 1L, List.of());
        when(semanticRetrievalEngine.search(eq(""), anyInt())).thenReturn(emptyResponse);

        mockMvc.perform(get("/api/v1/search/semantic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(""))
                .andExpect(jsonPath("$.mode").value("semantic"))
                .andExpect(jsonPath("$.totalHits").value(0))
                .andExpect(jsonPath("$.results").isEmpty());
    }
}
