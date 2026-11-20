package com.searchforge.query.autocomplete.controller;

import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;
import com.searchforge.query.autocomplete.service.AutocompleteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AutocompleteController.class)
class AutocompleteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutocompleteService autocompleteService;

    @Test
    @DisplayName("GET /api/v1/search/autocomplete should return 200 with suggestions")
    void shouldReturnSuggestionsSuccessfully() throws Exception {
        List<AutocompleteSuggestion> mockSuggestions = List.of(
                new AutocompleteSuggestion("postgresql", 120, 35, 8.42),
                new AutocompleteSuggestion("postings", 80, 25, 6.91)
        );

        given(autocompleteService.suggest(eq("post"), anyInt())).willReturn(mockSuggestions);

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "post")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefix").value("post"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.suggestions[0].term").value("postgresql"))
                .andExpect(jsonPath("$.suggestions[0].ctf").value(120))
                .andExpect(jsonPath("$.suggestions[0].df").value(35))
                .andExpect(jsonPath("$.suggestions[0].score").value(8.42))
                .andExpect(jsonPath("$.suggestions[1].term").value("postings"))
                .andExpect(jsonPath("$.suggestions[1].ctf").value(80))
                .andExpect(jsonPath("$.suggestions[1].df").value(25))
                .andExpect(jsonPath("$.suggestions[1].score").value(6.91));
    }

    @Test
    @DisplayName("GET /api/v1/search/autocomplete should return empty array when no terms match")
    void shouldReturnEmptyWhenNoMatch() throws Exception {
        given(autocompleteService.suggest(eq("xyz"), anyInt())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "xyz")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefix").value("xyz"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.suggestions").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/search/autocomplete should return 400 when prefix is missing or blank")
    void shouldReturnBadRequestForMissingOrBlankPrefix() throws Exception {
        mockMvc.perform(get("/api/v1/search/autocomplete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Prefix parameter 'prefix' must not be blank"));

        mockMvc.perform(get("/api/v1/search/autocomplete").param("prefix", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Prefix parameter 'prefix' must not be blank"));
    }

    @Test
    @DisplayName("GET /api/v1/search/autocomplete should return 400 for invalid limits")
    void shouldReturnBadRequestForInvalidLimits() throws Exception {
        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "post")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "post")
                        .param("limit", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "post")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'limit' must be between 1 and 100"));
    }

    @Test
    @DisplayName("GET /api/v1/search/autocomplete should use default limit of 5 when omitted")
    void shouldUseDefaultLimit() throws Exception {
        given(autocompleteService.suggest(eq("spring"), eq(5))).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefix").value("spring"))
                .andExpect(jsonPath("$.count").value(0));
    }
}
