package com.searchforge.lifecycle.recovery;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Service managing retry policies, failure classification, exponential backoff,
 * dead-letter tracking, and recovery of interrupted or failed document indexing operations.
 */
@Service
public class DocumentRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRecoveryService.class);

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final long DEFAULT_BASE_BACKOFF_MS = 1000L;
    public static final long DEFAULT_MAX_BACKOFF_MS = 30000L;

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final TransactionalIndexingCoordinator indexingCoordinator;
    private final int maxRetries;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    @Autowired
    public DocumentRecoveryService(
            CrawledDocumentRepository crawledDocumentRepository,
            TransactionalIndexingCoordinator indexingCoordinator
    ) {
        this(crawledDocumentRepository, indexingCoordinator, DEFAULT_MAX_RETRIES, DEFAULT_BASE_BACKOFF_MS, DEFAULT_MAX_BACKOFF_MS);
    }

    public DocumentRecoveryService(
            CrawledDocumentRepository crawledDocumentRepository,
            TransactionalIndexingCoordinator indexingCoordinator,
            int maxRetries,
            long baseBackoffMs,
            long maxBackoffMs
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository);
        this.indexingCoordinator = Objects.requireNonNull(indexingCoordinator);
        this.maxRetries = maxRetries;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    /**
     * Classifies a failure into TRANSIENT or PERMANENT and determines backoff delay.
     */
    public RetryDecision classifyFailure(Throwable throwable, int currentRetryCount) {
        if (throwable == null) {
            return RetryDecision.deadLetter(FailureType.PERMANENT, "Unknown failure (null exception)");
        }

        FailureType type = determineFailureType(throwable);
        if (type == FailureType.PERMANENT) {
            return RetryDecision.deadLetter(type, "Permanent error: " + throwable.getMessage());
        }

        if (currentRetryCount >= maxRetries) {
            return RetryDecision.deadLetter(type, String.format(
                    "Exceeded maximum retries (%d/%d): %s", currentRetryCount, maxRetries, throwable.getMessage()
            ));
        }

        long backoff = calculateBackoff(currentRetryCount);
        return RetryDecision.retry(type, backoff, throwable.getMessage());
    }

    private FailureType determineFailureType(Throwable t) {
        if (t instanceof TimeoutException || t instanceof IOException || t instanceof SQLException) {
            return FailureType.TRANSIENT;
        }

        String msg = t.getMessage() != null ? t.getMessage().toLowerCase(Locale.ROOT) : "";
        if (msg.contains("timeout") || msg.contains("connection") || msg.contains("deadlock")
                || msg.contains("lock") || msg.contains("memory") || msg.contains("oom")
                || msg.contains("busy") || msg.contains("temporary")) {
            return FailureType.TRANSIENT;
        }

        // Cause inspection
        if (t.getCause() != null && t.getCause() != t) {
            return determineFailureType(t.getCause());
        }

        return FailureType.PERMANENT;
    }

    /**
     * Calculates exponential backoff: baseDelay * 2^retryCount (capped at maxBackoffMs).
     */
    public long calculateBackoff(int retryCount) {
        long exponential = baseBackoffMs * (1L << Math.min(retryCount, 30));
        return Math.min(maxBackoffMs, exponential);
    }

    /**
     * Records failure and schedules retry or dead-letter state.
     * Preserves active version if the document was previously active.
     */
    @Transactional
    public void recordFailure(CrawledDocument doc, Throwable throwable) {
        RetryDecision decision = classifyFailure(throwable, doc.getRetryCount());
        doc.setLastProcessedAt(Instant.now());

        if (doc.getStatus() == DocumentLifecycleStatus.ACTIVE) {
            // Version preservation: active document stays active, records failure message
            log.warn("Update failed for active document {}. Preserving active version {}. Reason: {}",
                    doc.getId(), doc.getVersion(), decision.reason());
            doc.setStatusMessage("Previous active version preserved. Update failed: " + decision.reason());
        } else {
            if (decision.shouldRetry()) {
                doc.setStatus(DocumentLifecycleStatus.RETRY);
                doc.incrementRetryCount();
                doc.setStatusMessage(String.format("Scheduled retry %d/%d (backoff %d ms): %s",
                        doc.getRetryCount(), maxRetries, decision.backoffMillis(), decision.reason()));
                log.info("Document {} transitioned to RETRY (attempt {}): {}", doc.getId(), doc.getRetryCount(), decision.reason());
            } else {
                doc.setStatus(DocumentLifecycleStatus.FAILED);
                doc.setStatusMessage("Dead-lettered: " + decision.reason());
                log.error("Document {} permanently FAILED: {}", doc.getId(), decision.reason());
            }
        }

        crawledDocumentRepository.save(doc);
    }

    /**
     * Retries indexing a single document.
     */
    @Transactional
    public DocumentRecoveryResult retryDocument(Long documentId) {
        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return new DocumentRecoveryResult(documentId, false, null, 0, "Document not found: " + documentId);
        }

        CrawledDocument doc = docOpt.get();
        log.info("Attempting recovery for document {} (status: {}, retry count: {})",
                documentId, doc.getStatus(), doc.getRetryCount());

        try {
            StagedIndexingResult result = indexingCoordinator.processDocument(
                    doc.getId(), doc.getTitle(), doc.getCleanText()
            );

            if (result.isSuccess()) {
                doc.setRetryCount(0);
                doc.setStatusMessage("Recovered successfully at " + Instant.now());
                crawledDocumentRepository.save(doc);
                log.info("Document {} recovered successfully to ACTIVE (version {})", documentId, doc.getVersion());
                return new DocumentRecoveryResult(documentId, true, doc.getStatus(), 0, "Recovered successfully");
            } else {
                recordFailure(doc, new RuntimeException(result.message()));
                return new DocumentRecoveryResult(documentId, false, doc.getStatus(), doc.getRetryCount(), result.message());
            }
        } catch (Exception e) {
            recordFailure(doc, e);
            return new DocumentRecoveryResult(documentId, false, doc.getStatus(), doc.getRetryCount(), e.getMessage());
        }
    }

    /**
     * Re-attempts all documents currently in RETRY status.
     */
    @Transactional
    public RecoveryBatchResult retryAllEligible() {
        List<CrawledDocument> retryBatch = crawledDocumentRepository.findByStatus(DocumentLifecycleStatus.RETRY);
        log.info("Found {} documents eligible for recovery retry", retryBatch.size());

        List<DocumentRecoveryResult> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        for (CrawledDocument doc : retryBatch) {
            DocumentRecoveryResult result = retryDocument(doc.getId());
            results.add(result);
            if (result.success()) {
                success++;
            } else {
                failed++;
            }
        }

        log.info("Batch recovery completed: {} total, {} succeeded, {} failed", retryBatch.size(), success, failed);
        return new RecoveryBatchResult(retryBatch.size(), success, failed, results);
    }
}
