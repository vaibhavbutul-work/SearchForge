package com.searchforge.lifecycle.dto;

import java.time.Instant;
import java.util.List;

public record IntegrityCheckResponse(
        Instant timestamp,
        boolean healthy,
        int totalDocumentsChecked,
        int activeDocumentCount,
        int deletedDocumentCount,
        int cleanDocuments,
        int totalIssueCount,
        List<Long> missingLexicalDocuments,
        List<Long> orphanedLexicalDocuments,
        List<String> statisticsDiscrepancies,
        List<String> embeddingIssues
) {
}
