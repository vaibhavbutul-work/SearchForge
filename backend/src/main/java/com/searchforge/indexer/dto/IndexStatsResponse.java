package com.searchforge.indexer.dto;

/**
 * REST API response DTO for corpus and in-memory index statistics.
 */
public record IndexStatsResponse(
        long totalIndexedDocuments,
        long vocabularySize,
        long totalPostings,
        double averageDocumentLength,
        int inMemoryDocuments,
        int inMemoryVocabularySize
) {
}
