package com.searchforge.controller;

import com.searchforge.dto.HealthResponse;
import com.searchforge.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing health and readiness endpoints for the SearchForge backend.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthService healthService;

    // Constructor injection: Spring automatically injects the HealthService bean
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Health check endpoint to verify backend service and database status.
     * 
     * @return HTTP 200 with HealthResponse JSON payload
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = healthService.checkHealth();
        return ResponseEntity.ok(response);
    }
}
