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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;

    @Test
    @DisplayName("GET /api/v1/health returns 200 OK with UP status")
    void getHealth_shouldReturnOkStatus() throws Exception {
        HealthResponse mockResponse = new HealthResponse(
            "UP",
            "SearchForge",
            "1.0.0",
            "UP",
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
}
