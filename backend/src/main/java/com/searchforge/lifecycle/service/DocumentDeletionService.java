package com.searchforge.lifecycle.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Service managing safe, multi-tier document deletion across SearchForge.
 *
 * Guarantees:
 * 1. Immediate search exclusion via DELETING status lock.
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

    public DocumentDeletionService(CrawledDocumentRepository crawledDocumentRepository,
                                   IndexingEngine indexingEngine,
                                   DocumentEmbeddingService embeddingService) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.indexingEngine = Objects.requireNonNull(indexingEngine, "IndexingEngine cannot be null");
        this.embeddingService = Objects.requireNonNull(embeddingService, "DocumentEmbeddingService cannot be null");
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

        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            // Document row not found: clean up any orphaned postings or vectors defensively
            indexingEngine.removeDocument(documentId);
            embeddingService.deleteEmbedding(documentId);
            log.warn("Document ID {} not found in repository; cleaned orphaned index and vector entries", documentId);
            return DeletionResult.notFound(documentId);
        }

        CrawledDocument doc = docOpt.get();
        String url = doc.getUrl();

        try {
            // 1. Soft-lock: Mark document as DELETING (immediately hidden from search queries)
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
            doc.transitionTo(DocumentLifecycleStatus.FAILED);
            doc.setStatusMessage("Deletion failed: " + e.getMessage());
            crawledDocumentRepository.save(doc);
            return DeletionResult.failure(documentId, "Deletion error: " + e.getMessage());
        }
    }
}
