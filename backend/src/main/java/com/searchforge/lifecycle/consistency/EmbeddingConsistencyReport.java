package com.searchforge.lifecycle.consistency;

/**
 * Detailed report evaluating the consistency state of an individual document's embedding.
 */
public record EmbeddingConsistencyReport(
        Long documentId,
        EmbeddingConsistencyStatus status,
        Integer documentVersion,
        Integer embeddingVersion,
        String documentHash,
        String embeddingHash,
        String configuredModel,
        String embeddingModel,
        int configuredDimensions,
        int embeddingDimensions,
        boolean inDatabase,
        boolean inVectorStorage,
        String details
) {
    public boolean isConsistent() {
        return status == EmbeddingConsistencyStatus.CURRENT;
    }
}
