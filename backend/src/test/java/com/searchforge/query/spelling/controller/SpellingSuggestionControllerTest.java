package com.searchforge.query.spelling.controller;

import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.model.SpellingCorrection;
import com.searchforge.query.spelling.service.SpellingCorrectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpellingSuggestionController.class)
class SpellingSuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpellingCorrectionService spellingCorrectionService;

    @Test
    @DisplayName("GET /api/v1/search/suggest should return 200 with corrected query and details")
    void shouldReturnSuggestionSuccessfully() throws Exception {
        QuerySuggestionResponse mockResponse = new QuerySuggestionResponse(
                "postgreql btre",
                "postgresql btree",
                true,
                List.of(
                        new SpellingCorrection("postgreql", "postgresql", 1),
                        new SpellingCorrection("btre", "btree", 1)
                )
        );

        given(spellingCorrectionService.suggest(eq("postgreql btre"))).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/search/suggest")
                        .param("q", "postgreql btre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("postgreql btre"))
                .andExpect(jsonPath("$.suggestedQuery").value("postgresql btree"))
                .andExpect(jsonPath("$.hasCorrection").value(true))
                .andExpect(jsonPath("$.corrections[0].original").value("postgreql"))
                .andExpect(jsonPath("$.corrections[0].suggestion").value("postgresql"))
                .andExpect(jsonPath("$.corrections[0].editDistance").value(1))
                .andExpect(jsonPath("$.corrections[1].original").value("btre"))
                .andExpect(jsonPath("$.corrections[1].suggestion").value("btree"))
                .andExpect(jsonPath("$.corrections[1].editDistance").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/search/suggest should return 200 with hasCorrection=false when query is spelled correctly")
    void shouldReturnNoCorrectionForValidQuery() throws Exception {
        QuerySuggestionResponse mockResponse = QuerySuggestionResponse.noCorrection("java spring");

        given(spellingCorrectionService.suggest(eq("java spring"))).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/search/suggest")
                        .param("q", "java spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalQuery").value("java spring"))
                .andExpect(jsonPath("$.suggestedQuery").value("java spring"))
                .andExpect(jsonPath("$.hasCorrection").value(false))
                .andExpect(jsonPath("$.corrections").isArray())
                .andExpect(jsonPath("$.corrections").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/search/suggest should return 400 Bad Request when query parameter 'q' is missing or blank")
    void shouldReturnBadRequestWhenQueryIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/search/suggest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Query parameter 'q' must not be blank"));

        mockMvc.perform(get("/api/v1/search/suggest").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Query parameter 'q' must not be blank"));
    }
}
