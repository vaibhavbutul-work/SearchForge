package com.searchforge.lifecycle.recovery;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 9: Retry & Recovery System Unit Tests")
class DocumentRecoveryServiceTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private TransactionalIndexingCoordinator indexingCoordinator;

    private DocumentRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new DocumentRecoveryService(
                crawledDocumentRepository,
                indexingCoordinator,
                3,       // maxRetries
                1000L,   // baseBackoff
                30000L   // maxBackoff
        );
    }

    @Test
    @DisplayName("Transient IO/Timeout failure under max retries produces retry decision with exponential backoff")
    void testTransientFailureClassification() {
        RetryDecision decision0 = recoveryService.classifyFailure(new TimeoutException("Socket timed out"), 0);
        assertTrue(decision0.shouldRetry());
        assertEquals(FailureType.TRANSIENT, decision0.failureType());
        assertEquals(1000L, decision0.backoffMillis());

        RetryDecision decision1 = recoveryService.classifyFailure(new IOException("Connection reset"), 1);
        assertTrue(decision1.shouldRetry());
        assertEquals(2000L, decision1.backoffMillis());

        RetryDecision decision2 = recoveryService.classifyFailure(new RuntimeException("database deadlock detected"), 2);
        assertTrue(decision2.shouldRetry());
        assertEquals(4000L, decision2.backoffMillis());
    }

    @Test
    @DisplayName("Transient failure exceeding max retries transitions to dead-letter (no retry)")
    void testExceededRetriesDeadLetter() {
        RetryDecision decision = recoveryService.classifyFailure(new TimeoutException("Timed out"), 3);
        assertFalse(decision.shouldRetry());
        assertEquals(FailureType.TRANSIENT, decision.failureType());
        assertTrue(decision.reason().contains("Exceeded maximum retries"));
    }

    @Test
    @DisplayName("Permanent failure immediately dead-letters without retrying")
    void testPermanentFailureClassification() {
        RetryDecision decision = recoveryService.classifyFailure(new IllegalArgumentException("Invalid format"), 0);
        assertFalse(decision.shouldRetry());
        assertEquals(FailureType.PERMANENT, decision.failureType());
        assertTrue(decision.reason().contains("Permanent error"));
    }

    @Test
    @DisplayName("Active document preserves active status on update failure without corrupting state")
    void testActiveDocumentVersionPreservationOnFailure() {
        Long docId = 42L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/42", "example.com", "Active Title", "Active Content", "hash1", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 2
        );
        doc.setId(docId);

        recoveryService.recordFailure(doc, new RuntimeException("ONNX Runtime temporary OOM"));

        // Document remains ACTIVE and retains its version
        assertEquals(DocumentLifecycleStatus.ACTIVE, doc.getStatus());
        assertEquals(2, doc.getVersion());
        assertTrue(doc.getStatusMessage().contains("Previous active version preserved"));
        verify(crawledDocumentRepository).save(doc);
    }

    @Test
    @DisplayName("Non-active document transitions to RETRY and increments retryCount")
    void testNonActiveDocumentTransitionsToRetry() {
        Long docId = 43L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/43", "example.com", "Title", "Content", "hash1", 200, Instant.now(),
                DocumentLifecycleStatus.PROCESSING, 1
        );
        doc.setId(docId);
        doc.setRetryCount(0);

        recoveryService.recordFailure(doc, new IOException("Connection refused"));

        assertEquals(DocumentLifecycleStatus.RETRY, doc.getStatus());
        assertEquals(1, doc.getRetryCount());
        assertTrue(doc.getStatusMessage().contains("Scheduled retry 1/3"));
        verify(crawledDocumentRepository).save(doc);
    }

    @Test
    @DisplayName("retryDocument succeeds and resets retry count to 0")
    void testRetryDocumentSuccess() {
        Long docId = 44L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/44", "example.com", "Title", "Content", "hash1", 200, Instant.now(),
                DocumentLifecycleStatus.RETRY, 1
        );
        doc.setId(docId);
        doc.setRetryCount(2);

        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(indexingCoordinator.processDocument(eq(docId), anyString(), anyString()))
                .thenReturn(StagedIndexingResult.success(docId, 1, 10, 15L));

        DocumentRecoveryResult result = recoveryService.retryDocument(docId);

        assertTrue(result.success());
        assertEquals(0, doc.getRetryCount());
        assertTrue(doc.getStatusMessage().contains("Recovered successfully"));
        verify(crawledDocumentRepository).save(doc);
    }

    @Test
    @DisplayName("retryAllEligible iterates over documents in RETRY status and reports batch metrics")
    void testRetryAllEligible() {
        Long docId = 50L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/50", "example.com", "Title", "Content", "hash1", 200, Instant.now(),
                DocumentLifecycleStatus.RETRY, 1
        );
        doc.setId(docId);

        when(crawledDocumentRepository.findByStatus(DocumentLifecycleStatus.RETRY)).thenReturn(List.of(doc));
        when(crawledDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(indexingCoordinator.processDocument(eq(docId), anyString(), anyString()))
                .thenReturn(StagedIndexingResult.success(docId, 1, 10, 15L));

        RecoveryBatchResult batchResult = recoveryService.retryAllEligible();

        assertEquals(1, batchResult.totalAttempted());
        assertEquals(1, batchResult.totalSucceeded());
        assertEquals(0, batchResult.totalFailed());
    }
}
