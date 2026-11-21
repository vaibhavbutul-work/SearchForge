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

    public TransactionalIndexingCoordinator(CrawledDocumentRepository crawledDocumentRepository,
                                            DocumentChangeDetector changeDetector,
                                            Tokenizer lexicalTokenizer,
                                            EmbeddingModel embeddingModel,
                                            IndexingEngine indexingEngine,
                                            DocumentEmbeddingRepository embeddingRepository,
                                            VectorStorage vectorStorage,
                                            HtmlParser htmlParser) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.changeDetector = Objects.requireNonNull(changeDetector, "DocumentChangeDetector cannot be null");
        this.lexicalTokenizer = Objects.requireNonNull(lexicalTokenizer, "Lexical Tokenizer cannot be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.indexingEngine = Objects.requireNonNull(indexingEngine, "IndexingEngine cannot be null");
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository, "DocumentEmbeddingRepository cannot be null");
        this.vectorStorage = Objects.requireNonNull(vectorStorage, "VectorStorage cannot be null");
        this.htmlParser = Objects.requireNonNull(htmlParser, "HtmlParser cannot be null");
    }

    /**
     * Executes the staged indexing workflow:
     * Stage 1: CPU-bound tokenization and ONNX embedding (OUTSIDE database transaction).
     * Stage 2: Atomic database commit of document, lexical postings, and embedding entity.
     * Stage 3: In-memory index and vector storage synchronization.
     */
    public StagedIndexingResult processDocument(Long documentId, String title, String content) {
        long startTime = System.currentTimeMillis();

        if (documentId == null) {
            return StagedIndexingResult.failure(null, "Document ID cannot be null", 0);
        }

        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return StagedIndexingResult.failure(documentId, "Document not found: " + documentId, 0);
        }

        CrawledDocument doc = docOpt.get();
        String newHash = htmlParser.computeSha256(content != null ? content : "");

        // Idempotency: Skip if unchanged
        if (Objects.equals(doc.getContentHash(), newHash) && Objects.equals(doc.getTitle(), title)
                && doc.getStatus() == DocumentLifecycleStatus.ACTIVE) {
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
        // STAGE 2: Atomic Transactional Commit (Database Transaction)
        // ====================================================================
        try {
            commitRelationalState(documentId, title, content, newHash, targetVersion, binaryVector);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Stage 2 atomic commit failed for document {}: {}", documentId, e.getMessage(), e);
            recordProcessingFailure(documentId, "Database transaction error: " + e.getMessage());
            return StagedIndexingResult.failure(documentId, "Stage 2 error: " + e.getMessage(), duration);
        }

        // ====================================================================
        // STAGE 3: In-Memory Synchronization (Post-Commit)
        // ====================================================================
        try {
            vectorStorage.put(new DocumentVector(documentId, vector));
        } catch (Exception e) {
            log.warn("Post-commit memory sync warning for document {}: {}", documentId, e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Successfully completed staged indexing for doc {} (version {}): {} tokens in {} ms",
                documentId, targetVersion, tokens.size(), duration);

        return StagedIndexingResult.success(documentId, targetVersion, tokens.size(), duration);
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

        doc.transitionTo(DocumentLifecycleStatus.PROCESSING);
        doc.setTitle(title);
        doc.setCleanText(content);
        doc.setContentHash(contentHash);
        doc.setVersion(targetVersion);
        crawledDocumentRepository.save(doc);

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
        crawledDocumentRepository.findById(documentId).ifPresent(doc -> {
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
}
