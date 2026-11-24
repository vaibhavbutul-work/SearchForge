package com.searchforge.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Task 11.3: Input Validation, Schema Bounds & Error Masking Tests")
class InputValidationAndHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Search query exceeding 500 characters is rejected with 400 Bad Request")
    void search_excessiveQueryLength_returns400() throws Exception {
        String longQuery = "a".repeat(501);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Search query parameter 'q' must not exceed 500 characters"));
    }

    @Test
    @DisplayName("Search filter exceeding 500 characters is rejected with 400 Bad Request")
    void searchFiltered_excessiveFilterLength_returns400() throws Exception {
        String longFilter = "lang:".concat("x".repeat(500));

        mockMvc.perform(get("/api/v1/search/filtered")
                        .param("q", "valid search")
                        .param("filter", longFilter))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Filter parameter 'filter' must not exceed 500 characters"));
    }

    @Test
    @DisplayName("Autocomplete prefix exceeding 100 characters is rejected with 400 Bad Request")
    void autocomplete_excessivePrefixLength_returns400() throws Exception {
        String longPrefix = "term".repeat(26); // 104 chars

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("prefix", longPrefix))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Prefix parameter 'prefix' must not exceed 100 characters"));
    }

    @Test
    @DisplayName("Spelling suggestion query exceeding 200 characters is rejected with 400 Bad Request")
    void suggest_excessiveQueryLength_returns400() throws Exception {
        String longQuery = "word".repeat(51); // 204 chars

        mockMvc.perform(get("/api/v1/search/suggest")
                        .param("q", longQuery))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Query parameter 'q' must not exceed 200 characters"));
    }

    @Test
    @DisplayName("Index build with batchSize outside 1..1000 is rejected with 400 Bad Request")
    void indexBuild_outOfBoundsBatchSize_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/index/build")
                        .param("batchSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'batchSize' must be between 1 and 1000"));

        mockMvc.perform(post("/api/v1/index/build")
                        .param("batchSize", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parameter 'batchSize' must be between 1 and 1000"));
    }

    @Test
    @DisplayName("Crawl start with more than 50 seed URLs is rejected with 400 Bad Request")
    void crawlerStart_excessiveSeedUrls_returns400() throws Exception {
        List<String> seeds = Collections.nCopies(51, "https://example.com");
        String jsonPayload = "{\"seedUrls\": " + seeds.stream().map(s -> "\"" + s + "\"").toList() + "}";

        mockMvc.perform(post("/api/v1/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Maximum 50 seed URLs allowed per crawl request"));
    }

    @Test
    @DisplayName("Crawl start with seed URL exceeding 2048 characters is rejected with 400 Bad Request")
    void crawlerStart_excessiveUrlLength_returns400() throws Exception {
        String hugeUrl = "https://example.com/" + "a".repeat(2050);
        String jsonPayload = "{\"seedUrls\": [\"" + hugeUrl + "\"]}";

        mockMvc.perform(post("/api/v1/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Seed URL exceeds maximum allowed length of 2048 characters"));
    }

    @Test
    @DisplayName("Crawler documents with pageSize > 100 is rejected with 400 Bad Request")
    void crawlerDocuments_excessivePageSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/crawler/documents")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page size must not exceed 100"));
    }

    @Test
    @DisplayName("Crawler documents with negative page index is rejected with 400 Bad Request")
    void crawlerDocuments_negativePageIndex_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/crawler/documents")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Page index must not be negative"));
    }

    @Test
    @DisplayName("Lifecycle status with docId <= 0 is rejected with 400 Bad Request")
    void lifecycleStatus_nonPositiveDocId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/lifecycle/status/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document ID must be a positive integer"));

        mockMvc.perform(get("/api/v1/lifecycle/status/-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document ID must be a positive integer"));
    }

    @Test
    @DisplayName("Type mismatch parameter returns RFC 7807 400 Bad Request without leaking internal stack traces")
    void typeMismatch_parameter_returnsSanitized400() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "valid search")
                        .param("limit", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Parameter 'limit' must be of type int"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Malformed JSON payload returns clean 400 Bad Request without leaking stack traces")
    void malformedJson_payload_returnsSanitized400() throws Exception {
        mockMvc.perform(post("/api/v1/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ malformed_json: true, unterminated"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON request body"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Unsupported HTTP method returns 405 Method Not Allowed")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(put("/api/v1/search")
                        .param("q", "test"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }
}
