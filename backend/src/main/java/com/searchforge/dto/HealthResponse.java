package com.searchforge.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
) {
    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";

    @JsonIgnore
    public boolean isUp() {
        return STATUS_UP.equals(status);
    }
}
