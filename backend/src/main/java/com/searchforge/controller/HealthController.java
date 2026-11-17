package com.searchforge.controller;

import com.searchforge.dto.HealthResponse;
import com.searchforge.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing health and readiness endpoints for SearchForge.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Health check endpoint.
     * 
     * @return HTTP 200 OK if service and database are UP,
     *         HTTP 503 Service Unavailable if any dependency is DOWN.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse response = healthService.checkHealth();
        if (response.isUp()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
