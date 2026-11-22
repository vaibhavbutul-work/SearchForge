package com.searchforge.lifecycle.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.lock.DocumentLockManager;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Service managing safe, multi-tier document deletion across SearchForge.
 *
 * Guarantees:
 * 1. Immediate search exclusion via DELETING status lock and ActiveDocumentFilter.
 * 2. Purges lexical postings, positional offsets, and metadata from database and RAM.
 * 3. Updates BM25 corpus statistics (N, avgdl, df, ctf).
 * 4. Purges dense vector embedding from database and in-memory VectorStorage.
 * 5. Supports both permanent hard deletion and recoverable soft deletion.
 * 6. Preserves database referential integrity and cleans orphaned data.
 */
@Service
public class DocumentDeletionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionService.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final IndexingEngine indexingEngine;
    private final DocumentEmbeddingService embeddingService;
    private final DocumentLockManager documentLockManager;
    private final ActiveDocumentFilter activeDocumentFilter;

    @Autowired
    public DocumentDeletionService(CrawledDocumentRepository crawledDocumentRepository,
                                   IndexingEngine indexingEngine,
                                   DocumentEmbeddingService embeddingService,
                                   DocumentLockManager documentLockManager,
                                   @Autowired(required = false) ActiveDocumentFilter activeDocumentFilter) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.indexingEngine = Objects.requireNonNull(indexingEngine, "IndexingEngine cannot be null");
        this.embeddingService = Objects.requireNonNull(embeddingService, "DocumentEmbeddingService cannot be null");
        this.documentLockManager = documentLockManager != null ? documentLockManager : new DocumentLockManager();
        this.activeDocumentFilter = activeDocumentFilter;
    }

    public DocumentDeletionService(CrawledDocumentRepository crawledDocumentRepository,
                                   IndexingEngine indexingEngine,
                                   DocumentEmbeddingService embeddingService,
                                   DocumentLockManager documentLockManager) {
        this(crawledDocumentRepository, indexingEngine, embeddingService, documentLockManager, null);
    }

    public DocumentDeletionService(CrawledDocumentRepository crawledDocumentRepository,
                                   IndexingEngine indexingEngine,
                                   DocumentEmbeddingService embeddingService) {
        this(crawledDocumentRepository, indexingEngine, embeddingService, new DocumentLockManager(), null);
    }

    /**
     * Executes complete document deletion with default hard deletion.
     */
    @Transactional
    public DeletionResult deleteDocument(Long documentId) {
        return deleteDocument(documentId, true);
    }

    /**
     * Deletes a document across all storage tiers with configurable hard or soft deletion.
     *
     * @param documentId Document ID to delete.
     * @param hardDelete If true, deletes CrawledDocument row; if false, marks status as DELETED.
     */
    @Transactional
    public DeletionResult deleteDocument(Long documentId, boolean hardDelete) {
        if (documentId == null) {
            return DeletionResult.failure(null, "Document ID cannot be null");
        }

        return documentLockManager.withLock(documentId, () -> executeDeletion(documentId, hardDelete));
    }

    private DeletionResult executeDeletion(Long documentId, boolean hardDelete) {
        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            if (activeDocumentFilter != null) {
                activeDocumentFilter.markInactive(documentId);
            }
            // Document row not found: clean up any orphaned postings or vectors defensively
            indexingEngine.removeDocument(documentId);
            embeddingService.deleteEmbedding(documentId);
            log.warn("Document ID {} not found in repository; cleaned orphaned index and vector entries", documentId);
            return DeletionResult.notFound(documentId);
        }

        CrawledDocument doc = docOpt.get();
        String url = doc.getUrl();

        // Idempotency: If document is already DELETED or DELETING, return cleanly
        if (doc.getStatus() == DocumentLifecycleStatus.DELETED) {
            log.debug("Document ID {} is already in terminal DELETED state; skipping duplicate deletion", documentId);
            return DeletionResult.success(documentId, url, hardDelete);
        }
        if (doc.getStatus() == DocumentLifecycleStatus.DELETING) {
            log.debug("Document ID {} is already in DELETING state", documentId);
            return DeletionResult.success(documentId, url, hardDelete);
        }

        try {
            // 1. Soft-lock: Mark document as DELETING (immediately hidden from search queries)
            if (activeDocumentFilter != null) {
                activeDocumentFilter.markInactive(documentId);
            }
            doc.transitionTo(DocumentLifecycleStatus.DELETING);
            crawledDocumentRepository.save(doc);
            crawledDocumentRepository.flush();

            // 2. Remove from lexical and positional inverted index
            // Purges postings, updates term df/ctf stats, deletes document metadata,
            // and decrements in-memory docLengths and totalTokens.
            indexingEngine.removeDocument(documentId);

            // 3. Remove dense semantic embedding
            // Purges from in-memory VectorStorage and deletes DocumentEmbeddingEntity.
            embeddingService.deleteEmbedding(documentId);

            // 4. Finalize deletion
            if (hardDelete) {
                crawledDocumentRepository.delete(doc);
                crawledDocumentRepository.flush();
                log.info("Permanently hard-deleted document ID {} ({}) across all tiers", documentId, url);
            } else {
                doc.transitionTo(DocumentLifecycleStatus.DELETED);
                crawledDocumentRepository.save(doc);
                log.info("Soft-deleted document ID {} ({}) and purged all search representations", documentId, url);
            }

            return DeletionResult.success(documentId, url, hardDelete);
        } catch (Exception e) {
            log.error("Failed to delete document ID {}: {}", documentId, e.getMessage(), e);
            try {
                if (doc.getStatus() != DocumentLifecycleStatus.DELETED && doc.getStatus() != DocumentLifecycleStatus.DELETING) {
                    doc.transitionTo(DocumentLifecycleStatus.FAILED);
                }
                doc.setStatusMessage("Deletion failed: " + e.getMessage());
                crawledDocumentRepository.save(doc);
            } catch (Exception ex) {
                log.warn("Could not transition doc {} to FAILED during deletion error: {}", documentId, ex.getMessage());
            }
            return DeletionResult.failure(documentId, "Deletion error: " + e.getMessage());
        }
    }
}
