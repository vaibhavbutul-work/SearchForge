package com.searchforge.lifecycle.dto;

public record DocumentDeletionResponse(
        Long documentId,
        boolean success,
        boolean hardDeleted,
        long durationMs,
        String message
) {
}
