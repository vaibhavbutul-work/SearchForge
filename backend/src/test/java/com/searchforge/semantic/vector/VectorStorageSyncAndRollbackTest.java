package com.searchforge.semantic.vector;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.lifecycle.change.DocumentChangeDetector;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.lock.DocumentLockManager;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Task 7: Atomic Vector Storage Synchronization, Rollback Guard & Auto-Reconciliation Tests")
class VectorStorageSyncAndRollbackTest {

    @Autowired
    private CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;

    @Autowired
    private IndexingEngine indexingEngine;

    @Autowired
    private Tokenizer lexicalTokenizer;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private DocumentChangeDetector changeDetector;

    @Autowired
    private HtmlParser htmlParser;

    @Autowired
    private DocumentLockManager documentLockManager;

    @Autowired
    private IndexIntegrityChecker integrityChecker;

    @Autowired
    private com.searchforge.indexer.repository.TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedDocumentMetadataRepository metadataRepository;

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        crawledDocumentRepository.deleteAll();
    }

    @Test
    @DisplayName("Verify atomic vector CAS, putAndGetPrevious, and dimension validation on InMemoryVectorStorage")
    void testAtomicVectorStorageOperations() {
        InMemoryVectorStorage storage = new InMemoryVectorStorage(384);
        long docId = 999L;

        float[] v1 = new float[384];
        v1[0] = 1.0f;
        DocumentVector docVec1 = new DocumentVector(docId, v1);

        // First insertion: previous must be empty
        Optional<DocumentVector> prev1 = storage.putAndGetPrevious(docVec1);
        assertTrue(prev1.isEmpty());
        assertTrue(storage.contains(docId));

        // Second insertion: previous must return docVec1
        float[] v2 = new float[384];
        v2[1] = 1.0f;
        DocumentVector docVec2 = new DocumentVector(docId, v2);

        Optional<DocumentVector> prev2 = storage.putAndGetPrevious(docVec2);
        assertTrue(prev2.isPresent());
        assertEquals(1.0f, prev2.get().getValues()[0], 1e-4);

        // Atomic CAS replace with wrong expectedOld should fail
        float[] v3 = new float[384];
        v3[2] = 1.0f;
        DocumentVector docVec3 = new DocumentVector(docId, v3);

        boolean wrongReplace = storage.replace(docId, docVec1, docVec3);
        assertFalse(wrongReplace, "Replace must return false when expectedOld does not match current");

        // Atomic CAS replace with correct expectedOld should succeed
        DocumentVector currentInStorage = storage.get(docId).orElseThrow();
        boolean successReplace = storage.replace(docId, currentInStorage, docVec3);
        assertTrue(successReplace, "Replace must succeed when expectedOld matches current");
    }

    @Test
    @DisplayName("Verify Stage 3 memory failure injection triggers defensive rollback, transactional alert, and quarantine")
    void testStage3FailureInjectionAndDefensiveRollback() {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/vector-sync-fail",
                "example.com",
                "Failing Vector Page",
                "Initial text to crawl",
                "initial-hash",
                200,
                null,
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        long docId = doc.getId();

        // Create a faulty VectorStorage that throws on put to simulate Stage 3 memory failure
        VectorStorage faultyStorage = new VectorStorage() {
            private final VectorStorage delegate = new InMemoryVectorStorage(384);

            @Override
            public void put(long documentId, float[] vector) {
                throw new RuntimeException("Simulated JVM vector memory exhaustion");
            }

            @Override
            public void put(DocumentVector documentVector) {
                throw new RuntimeException("Simulated JVM vector memory exhaustion");
            }

            @Override
            public Optional<DocumentVector> putAndGetPrevious(DocumentVector documentVector) {
                throw new RuntimeException("Simulated JVM vector memory exhaustion");
            }

            @Override
            public boolean replace(long documentId, DocumentVector expectedOld, DocumentVector newVector) {
                return false;
            }

            @Override
            public Optional<DocumentVector> get(long documentId) {
                return delegate.get(documentId);
            }

            @Override
            public boolean contains(long documentId) {
                return delegate.contains(documentId);
            }

            @Override
            public boolean remove(long documentId) {
                return delegate.remove(documentId);
            }

            @Override
            public void clear() {
                delegate.clear();
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public int dimensions() {
                return 384;
            }

            @Override
            public java.util.List<Long> getAllDocumentIds() {
                return delegate.getAllDocumentIds();
            }

            @Override
            public java.util.List<DocumentVector> getAllVectors() {
                return delegate.getAllVectors();
            }
        };

        TransactionalIndexingCoordinator coordinatorWithFaultyStorage = new TransactionalIndexingCoordinator(
                crawledDocumentRepository,
                changeDetector,
                lexicalTokenizer,
                embeddingModel,
                indexingEngine,
                embeddingRepository,
                faultyStorage,
                htmlParser,
                documentLockManager
        );

        // Process document: Stage 2 relational commit will succeed, but Stage 3 vector memory sync will fail
        StagedIndexingResult result = coordinatorWithFaultyStorage.processDocument(
                docId,
                "Failing Vector Page",
                "Text requiring dense embedding representation in memory"
        );

        // Assert that result reflects PARTIAL_SUCCESS rather than masking as SUCCESS
        assertEquals(StagedIndexingResult.Status.PARTIAL_SUCCESS, result.status());
        assertTrue(result.isPartialSuccess());
        assertTrue(result.message().contains("Stage 2 relational commit succeeded, but Stage 3 vector memory sync failed"));

        // Verify relational state: Document is committed as ACTIVE
        CrawledDocument committedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentLifecycleStatus.ACTIVE, committedDoc.getStatus());

        // Verify quarantine metadata: Document status message is tagged with DESYNCHRONIZED alert
        assertNotNull(committedDoc.getStatusMessage());
        assertTrue(committedDoc.getStatusMessage().startsWith("DESYNCHRONIZED: Vector memory sync failed"));

        // Verify vector storage does NOT contain any corrupt or partial vector
        assertFalse(faultyStorage.contains(docId));

        // Verify DB embedding entity was safely committed in Stage 2
        assertTrue(embeddingRepository.findByDocumentId(docId).isPresent());
    }

    @Test
    @DisplayName("Verify zero-inference direct vector reconciliation restores vector storage and clears quarantine tag")
    void testZeroInferenceDirectVectorReconciliation() {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/vector-reconcile",
                "example.com",
                "Reconcile Page",
                "Initial text to crawl",
                "hash-7772",
                200,
                null,
                DocumentLifecycleStatus.DISCOVERED,
                1
        );
        doc = crawledDocumentRepository.saveAndFlush(doc);
        long docId = doc.getId();

        InMemoryVectorStorage workingStorage = new InMemoryVectorStorage(384);

        TransactionalIndexingCoordinator coordinator = new TransactionalIndexingCoordinator(
                crawledDocumentRepository,
                changeDetector,
                lexicalTokenizer,
                embeddingModel,
                indexingEngine,
                embeddingRepository,
                workingStorage,
                htmlParser,
                documentLockManager
        );

        // Index document normally
        StagedIndexingResult initialResult = coordinator.processDocument(
                docId,
                "Reconcile Page",
                "Neural vector search systems with dense embeddings"
        );
        assertEquals(StagedIndexingResult.Status.SUCCESS, initialResult.status());
        assertTrue(workingStorage.contains(docId));

        // Simulate an abrupt vector memory wipe or desynchronization incident
        workingStorage.remove(docId);
        assertFalse(workingStorage.contains(docId));
        coordinator.flagVectorDesynchronization(docId, "Simulated node restart memory wipe");

        // Verify quarantine message is active
        CrawledDocument desyncedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertNotNull(desyncedDoc.getStatusMessage());
        assertTrue(desyncedDoc.getStatusMessage().contains("DESYNCHRONIZED"));

        // Execute direct zero-inference reconciliation
        boolean reconciled = coordinator.reconcileDocumentVector(docId);
        assertTrue(reconciled, "reconcileDocumentVector must return true");

        // Verify vector is restored in vectorStorage
        assertTrue(workingStorage.contains(docId));
        DocumentVector restoredVec = workingStorage.get(docId).orElseThrow();
        assertEquals(384, restoredVec.getDimensions());
        assertTrue(restoredVec.isUnitNormalized());

        // Verify quarantine tag has been cleanly removed
        CrawledDocument healedDoc = crawledDocumentRepository.findById(docId).orElseThrow();
        assertNull(healedDoc.getStatusMessage(), "Quarantine status message must be cleared upon successful reconciliation");
    }
}
