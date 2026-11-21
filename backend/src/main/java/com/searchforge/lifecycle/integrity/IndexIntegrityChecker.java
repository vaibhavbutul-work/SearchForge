package com.searchforge.lifecycle.integrity;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.lifecycle.consistency.DocumentEmbeddingConsistencyValidator;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Non-destructive auditor and integrity checker for SearchForge.
 *
 * Reconciles state across the relational database (CrawledDocument),
 * inverted/positional index, document statistics, and dense vector embeddings.
 */
@Service
public class IndexIntegrityChecker {

    private static final Logger log = LoggerFactory.getLogger(IndexIntegrityChecker.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final InvertedIndex inMemoryIndex;
    private final IndexedDocumentMetadataRepository metadataRepository;
    private final DocumentEmbeddingConsistencyValidator embeddingValidator;

    public IndexIntegrityChecker(
            CrawledDocumentRepository crawledDocumentRepository,
            InvertedIndex inMemoryIndex,
            IndexedDocumentMetadataRepository metadataRepository,
            DocumentEmbeddingConsistencyValidator embeddingValidator
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository);
        this.inMemoryIndex = Objects.requireNonNull(inMemoryIndex);
        this.metadataRepository = Objects.requireNonNull(metadataRepository);
        this.embeddingValidator = Objects.requireNonNull(embeddingValidator);
    }

    /**
     * Conducts a complete, non-destructive audit across all index layers.
     */
    @Transactional(readOnly = true)
    public IndexIntegrityReport checkIntegrity() {
        log.info("Starting complete index reconciliation and integrity audit...");
        Instant timestamp = Instant.now();

        List<CrawledDocument> allDocs = crawledDocumentRepository.findAll();
        Map<Long, CrawledDocument> docMap = allDocs.stream()
                .collect(Collectors.toMap(CrawledDocument::getId, doc -> doc));

        List<CrawledDocument> activeDocs = allDocs.stream()
                .filter(doc -> doc.getStatus() == DocumentLifecycleStatus.ACTIVE)
                .toList();

        List<CrawledDocument> deletedDocs = allDocs.stream()
                .filter(doc -> doc.getStatus() == DocumentLifecycleStatus.DELETED)
                .toList();

        Set<Long> activeDocIds = activeDocs.stream()
                .map(CrawledDocument::getId)
                .collect(Collectors.toSet());

        // 1. Check for missing lexical postings for active documents
        List<Long> missingLexicalDocuments = new ArrayList<>();
        long totalTokensInActiveDocs = 0;

        for (CrawledDocument activeDoc : activeDocs) {
            Long id = activeDoc.getId();
            int docLength = inMemoryIndex.getDocLength(id);
            boolean inMetadata = metadataRepository.existsByDocumentId(id);

            if (docLength <= 0 || !inMetadata) {
                missingLexicalDocuments.add(id);
            } else {
                totalTokensInActiveDocs += docLength;
            }
        }

        // 2. Check for orphaned lexical postings (in-memory docs not active in DB)
        List<Long> indexedDocIds = inMemoryIndex.getAllDocumentIds();
        List<Long> orphanedLexicalDocuments = new ArrayList<>();

        for (Long indexedId : indexedDocIds) {
            if (!activeDocIds.contains(indexedId)) {
                orphanedLexicalDocuments.add(indexedId);
            }
        }

        // 3. Check BM25 Corpus Statistics
        List<String> statisticsDiscrepancies = new ArrayList<>();
        int indexTotalDocs = inMemoryIndex.getTotalDocuments();
        int expectedActiveDocs = activeDocs.size();

        if (indexTotalDocs != expectedActiveDocs) {
            statisticsDiscrepancies.add(String.format(
                    "Corpus document count discrepancy: InvertedIndex reported %d documents, but database has %d active documents",
                    indexTotalDocs, expectedActiveDocs
            ));
        }

        if (expectedActiveDocs > 0) {
            double expectedAvgDl = (double) totalTokensInActiveDocs / expectedActiveDocs;
            double actualAvgDl = inMemoryIndex.getAverageDocumentLength();
            if (Math.abs(expectedAvgDl - actualAvgDl) > 0.05) {
                statisticsDiscrepancies.add(String.format(
                        "Average document length discrepancy: InvertedIndex reported %.2f, expected %.2f",
                        actualAvgDl, expectedAvgDl
                ));
            }
        }

        // 4. Check Dense Semantic Embeddings
        List<EmbeddingConsistencyReport> allEmbeddingReports = embeddingValidator.validateAll();
        List<EmbeddingConsistencyReport> embeddingIssues = allEmbeddingReports.stream()
                .filter(report -> !report.isConsistent())
                .toList();

        // 5. Calculate clean documents
        Set<Long> defectiveDocIds = new HashSet<>(missingLexicalDocuments);
        defectiveDocIds.addAll(orphanedLexicalDocuments);
        for (EmbeddingConsistencyReport issue : embeddingIssues) {
            if (issue.documentId() != null) {
                defectiveDocIds.add(issue.documentId());
            }
        }

        int cleanCount = Math.max(0, allDocs.size() - defectiveDocIds.size());

        IndexIntegrityReport report = new IndexIntegrityReport(
                timestamp,
                allDocs.size(),
                activeDocs.size(),
                deletedDocs.size(),
                cleanCount,
                missingLexicalDocuments,
                orphanedLexicalDocuments,
                statisticsDiscrepancies,
                embeddingIssues
        );

        if (report.isHealthy()) {
            log.info("Index reconciliation audit passed: index is completely healthy ({} active documents).", activeDocs.size());
        } else {
            log.warn("Index reconciliation audit found {} defects: missingLexical={}, orphanedLexical={}, statsIssues={}, embeddingIssues={}",
                    report.totalIssueCount(),
                    missingLexicalDocuments.size(),
                    orphanedLexicalDocuments.size(),
                    statisticsDiscrepancies.size(),
                    embeddingIssues.size()
            );
        }

        return report;
    }
}
