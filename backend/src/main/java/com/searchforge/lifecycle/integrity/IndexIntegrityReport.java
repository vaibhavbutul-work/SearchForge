package com.searchforge.lifecycle.integrity;

import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import java.time.Instant;
import java.util.List;

/**
 * Immutable report representing the overall integrity and consistency of the search engine's indexes.
 */
public record IndexIntegrityReport(
        Instant timestamp,
        int totalDocumentsChecked,
        int activeDocumentCount,
        int deletedDocumentCount,
        int cleanDocuments,
        List<Long> missingLexicalDocuments,
        List<Long> orphanedLexicalDocuments,
        List<String> statisticsDiscrepancies,
        List<EmbeddingConsistencyReport> embeddingIssues
) {
    /**
     * Checks if the index is 100% consistent across storage, lexical index, positional index,
     * document statistics, and semantic embeddings.
     */
    public boolean isHealthy() {
        return (missingLexicalDocuments == null || missingLexicalDocuments.isEmpty())
                && (orphanedLexicalDocuments == null || orphanedLexicalDocuments.isEmpty())
                && (statisticsDiscrepancies == null || statisticsDiscrepancies.isEmpty())
                && (embeddingIssues == null || embeddingIssues.isEmpty());
    }

    /**
     * Total number of distinct consistency defects discovered during the audit.
     */
    public int totalIssueCount() {
        int count = 0;
        if (missingLexicalDocuments != null) count += missingLexicalDocuments.size();
        if (orphanedLexicalDocuments != null) count += orphanedLexicalDocuments.size();
        if (statisticsDiscrepancies != null) count += statisticsDiscrepancies.size();
        if (embeddingIssues != null) count += embeddingIssues.size();
        return count;
    }
}
