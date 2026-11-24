package com.searchforge.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard RFC 7807 Problem Details representation for SearchForge API errors.
 * Ensures consistent, machine-readable error bodies without exposing internal stack traces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiErrorResponse badRequest(String message, String path) {
        return new ApiErrorResponse(Instant.now(), 400, message, message, path);
    }

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path);
    }
}
