package com.searchforge.lifecycle.integrity;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.lifecycle.consistency.DocumentEmbeddingConsistencyValidator;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyStatus;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 7: Index Reconciliation & Integrity Checker Unit Tests")
class IndexIntegrityCheckerTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private InvertedIndex inMemoryIndex;

    @Mock
    private IndexedDocumentMetadataRepository metadataRepository;

    @Mock
    private DocumentEmbeddingConsistencyValidator embeddingValidator;

    private IndexIntegrityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new IndexIntegrityChecker(
                crawledDocumentRepository,
                inMemoryIndex,
                metadataRepository,
                embeddingValidator
        );
    }

    @Test
    @DisplayName("Healthy index returns clean report with isHealthy true")
    void testHealthyIndexReport() {
        Long docId = 1L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/1", "example.com", "Title", "Content", "hash1", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findAll()).thenReturn(List.of(doc));
        when(inMemoryIndex.getDocLength(docId)).thenReturn(10);
        when(metadataRepository.existsByDocumentId(docId)).thenReturn(true);
        when(inMemoryIndex.getAllDocumentIds()).thenReturn(List.of(docId));
        when(inMemoryIndex.getTotalDocuments()).thenReturn(1);
        when(inMemoryIndex.getAverageDocumentLength()).thenReturn(10.0);

        EmbeddingConsistencyReport healthyEmbedding = new EmbeddingConsistencyReport(
                docId, EmbeddingConsistencyStatus.CURRENT, 1, 1, "hash1", "hash1",
                "all-MiniLM-L6-v2", "all-MiniLM-L6-v2", 384, 384, true, true, "OK"
        );
        when(embeddingValidator.validateAll()).thenReturn(List.of(healthyEmbedding));

        IndexIntegrityReport report = checker.checkIntegrity();

        assertNotNull(report);
        assertTrue(report.isHealthy());
        assertEquals(0, report.totalIssueCount());
        assertEquals(1, report.activeDocumentCount());
        assertEquals(1, report.cleanDocuments());
    }

    @Test
    @DisplayName("Active document missing from lexical index is flagged in missingLexicalDocuments")
    void testActiveDocumentMissingLexicalPostings() {
        Long docId = 2L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/2", "example.com", "Title", "Content", "hash2", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findAll()).thenReturn(List.of(doc));
        // Missing from in-memory index
        when(inMemoryIndex.getDocLength(docId)).thenReturn(0);
        when(metadataRepository.existsByDocumentId(docId)).thenReturn(false);
        when(inMemoryIndex.getAllDocumentIds()).thenReturn(List.of());
        when(inMemoryIndex.getTotalDocuments()).thenReturn(0);
        when(embeddingValidator.validateAll()).thenReturn(List.of());

        IndexIntegrityReport report = checker.checkIntegrity();

        assertFalse(report.isHealthy());
        assertTrue(report.missingLexicalDocuments().contains(docId));
        assertFalse(report.statisticsDiscrepancies().isEmpty());
    }

    @Test
    @DisplayName("Residual postings in index for deleted or non-existent document are flagged as orphaned")
    void testOrphanedLexicalPostings() {
        when(crawledDocumentRepository.findAll()).thenReturn(List.of());
        when(inMemoryIndex.getAllDocumentIds()).thenReturn(List.of(999L));
        when(inMemoryIndex.getTotalDocuments()).thenReturn(1);
        when(embeddingValidator.validateAll()).thenReturn(List.of());

        IndexIntegrityReport report = checker.checkIntegrity();

        assertFalse(report.isHealthy());
        assertTrue(report.orphanedLexicalDocuments().contains(999L));
    }

    @Test
    @DisplayName("Corpus statistics drift between index and active documents is flagged")
    void testCorpusStatisticsDrift() {
        Long docId = 1L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/1", "example.com", "Title", "Content", "hash1", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findAll()).thenReturn(List.of(doc));
        when(inMemoryIndex.getDocLength(docId)).thenReturn(100);
        when(metadataRepository.existsByDocumentId(docId)).thenReturn(true);
        when(inMemoryIndex.getAllDocumentIds()).thenReturn(List.of(docId));
        when(inMemoryIndex.getTotalDocuments()).thenReturn(1);
        // Drastically incorrect average document length in index
        when(inMemoryIndex.getAverageDocumentLength()).thenReturn(10.0);
        when(embeddingValidator.validateAll()).thenReturn(List.of());

        IndexIntegrityReport report = checker.checkIntegrity();

        assertFalse(report.isHealthy());
        assertEquals(1, report.statisticsDiscrepancies().size());
        assertTrue(report.statisticsDiscrepancies().get(0).contains("Average document length discrepancy"));
    }
}
