package com.searchforge.lifecycle;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.metrics.LifecycleMetricsCollector;
import com.searchforge.lifecycle.metrics.LifecycleSnapshot;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.repair.IndexRepairResult;
import com.searchforge.lifecycle.repair.IndexRepairService;
import com.searchforge.lifecycle.repair.RepairMode;
import com.searchforge.lifecycle.service.DeletionResult;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("Task 12: Comprehensive Index Lifecycle, Consistency & Recovery Integration Tests")
class IndexLifecycleIntegrationTest {

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private InvertedIndex inMemoryIndex;

    @Autowired
    private VectorStorage vectorStorage;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private TransactionalIndexingCoordinator indexingCoordinator;

    @Autowired
    private DocumentDeletionService deletionService;

    @Autowired
    private IndexIntegrityChecker integrityChecker;

    @Autowired
    private IndexRepairService repairService;

    @Autowired
    private LifecycleMetricsCollector metricsCollector;

    @Autowired
    private IndexedDocumentMetadataRepository metadataRepository;

    @Autowired
    private TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired(required = false)
    private ActiveDocumentFilter activeDocumentFilter;

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    void setUp() {
        inMemoryIndex.clear();
        vectorStorage.clear();
        if (activeDocumentFilter != null) {
            activeDocumentFilter.clear();
        }
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
        metricsCollector.reset();
    }

    @Test
    @DisplayName("End-to-End: Document lifecycle from creation to active indexing, verification, and searchability")
    void testFullDocumentLifecycle() {
        // 1. Discover document
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.org/docs/lifecycle",
                "searchforge.org",
                "Index Lifecycle in SearchForge",
                "SearchForge guarantees data consistency across inverted index and dense vectors.",
                "initialhash",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.save(doc);
        Long docId = doc.getId();

        // 2. Staged Indexing (outside and inside transaction)
        StagedIndexingResult result = indexingCoordinator.processDocument(
                docId, doc.getTitle(), doc.getCleanText()
        );

        assertEquals(StagedIndexingResult.Status.SUCCESS, result.status());
        assertEquals(1, result.version());

        // 3. Verify state across all layers
        CrawledDocument activeDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentLifecycleStatus.ACTIVE, activeDoc.getStatus());
        assertEquals(1, activeDoc.getVersion());
        assertTrue(activeDoc.isSearchable());

        // Lexical index presence
        assertTrue(inMemoryIndex.getDocLength(docId) > 0);
        Optional<PostingsList> postings = inMemoryIndex.getPostings("searchforg"); // stemmed
        assertTrue(postings.isPresent());
        assertTrue(postings.get().containsDocument(docId));

        // Vector storage & embedding entity presence
        assertTrue(vectorStorage.contains(docId));
        assertTrue(embeddingRepository.existsByDocumentId(docId));

        // 4. Verify full system integrity audit passes
        IndexIntegrityReport report = integrityChecker.checkIntegrity();
        assertTrue(report.isHealthy());
        assertEquals(0, report.totalIssueCount());
        assertEquals(1, report.activeDocumentCount());
        assertEquals(1, report.cleanDocuments());
    }

    @Test
    @DisplayName("Incremental Update: Content modification increments version and atomically replaces old index postings")
    void testIncrementalUpdateFlow() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.org/update-test",
                "searchforge.org",
                "Original Title",
                "Apples and Bananas fruit salad.",
                "hash1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.save(doc);
        Long docId = doc.getId();

        // Initial indexing: Version 1
        indexingCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());
        assertTrue(inMemoryIndex.getPostings("appl").orElseThrow().containsDocument(docId));

        // Modify content: Version 2
        String newTitle = "Updated Title";
        String newContent = "Carrots and Broccoli vegetable salad.";
        doc.setTitle(newTitle);
        doc.setCleanText(newContent);
        crawledDocumentRepository.save(doc);

        StagedIndexingResult updateResult = indexingCoordinator.processDocument(docId, newTitle, newContent);
        assertEquals(StagedIndexingResult.Status.SUCCESS, updateResult.status());
        assertEquals(2, updateResult.version());

        CrawledDocument updatedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertEquals(2, updatedDoc.getVersion());

        // Old terms ("appl", "banana") must be completely purged from index
        boolean hasAppl = inMemoryIndex.getPostings("appl").map(p -> p.containsDocument(docId)).orElse(false);
        boolean hasBanana = inMemoryIndex.getPostings("banana").map(p -> p.containsDocument(docId)).orElse(false);
        assertFalse(hasAppl);
        assertFalse(hasBanana);

        // New terms ("carrot", "broccoli") must be indexed
        assertTrue(inMemoryIndex.getPostings("carrot").orElseThrow().containsDocument(docId));
        assertTrue(inMemoryIndex.getPostings("broccoli").orElseThrow().containsDocument(docId));

        // System integrity remains 100% clean
        IndexIntegrityReport report = integrityChecker.checkIntegrity();
        assertTrue(report.isHealthy());
    }

    @Test
    @DisplayName("Idempotent Crawl: Re-processing identical content skips re-indexing and avoids wasted inference")
    void testIdempotentReprocessing() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.org/idempotent",
                "searchforge.org",
                "Static Title",
                "Static unchanging content.",
                "statichash",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.save(doc);
        Long docId = doc.getId();

        // First index
        indexingCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());

        // Second index with same content
        StagedIndexingResult skipResult = indexingCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());

        assertEquals(StagedIndexingResult.Status.UNCHANGED, skipResult.status());
        assertEquals(1, skipResult.version());
    }

    @Test
    @DisplayName("Deletion Flow: Document deletion immediately purges lexical postings, metadata, and vectors")
    void testDocumentDeletionFlow() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.org/delete-me",
                "searchforge.org",
                "Delete Test",
                "Temporary document to be deleted.",
                "delhash",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.save(doc);
        Long docId = doc.getId();

        indexingCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());
        assertTrue(inMemoryIndex.getDocLength(docId) > 0);
        assertTrue(vectorStorage.contains(docId));

        // Delete document (soft delete)
        DeletionResult delResult = deletionService.deleteDocument(docId, false);
        assertTrue(delResult.isSuccess());

        CrawledDocument deletedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentLifecycleStatus.DELETED, deletedDoc.getStatus());
        assertFalse(deletedDoc.isSearchable());

        // Must be purged from index and vector storage
        assertEquals(0, inMemoryIndex.getDocLength(docId));
        assertFalse(inMemoryIndex.getAllDocumentIds().contains(docId));
        assertFalse(vectorStorage.contains(docId));
        assertFalse(embeddingRepository.existsByDocumentId(docId));

        // Integrity audit passes with 0 issues
        IndexIntegrityReport report = integrityChecker.checkIntegrity();
        assertTrue(report.isHealthy());
        assertEquals(0, report.activeDocumentCount());
        assertEquals(1, report.deletedDocumentCount());
    }

    @Test
    @DisplayName("Recovery & Automated Repair: Corrupted index state is detected and repaired to 100% health")
    void testIntegrityAuditAndAutomatedRepair() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.org/repair-test",
                "searchforge.org",
                "Repair Test",
                "Important knowledge base article.",
                "hash1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.save(doc);
        Long docId = doc.getId();

        indexingCoordinator.processDocument(docId, doc.getTitle(), doc.getCleanText());

        // Artificially corrupt state: clear in-memory index
        inMemoryIndex.clear();

        // Audit must detect corruption
        IndexIntegrityReport corruptedReport = integrityChecker.checkIntegrity();
        assertFalse(corruptedReport.isHealthy());
        assertTrue(corruptedReport.missingLexicalDocuments().contains(docId));

        // Execute automated repair
        IndexRepairResult repairResult = repairService.executeRepair(RepairMode.REPAIR);
        assertTrue(repairResult.isCompletelyRepaired());
        assertEquals(1, repairResult.missingDocumentsReindexed());

        // Verify state is restored
        assertTrue(inMemoryIndex.getDocLength(docId) > 0);
        IndexIntegrityReport postReport = integrityChecker.checkIntegrity();
        assertTrue(postReport.isHealthy());
        assertEquals(0, postReport.totalIssueCount());
    }
}
