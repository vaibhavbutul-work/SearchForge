package com.searchforge.lifecycle.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.parser.ParsedDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingResult;
import com.searchforge.lifecycle.change.ChangeDetectionResult;
import com.searchforge.lifecycle.change.ChangeType;
import com.searchforge.lifecycle.change.DocumentChangeDetector;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Engine coordinating incremental document updates, version increments,
 * lexical index replacement, positional update, BM25 statistics maintenance,
 * and semantic embedding synchronization without full corpus reindexing.
 */
@Service
public class IncrementalIndexUpdateEngine {

    private static final Logger log = LoggerFactory.getLogger(IncrementalIndexUpdateEngine.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final DocumentChangeDetector changeDetector;
    private final IndexingEngine indexingEngine;
    private final DocumentEmbeddingService embeddingService;
    private final HtmlParser htmlParser;

    public IncrementalIndexUpdateEngine(CrawledDocumentRepository crawledDocumentRepository,
                                        DocumentChangeDetector changeDetector,
                                        IndexingEngine indexingEngine,
                                        DocumentEmbeddingService embeddingService,
                                        HtmlParser htmlParser) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
        this.changeDetector = Objects.requireNonNull(changeDetector, "DocumentChangeDetector cannot be null");
        this.indexingEngine = Objects.requireNonNull(indexingEngine, "IndexingEngine cannot be null");
        this.embeddingService = Objects.requireNonNull(embeddingService, "DocumentEmbeddingService cannot be null");
        this.htmlParser = Objects.requireNonNull(htmlParser, "HtmlParser cannot be null");
    }

    /**
     * Incrementally updates an existing document with new content.
     * Avoids rebuilding the entire corpus.
     */
    @Transactional
    public IncrementalUpdateResult updateDocument(Long documentId, String newTitle, String newCleanText) {
        long startTime = System.currentTimeMillis();

        if (documentId == null) {
            return IncrementalUpdateResult.failure(null, null, 0, "Document ID cannot be null", 0);
        }

        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return IncrementalUpdateResult.failure(documentId, null, 0, "Document ID not found: " + documentId, 0);
        }

        CrawledDocument doc = docOpt.get();
        String newHash = htmlParser.computeSha256(newCleanText != null ? newCleanText : "");

        // Change detection: If hash and title match, skip processing
        if (Objects.equals(doc.getContentHash(), newHash) && Objects.equals(doc.getTitle(), newTitle)) {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Document {} is unchanged; skipping incremental update", documentId);
            return IncrementalUpdateResult.unchanged(documentId, doc.getUrl(), doc.getVersion(), duration);
        }

        int prevVersion = doc.getVersion();
        int newVersion = prevVersion + 1;

        try {
            // 1. Transition to PROCESSING state
            doc.transitionTo(DocumentLifecycleStatus.PROCESSING);
            doc.setTitle(newTitle);
            doc.setCleanText(newCleanText);
            doc.setContentHash(newHash);
            doc.setVersion(newVersion);
            crawledDocumentRepository.save(doc);

            // 2. Incremental Lexical & Positional Index Replacement
            // Purges old postings, decrements old term stats, tokenizes new content,
            // inserts new postings, and updates in-memory index + BM25 statistics.
            IndexingResult indexResult = indexingEngine.indexDocument(documentId, newTitle, newCleanText);
            if (!indexResult.success()) {
                doc.transitionTo(DocumentLifecycleStatus.FAILED);
                doc.setStatusMessage("Lexical indexing failed: " + indexResult.errorMessage());
                crawledDocumentRepository.save(doc);
                long duration = System.currentTimeMillis() - startTime;
                return IncrementalUpdateResult.failure(documentId, doc.getUrl(), prevVersion, indexResult.errorMessage(), duration);
            }

            // 3. Refresh Dense Semantic Vector Embedding
            embeddingService.generateAndStoreEmbedding(
                    documentId,
                    newTitle,
                    newCleanText,
                    newHash,
                    newVersion
            );

            // 4. Activate updated document
            doc.transitionTo(DocumentLifecycleStatus.ACTIVE);
            doc.setLastProcessedAt(Instant.now());
            crawledDocumentRepository.save(doc);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Incrementally updated doc {} (ver {} -> {}): {} tokens, {} unique terms in {} ms",
                    documentId, prevVersion, newVersion, indexResult.tokenCount(), indexResult.uniqueTerms(), duration);

            return IncrementalUpdateResult.success(
                    documentId,
                    doc.getUrl(),
                    prevVersion,
                    newVersion,
                    indexResult.tokenCount(),
                    indexResult.uniqueTerms(),
                    true,
                    duration
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to incrementally update document {}: {}", documentId, e.getMessage(), e);
            doc.transitionTo(DocumentLifecycleStatus.FAILED);
            doc.setStatusMessage("Incremental update failed: " + e.getMessage());
            crawledDocumentRepository.save(doc);
            return IncrementalUpdateResult.failure(documentId, doc.getUrl(), prevVersion, e.getMessage(), duration);
        }
    }

    /**
     * Processes an incoming parsed document from the crawler with automatic change detection.
     */
    @Transactional
    public IncrementalUpdateResult processParsedDocument(ParsedDocument parsedDoc) {
        ChangeDetectionResult change = changeDetector.detectChange(parsedDoc);

        if (change.changeType() == ChangeType.UNCHANGED) {
            return IncrementalUpdateResult.unchanged(
                    change.existingDocumentId(),
                    parsedDoc.url(),
                    change.currentVersion() != null ? change.currentVersion() : 1,
                    0
            );
        }

        if (change.changeType() == ChangeType.DUPLICATE_CONTENT_DIFFERENT_URL) {
            return IncrementalUpdateResult.failure(
                    null,
                    parsedDoc.url(),
                    1,
                    "Duplicate content exists at another URL; skipping",
                    0
            );
        }

        if (change.changeType() == ChangeType.MODIFIED) {
            return updateDocument(change.existingDocumentId(), parsedDoc.title(), parsedDoc.cleanText());
        }

        // NEW_DOCUMENT: Persist, Index, and Embed
        long startTime = System.currentTimeMillis();
        CrawledDocument newDoc = new CrawledDocument(
                parsedDoc.url(),
                parsedDoc.domain(),
                parsedDoc.title(),
                parsedDoc.cleanText(),
                parsedDoc.contentHash(),
                parsedDoc.httpStatusCode(),
                parsedDoc.crawledAt(),
                DocumentLifecycleStatus.PROCESSING,
                1
        );
        newDoc = crawledDocumentRepository.save(newDoc);

        IndexingResult indexResult = indexingEngine.indexDocument(newDoc);
        if (!indexResult.success()) {
            newDoc.transitionTo(DocumentLifecycleStatus.FAILED);
            newDoc.setStatusMessage(indexResult.errorMessage());
            crawledDocumentRepository.save(newDoc);
            return IncrementalUpdateResult.failure(newDoc.getId(), parsedDoc.url(), 1, indexResult.errorMessage(), System.currentTimeMillis() - startTime);
        }

        embeddingService.generateAndStoreEmbedding(
                newDoc.getId(),
                parsedDoc.title(),
                parsedDoc.cleanText(),
                parsedDoc.contentHash(),
                1
        );

        newDoc.transitionTo(DocumentLifecycleStatus.ACTIVE);
        crawledDocumentRepository.save(newDoc);

        long duration = System.currentTimeMillis() - startTime;
        return IncrementalUpdateResult.success(
                newDoc.getId(),
                parsedDoc.url(),
                0,
                1,
                indexResult.tokenCount(),
                indexResult.uniqueTerms(),
                true,
                duration
        );
    }
}
