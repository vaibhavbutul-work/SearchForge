package com.searchforge.lifecycle.transaction;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingResult;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.lifecycle.change.ChangeDetectionResult;
import com.searchforge.lifecycle.change.ChangeType;
import com.searchforge.lifecycle.change.DocumentChangeDetector;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.lock.DocumentLockManager;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.VectorSerialization;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinator implementing staged two-phase transactional indexing.
 *
 * Guarantees:
 * 1. Expensive ONNX model inference is executed OUTSIDE database transactions.
 *    No database connection is held open during neural tensor calculation.
 * 2. Relational document updates, lexical postings swap, and vector entity persistence
 *    are committed inside a single atomic database transaction.
 * 3. Complete rollback on any database error; prevents split-brain state.
 * 4. Strictly idempotent: repeated processing of identical content skips execution
 *    without inflating term frequencies, document counts, or statistics.
 */
@Service
public class TransactionalIndexingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TransactionalIndexingCoordinator.class);
    private static final int MAX_REPRESENTATION_WORDS = 250;

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final DocumentChangeDetector changeDetector;
    private final Tokenizer lexicalTokenizer;
    private final EmbeddingModel embeddingModel;
    private final IndexingEngine indexingEngine;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final VectorStorage vectorStorage;
    private final HtmlParser htmlParser;
    private final DocumentLockManager documentLockManager;
    private final ActiveDocumentFilter activeDocumentFilter;

    @org.springframework.beans.factory.annotation.Autowired
    public TransactionalIndexingCoordinator(CrawledDocumentRepository crawledDocumentRepository,
                                            DocumentChangeDetector changeDetector,
                                            Tokenizer lexicalTokenizer,
                                            EmbeddingModel embeddingModel,
                                            IndexingEngine indexingEngine,
                                            DocumentEmbeddingRepository embeddingRepository,
                                            VectorStorage vectorStorage,
                                            HtmlParser htmlParser,
                                            DocumentLockManager documentLockManager,
                                            @org.springframework.beans.factory.annotation.Autowired(required = false) ActiveDocumentFilter activeDocumentFilter) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.changeDetector = Objects.requireNonNull(changeDetector, "DocumentChangeDetector cannot be null");
        this.lexicalTokenizer = Objects.requireNonNull(lexicalTokenizer, "Lexical Tokenizer cannot be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.indexingEngine = Objects.requireNonNull(indexingEngine, "IndexingEngine cannot be null");
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository, "DocumentEmbeddingRepository cannot be null");
        this.vectorStorage = Objects.requireNonNull(vectorStorage, "VectorStorage cannot be null");
        this.htmlParser = Objects.requireNonNull(htmlParser, "HtmlParser cannot be null");
        this.documentLockManager = documentLockManager != null ? documentLockManager : new DocumentLockManager();
        this.activeDocumentFilter = activeDocumentFilter;
    }

    public TransactionalIndexingCoordinator(CrawledDocumentRepository crawledDocumentRepository,
                                            DocumentChangeDetector changeDetector,
                                            Tokenizer lexicalTokenizer,
                                            EmbeddingModel embeddingModel,
                                            IndexingEngine indexingEngine,
                                            DocumentEmbeddingRepository embeddingRepository,
                                            VectorStorage vectorStorage,
                                            HtmlParser htmlParser,
                                            DocumentLockManager documentLockManager) {
        this(crawledDocumentRepository, changeDetector, lexicalTokenizer, embeddingModel, indexingEngine,
                embeddingRepository, vectorStorage, htmlParser, documentLockManager, null);
    }

    public TransactionalIndexingCoordinator(CrawledDocumentRepository crawledDocumentRepository,
                                            DocumentChangeDetector changeDetector,
                                            Tokenizer lexicalTokenizer,
                                            EmbeddingModel embeddingModel,
                                            IndexingEngine indexingEngine,
                                            DocumentEmbeddingRepository embeddingRepository,
                                            VectorStorage vectorStorage,
                                            HtmlParser htmlParser) {
        this(crawledDocumentRepository, changeDetector, lexicalTokenizer, embeddingModel, indexingEngine,
                embeddingRepository, vectorStorage, htmlParser, new DocumentLockManager(), null);
    }

    /**
     * Executes the staged indexing workflow:
     * Stage 1: CPU-bound tokenization and ONNX embedding (OUTSIDE database transaction).
     * Stage 2: Atomic database commit of document, lexical postings, and embedding entity.
     * Stage 3: In-memory index and vector storage synchronization.
     */
    public StagedIndexingResult processDocument(Long documentId, String title, String content) {
        if (documentId == null) {
            return StagedIndexingResult.failure(null, "Document ID cannot be null", 0);
        }

        // Mutual exclusion per document ID to prevent lost updates and race conditions
        return documentLockManager.withLock(documentId, () -> executeStagedIndexing(documentId, title, content));
    }

    private StagedIndexingResult executeStagedIndexing(Long documentId, String title, String content) {
        long startTime = System.currentTimeMillis();

        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return StagedIndexingResult.failure(documentId, "Document not found: " + documentId, 0);
        }

        CrawledDocument doc = docOpt.get();

        // Terminal state guard: cannot index if document is undergoing deletion
        if (doc.getStatus() == DocumentLifecycleStatus.DELETING || doc.getStatus() == DocumentLifecycleStatus.DELETED) {
            log.warn("Cannot index document {}: currently in terminal/deleting state {}", documentId, doc.getStatus());
            return StagedIndexingResult.failure(documentId, "Document is in terminal state: " + doc.getStatus(), 0);
        }

        String newHash = htmlParser.computeSha256(content != null ? content : "");

        // Idempotency: Skip if unchanged
        if (Objects.equals(doc.getContentHash(), newHash) && Objects.equals(doc.getTitle(), title)
                && doc.getStatus() == DocumentLifecycleStatus.ACTIVE) {
            if (activeDocumentFilter != null) {
                activeDocumentFilter.markActive(documentId);
            }
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Document {} is identical to current version; skipping staged indexing", documentId);
            return StagedIndexingResult.unchanged(documentId, doc.getVersion(), duration);
        }

        int targetVersion = doc.getStatus() == DocumentLifecycleStatus.ACTIVE
                ? doc.getVersion() + 1
                : (doc.getVersion() > 0 ? doc.getVersion() : 1);

        // ====================================================================
        // STAGE 1: Pre-Transaction Computation (CPU Bound, Zero Database Locks)
        // ====================================================================
        List<ProcessedToken> tokens;
        float[] vector;
        byte[] binaryVector;

        try {
            // A. Lexical linguistic pipeline
            String textToTokenize = (title != null ? title + " " : "") + (content != null ? content : "");
            tokens = lexicalTokenizer.tokenize(textToTokenize);

            // B. Dense ONNX embedding generation (OUTSIDE DB TX)
            String representation = buildLeadRepresentation(title, content);
            vector = embeddingModel.embed(representation);
            binaryVector = VectorSerialization.toByteArray(vector);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Stage 1 computation failed for document {}: {}", documentId, e.getMessage(), e);
            recordProcessingFailure(documentId, "Pre-transaction computation error: " + e.getMessage());
            return StagedIndexingResult.failure(documentId, "Stage 1 error: " + e.getMessage(), duration);
        }

        // ====================================================================
        // STAGE 2: Atomic Transactional Commit with Optimistic Conflict Recovery
        // ====================================================================
        boolean committed = false;
        int maxCommitAttempts = 3;
        for (int attempt = 1; attempt <= maxCommitAttempts; attempt++) {
            try {
                commitRelationalState(documentId, title, content, newHash, targetVersion, binaryVector);
                committed = true;
                break;
            } catch (Exception e) {
                boolean isOptimisticLock = isOptimisticConflict(e);
                if (isOptimisticLock && attempt < maxCommitAttempts) {
                    log.warn("Optimistic locking conflict committing doc {} (attempt {}/{}). Refreshing and retrying...",
                            documentId, attempt, maxCommitAttempts);
                    // Refresh entity from database
                    Optional<CrawledDocument> refreshedOpt = crawledDocumentRepository.findById(documentId);
                    if (refreshedOpt.isPresent()) {
                        CrawledDocument freshDoc = refreshedOpt.get();
                        if (freshDoc.getStatus() == DocumentLifecycleStatus.DELETING || freshDoc.getStatus() == DocumentLifecycleStatus.DELETED) {
                            long duration = System.currentTimeMillis() - startTime;
                            log.info("Document {} transitioned to {} during concurrent processing; aborting commit cleanly", documentId, freshDoc.getStatus());
                            return StagedIndexingResult.failure(documentId, "Document deleted concurrently: " + freshDoc.getStatus(), duration);
                        }
                        // Idempotency check: did competing transaction commit identical content?
                        if (Objects.equals(freshDoc.getContentHash(), newHash) && freshDoc.getStatus() == DocumentLifecycleStatus.ACTIVE) {
                            long duration = System.currentTimeMillis() - startTime;
                            log.info("Document {} already committed with identical content by competing transaction; returning UNCHANGED", documentId);
                            return StagedIndexingResult.unchanged(documentId, freshDoc.getVersion(), duration);
                        }
                        targetVersion = freshDoc.getVersion() + 1;
                    }
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("Stage 2 atomic commit failed for document {}: {}", documentId, e.getMessage());
                    // Check if failure is due to concurrent deletion
                    Optional<CrawledDocument> currentOpt = crawledDocumentRepository.findById(documentId);
                    boolean isDeletedOrTerminal = currentOpt.map(d -> d.getStatus() == DocumentLifecycleStatus.DELETING || d.getStatus() == DocumentLifecycleStatus.DELETED).orElse(true);
                    if (!isDeletedOrTerminal) {
                        recordProcessingFailure(documentId, "Database transaction error: " + e.getMessage());
                    }
                    return StagedIndexingResult.failure(documentId, "Stage 2 error: " + e.getMessage(), duration);
                }
            }
        }

        if (!committed) {
            long duration = System.currentTimeMillis() - startTime;
            return StagedIndexingResult.failure(documentId, "Stage 2 failed: exhausted optimistic lock retries", duration);
        }

        // ====================================================================
        // STAGE 3: In-Memory Synchronization (Post-Commit) with Defensive Rollback Guard
        // ====================================================================
        boolean vectorSynced = false;
        String syncErrorMessage = null;
        int maxVectorSyncAttempts = 2;

        for (int attempt = 1; attempt <= maxVectorSyncAttempts; attempt++) {
            try {
                vectorStorage.put(new DocumentVector(documentId, vector));
                vectorSynced = true;
                break;
            } catch (Exception e) {
                syncErrorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Stage 3 vector memory sync attempt {}/{} failed for document {}: {}",
                        attempt, maxVectorSyncAttempts, documentId, syncErrorMessage);
                if (attempt < maxVectorSyncAttempts) {
                    try {
                        Thread.sleep(25L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!vectorSynced) {
            // Defensive Rollback: Purge any partial/stale entry in vector storage to prevent split-brain reads
            try {
                vectorStorage.remove(documentId);
            } catch (Exception re) {
                log.error("Failed to defensively purge document {} from vector storage: {}", documentId, re.getMessage());
            }

            if (activeDocumentFilter != null) {
                activeDocumentFilter.markInactive(documentId);
            }

            // Flag the document in DB with explicit transactional alert and DESYNCHRONIZED status message
            flagVectorDesynchronization(documentId, syncErrorMessage);

            long duration = System.currentTimeMillis() - startTime;
            log.error("TRANSACTIONAL_ALERT: Stage 3 vector memory synchronization failed for document {} (version {}). " +
                    "Relational commit succeeded, but in-memory vector storage was purged to prevent split-brain. Error: {}",
                    documentId, targetVersion, syncErrorMessage);

            return StagedIndexingResult.partialSuccess(
                    documentId,
                    targetVersion,
                    tokens.size(),
                    "Stage 2 relational commit succeeded, but Stage 3 vector memory sync failed: " + syncErrorMessage,
                    duration
            );
        }

        if (activeDocumentFilter != null) {
            activeDocumentFilter.markActive(documentId);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully completed staged indexing for doc {} (version {}): {} tokens in {} ms",
                documentId, targetVersion, tokens.size(), duration);

        return StagedIndexingResult.success(documentId, targetVersion, tokens.size(), duration);
    }

    /**
     * Flags a document entity with desynchronization alert metadata when Stage 3 memory sync fails.
     */
    @Transactional
    public void flagVectorDesynchronization(Long documentId, String errorMessage) {
        crawledDocumentRepository.findById(documentId).ifPresent(doc -> {
            try {
                doc.setStatusMessage("DESYNCHRONIZED: Vector memory sync failed: " + errorMessage);
                crawledDocumentRepository.save(doc);
            } catch (Exception e) {
                log.error("Failed to flag vector desynchronization on document {}: {}", documentId, e.getMessage());
            }
        });
    }

    /**
     * Direct zero-inference reconciliation of vector storage from committed relational state.
     * Restores dense vector in memory from DocumentEmbeddingEntity without re-running tokenization or ONNX models.
     */
    public boolean reconcileDocumentVector(Long documentId) {
        if (documentId == null) {
            return false;
        }
        Optional<DocumentEmbeddingEntity> entityOpt = embeddingRepository.findByDocumentId(documentId);
        if (entityOpt.isEmpty()) {
            return false;
        }

        try {
            DocumentEmbeddingEntity entity = entityOpt.get();
            float[] coords = VectorSerialization.fromByteArray(entity.getVectorData());
            vectorStorage.put(new DocumentVector(documentId, coords));

            crawledDocumentRepository.findById(documentId).ifPresent(doc -> {
                if (doc.getStatusMessage() != null && doc.getStatusMessage().startsWith("DESYNCHRONIZED")) {
                    doc.setStatusMessage(null);
                    crawledDocumentRepository.save(doc);
                }
            });
            if (activeDocumentFilter != null) {
                activeDocumentFilter.markActive(documentId);
            }
            log.info("Successfully reconciled vector storage for document {} from committed DB embedding", documentId);
            return true;
        } catch (Exception e) {
            log.error("Manual vector reconciliation failed for document {}: {}", documentId, e.getMessage());
            return false;
        }
    }

    /**
     * Stage 2: Atomic relational commit wrapped in a Spring database transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void commitRelationalState(Long documentId, String title, String content,
                                      String contentHash, int targetVersion, byte[] binaryVector) {
        // 1. Update CrawledDocument entity
        CrawledDocument doc = crawledDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document missing during commit: " + documentId));

        if (doc.getStatus() == DocumentLifecycleStatus.DELETING || doc.getStatus() == DocumentLifecycleStatus.DELETED) {
            throw new IllegalStateException("Cannot commit document " + documentId + ": currently in deleting state " + doc.getStatus());
        }

        if (doc.getStatus() != DocumentLifecycleStatus.PROCESSING) {
            doc.transitionTo(DocumentLifecycleStatus.PROCESSING);
        }

        doc.setTitle(title);
        doc.setCleanText(content);
        doc.setContentHash(contentHash);
        doc.setVersion(targetVersion);

        // 2. Lexical and positional inverted index atomic replacement
        IndexingResult indexingResult = indexingEngine.indexDocument(documentId, title, content);
        if (!indexingResult.success()) {
            throw new IllegalStateException("Lexical indexing failed during transaction: " + indexingResult.errorMessage());
        }

        // 3. Upsert version-locked DocumentEmbeddingEntity
        Optional<DocumentEmbeddingEntity> existingOpt = embeddingRepository.findByDocumentId(documentId);
        DocumentEmbeddingEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setVectorData(binaryVector);
            entity.setContentHash(contentHash);
            entity.setDocumentVersion(targetVersion);
        } else {
            entity = new DocumentEmbeddingEntity(
                    documentId,
                    embeddingModel.modelName(),
                    embeddingModel.dimensions(),
                    binaryVector,
                    contentHash,
                    targetVersion
            );
        }
        embeddingRepository.save(entity);

        // 4. Mark ACTIVE
        doc.transitionTo(DocumentLifecycleStatus.ACTIVE);
        doc.setLastProcessedAt(Instant.now());
        doc.setStatusMessage(null);
        crawledDocumentRepository.save(doc);
    }

    /**
     * Non-transactional failure recorder to persist error status without rolling back error logs.
     */
    @Transactional
    public void recordProcessingFailure(Long documentId, String errorMessage) {
        if (activeDocumentFilter != null) {
            activeDocumentFilter.markInactive(documentId);
        }
        crawledDocumentRepository.findById(documentId).ifPresent(doc -> {
            if (doc.getStatus() == DocumentLifecycleStatus.DELETING
                    || doc.getStatus() == DocumentLifecycleStatus.DELETED
                    || doc.getStatus() == DocumentLifecycleStatus.FAILED) {
                log.debug("Document {} is already in terminal state {}; skipping transition to FAILED", documentId, doc.getStatus());
                return;
            }
            try {
                doc.transitionTo(DocumentLifecycleStatus.FAILED);
                doc.setStatusMessage(errorMessage);
                crawledDocumentRepository.save(doc);
            } catch (Exception e) {
                log.warn("Could not transition doc {} to FAILED: {}", documentId, e.getMessage());
            }
        });
    }

    private String buildLeadRepresentation(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim()).append(". ");
        }
        if (content != null && !content.isBlank()) {
            String[] words = content.trim().split("\\s+");
            int limit = Math.min(words.length, MAX_REPRESENTATION_WORDS);
            for (int i = 0; i < limit; i++) {
                sb.append(words[i]).append(" ");
            }
        }
        String rep = sb.toString().trim();
        return rep.isEmpty() ? "empty document" : rep;
    }

    private boolean isOptimisticConflict(Throwable t) {
        if (t == null) return false;
        if (t instanceof org.springframework.dao.ConcurrencyFailureException
                || t instanceof jakarta.persistence.OptimisticLockException
                || t instanceof org.springframework.transaction.TransactionException) {
            return true;
        }
        String msg = t.getMessage() != null ? t.getMessage().toLowerCase(Locale.ROOT) : "";
        if (msg.contains("optimistic")
                || msg.contains("deadlock")
                || msg.contains("lock")
                || msg.contains("rollback-only")
                || msg.contains("timeout")
                || msg.contains("row was updated or deleted by another transaction")) {
            return true;
        }
        if (t.getCause() != null && t.getCause() != t) {
            return isOptimisticConflict(t.getCause());
        }
        return false;
    }
}
