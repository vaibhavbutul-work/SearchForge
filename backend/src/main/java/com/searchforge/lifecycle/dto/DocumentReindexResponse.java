package com.searchforge.lifecycle.dto;

public record DocumentReindexResponse(
        Long documentId,
        boolean success,
        int version,
        int tokenCount,
        long durationMs,
        String message
) {
}
