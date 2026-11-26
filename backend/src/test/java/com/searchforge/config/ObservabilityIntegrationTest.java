package com.searchforge.config;

import com.searchforge.config.observability.RequestCorrelationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@Tag("regression")
@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "management.endpoint.health.probes.enabled=true"
})
@AutoConfigureMockMvc
@DisplayName("Task 13.6: Logging, Correlation ID, Health Probes & Metrics Integration Tests")
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("RequestCorrelationFilter generates valid X-Request-Id when missing and preserves valid client ID")
    void correlationIdGenerationAndValidation() throws Exception {
        MvcResult generated = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestCorrelationFilter.HEADER_REQUEST_ID))
                .andReturn();

        String genId = generated.getResponse().getHeader(RequestCorrelationFilter.HEADER_REQUEST_ID);
        assertTrue(RequestCorrelationFilter.isValidRequestId(genId));

        // Valid incoming ID is preserved
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "trace-req_12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-req_12345"));

        // Malicious CRLF / log-injection payload is rejected and replaced with fresh UUID
        MvcResult sanitized = mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "bad\nid\r\ninjected"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String returnedId = sanitized.getResponse().getHeader("X-Request-Id");
        assertNotEquals("bad\nid\r\ninjected", returnedId);
        assertTrue(RequestCorrelationFilter.isValidRequestId(returnedId));
    }

    @Test
    @DisplayName("Actuator liveness and readiness probes return 200 UP")
    void actuatorLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator Prometheus endpoint exposes SearchForge custom metrics and JVM/HTTP metrics")
    void actuatorPrometheusExposesSearchForgeMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("searchforge_index_documents")))
                .andExpect(content().string(containsString("searchforge_vector_embeddings")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
