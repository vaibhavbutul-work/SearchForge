package com.searchforge.lifecycle.dto;

import com.searchforge.lifecycle.consistency.EmbeddingConsistencyStatus;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import java.time.Instant;

public record LifecycleStatusResponse(
        Long documentId,
        String url,
        DocumentLifecycleStatus status,
        int version,
        int retryCount,
        String statusMessage,
        Instant lastProcessedAt,
        EmbeddingConsistencyStatus embeddingStatus,
        boolean isSearchable
) {
}
