package com.searchforge.dto;

import java.time.Instant;

/**
 * Data Transfer Object (DTO) representing the health status of SearchForge.
 * Using a Java 21 Record provides immutability, compact syntax, and zero boilerplate.
 */
public record HealthResponse(
    String status,
    String service,
    String version,
    String database,
    Instant timestamp
) {}
