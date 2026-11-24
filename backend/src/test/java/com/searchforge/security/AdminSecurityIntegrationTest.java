package com.searchforge.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "searchforge.security.enabled=true",
        "searchforge.security.admin-api-key=test-integration-admin-key",
        "searchforge.security.header-name=X-API-KEY"
})
@DisplayName("Task 11.2: End-to-End Administrative Security Integration Tests")
class AdminSecurityIntegrationTest {

    private static final String VALID_KEY = "test-integration-admin-key";
    private static final String INVALID_KEY = "bad-hacker-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/crawler/status without API key is rejected with 401 Unauthorized")
    void crawlerStatus_withoutKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/crawler/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Missing API key. Provide a valid 'X-API-KEY' header."));
    }

    @Test
    @DisplayName("POST /api/v1/index/rebuild with invalid API key is rejected with 401 Unauthorized")
    void indexRebuild_withInvalidKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/index/rebuild")
                        .header("X-API-KEY", INVALID_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid API key."));
    }

    @Test
    @DisplayName("DELETE /api/v1/lifecycle/999 without API key is rejected with 401 Unauthorized")
    void lifecycleDelete_withoutKey_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/lifecycle/999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/crawler/status with valid X-API-KEY is authorized (returns 200 OK)")
    void crawlerStatus_withValidKey_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/crawler/status")
                        .header("X-API-KEY", VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").exists());
    }

    @Test
    @DisplayName("GET /api/v1/lifecycle/metrics with valid Authorization Bearer token is authorized")
    void lifecycleMetrics_withBearerToken_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/lifecycle/metrics")
                        .header("Authorization", "Bearer " + VALID_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public search endpoint /api/v1/search without API key remains accessible (200 OK)")
    void publicSearch_withoutKey_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "search engine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("search engine"));
    }

    @Test
    @DisplayName("Public statistics endpoint /api/v1/index/stats without API key remains accessible (200 OK)")
    void publicIndexStats_withoutKey_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/index/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIndexedDocuments").exists());
    }
}
