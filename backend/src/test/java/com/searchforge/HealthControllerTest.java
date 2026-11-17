package com.searchforge;

import com.searchforge.controller.HealthController;
import com.searchforge.dto.HealthResponse;
import com.searchforge.service.HealthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;

    @Test
    @DisplayName("GET /api/v1/health returns 200 OK when service is UP")
    void getHealth_shouldReturn200Ok_whenStatusIsUp() throws Exception {
        HealthResponse mockResponse = new HealthResponse(
            HealthResponse.STATUS_UP,
            "SearchForge",
            "1.0.0",
            HealthResponse.STATUS_UP,
            Instant.parse("2026-11-16T12:00:00Z")
        );

        given(healthService.checkHealth()).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("SearchForge"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.database").value("UP"))
            .andExpect(jsonPath("$.timestamp").value("2026-11-16T12:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/health returns 503 Service Unavailable when service is DOWN")
    void getHealth_shouldReturn503ServiceUnavailable_whenStatusIsDown() throws Exception {
        HealthResponse mockResponse = new HealthResponse(
            HealthResponse.STATUS_DOWN,
            "SearchForge",
            "1.0.0",
            HealthResponse.STATUS_DOWN,
            Instant.parse("2026-11-16T12:00:00Z")
        );

        given(healthService.checkHealth()).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value("DOWN"))
            .andExpect(jsonPath("$.service").value("SearchForge"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.database").value("DOWN"))
            .andExpect(jsonPath("$.timestamp").value("2026-11-16T12:00:00Z"));
    }
}
