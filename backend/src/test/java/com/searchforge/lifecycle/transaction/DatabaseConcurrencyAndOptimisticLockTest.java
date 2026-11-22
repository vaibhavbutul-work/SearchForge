package com.searchforge.lifecycle.transaction;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.lock.DocumentLockManager;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DatabaseConcurrencyAndOptimisticLockTest {

    @Autowired
    private CrawledDocumentRepository repository;

    @Autowired
    private TransactionalIndexingCoordinator coordinator;

    @Autowired
    private DocumentLockManager lockManager;

    @Autowired
    private com.searchforge.indexer.repository.TermPostingRepository postingRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedTermRepository termRepository;

    @Autowired
    private com.searchforge.indexer.repository.IndexedDocumentMetadataRepository metadataRepository;

    @Autowired
    private com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository embeddingRepository;

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    void setUp() {
        embeddingRepository.deleteAll();
        postingRepository.deleteAll();
        termRepository.deleteAll();
        metadataRepository.deleteAll();
        repository.deleteAll();
        lockManager.clear();
    }

    @Test
    @DisplayName("Should throw ObjectOptimisticLockingFailureException when concurrent transaction modifies stale entity")
    void shouldThrowOptimisticLockExceptionOnStaleEntityUpdate() {
        // 1. Seed initial document
        CrawledDocument initial = new CrawledDocument(
                "https://example.com/optimistic-test",
                "example.com",
                "Original Title",
                "Original text content",
                "hash-original",
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                1
        );
        initial = repository.saveAndFlush(initial);
        Long docId = initial.getId();
        assertEquals(0L, initial.getVersionLock(), "Initial versionLock must be 0");

        // 2. Load two distinct entity instances from database
        CrawledDocument instanceA = repository.findById(docId).orElseThrow();
        CrawledDocument instanceB = repository.findById(docId).orElseThrow();

        // 3. Transaction A updates title and commits: versionLock advances to 1
        instanceA.setTitle("Title Updated by Transaction A");
        repository.saveAndFlush(instanceA);

        CrawledDocument afterA = repository.findById(docId).orElseThrow();
        assertEquals(1L, afterA.getVersionLock(), "versionLock must advance to 1 after Transaction A commits");

        // 4. Transaction B attempts to save using its stale instanceB (still holding versionLock = 0)
        instanceB.setTitle("Conflicting Title by Transaction B");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            repository.saveAndFlush(instanceB);
        }, "Saving stale entity instance must throw ObjectOptimisticLockingFailureException");
    }

    @Test
    @DisplayName("Should verify that coordinator handles staged indexing with version increment and clean commit")
    void shouldExecuteStagedIndexingWithCleanVersionIncrement() {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/staged-commit-test",
                "example.com",
                "V1 Title",
                "Version 1 body content",
                "hash-v1",
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                1
        );
        doc = repository.saveAndFlush(doc);
        Long docId = doc.getId();

        // Execute staged indexing for V2
        StagedIndexingResult result = coordinator.processDocument(
                docId,
                "V2 Title",
                "Version 2 updated content with new vocabulary terms"
        );

        assertTrue(result.isSuccess(), "Result failed with: " + result.message());
        assertEquals(2, result.version());

        CrawledDocument updated = repository.findById(docId).orElseThrow();
        assertEquals("V2 Title", updated.getTitle());
        assertEquals(2, updated.getVersion());
        assertEquals(DocumentLifecycleStatus.ACTIVE, updated.getStatus());
        assertTrue(updated.getVersionLock() > 0, "JPA version_lock must have incremented");
    }

    @Test
    @DisplayName("Should recognize identical content and short-circuit to UNCHANGED without database write")
    void shouldRecognizeIdenticalContentAndReturnUnchanged() {
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/idempotent-test",
                "example.com",
                "Identical Title",
                "Identical static content",
                "hash-pre",
                200,
                Instant.now(),
                DocumentLifecycleStatus.ACTIVE,
                1
        );
        doc = repository.saveAndFlush(doc);
        Long docId = doc.getId();

        // Run 1: sets content and real hash
        StagedIndexingResult res1 = coordinator.processDocument(docId, "Identical Title", "Identical static content");
        assertTrue(res1.isSuccess());

        CrawledDocument committed = repository.findById(docId).orElseThrow();
        Long versionLockBefore = committed.getVersionLock();

        // Run 2: exact same title and content
        StagedIndexingResult res2 = coordinator.processDocument(docId, "Identical Title", "Identical static content");
        assertTrue(res2.isUnchanged(), "Second run with identical content must be UNCHANGED");

        CrawledDocument afterRun2 = repository.findById(docId).orElseThrow();
        assertEquals(versionLockBefore, afterRun2.getVersionLock(),
                "versionLock must not increment when operation is short-circuited as UNCHANGED");
    }
}
