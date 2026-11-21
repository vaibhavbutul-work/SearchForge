package com.searchforge.lifecycle.model;

import com.searchforge.crawler.model.CrawledDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 1: Document Lifecycle State Machine Unit Tests")
class DocumentLifecycleStateMachineTest {

    @Test
    @DisplayName("Verify normal happy-path pipeline transitions from DISCOVERED to ACTIVE")
    void testHappyPathTransitions() {
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.DISCOVERED, DocumentLifecycleStatus.FETCHED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.FETCHED, DocumentLifecycleStatus.PROCESSING));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.PROCESSING, DocumentLifecycleStatus.INDEXING));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.INDEXING, DocumentLifecycleStatus.EMBEDDING));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.EMBEDDING, DocumentLifecycleStatus.ACTIVE));

        // Direct shortcut from PROCESSING to ACTIVE for atomic unified indexing
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.PROCESSING, DocumentLifecycleStatus.ACTIVE));
    }

    @Test
    @DisplayName("Verify failure transitions from intermediate pipeline states")
    void testFailureTransitions() {
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.DISCOVERED, DocumentLifecycleStatus.FAILED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.FETCHED, DocumentLifecycleStatus.FAILED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.PROCESSING, DocumentLifecycleStatus.FAILED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.INDEXING, DocumentLifecycleStatus.FAILED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.EMBEDDING, DocumentLifecycleStatus.FAILED));
    }

    @Test
    @DisplayName("Verify recovery and retry transitions from FAILED state")
    void testRecoveryTransitions() {
        // FAILED -> RETRY -> PROCESSING -> ACTIVE
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.FAILED, DocumentLifecycleStatus.RETRY));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.RETRY, DocumentLifecycleStatus.PROCESSING));

        // Direct recovery: FAILED -> PROCESSING
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.FAILED, DocumentLifecycleStatus.PROCESSING));
    }

    @Test
    @DisplayName("Verify content update cycle from ACTIVE to PROCESSING to ACTIVE")
    void testActiveUpdateCycle() {
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.ACTIVE, DocumentLifecycleStatus.PROCESSING));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.PROCESSING, DocumentLifecycleStatus.ACTIVE));
    }

    @Test
    @DisplayName("Verify deletion flow from ACTIVE to DELETING to DELETED")
    void testDeletionFlow() {
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.ACTIVE, DocumentLifecycleStatus.DELETING));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.DELETING, DocumentLifecycleStatus.DELETED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.ACTIVE, DocumentLifecycleStatus.DELETED));
        assertTrue(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.FAILED, DocumentLifecycleStatus.DELETED));
    }

    @Test
    @DisplayName("Terminal DELETED state rejects any subsequent transitions")
    void testDeletedIsTerminal() {
        assertTrue(DocumentLifecycleStatus.DELETED.isTerminal());

        for (DocumentLifecycleStatus status : DocumentLifecycleStatus.values()) {
            if (status != DocumentLifecycleStatus.DELETED) {
                assertFalse(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.DELETED, status),
                        "DELETED should not be allowed to transition to " + status);
                assertThrows(InvalidLifecycleTransitionException.class, () ->
                        DocumentLifecycleStateMachine.validateTransition(DocumentLifecycleStatus.DELETED, status));
            }
        }
    }

    @Test
    @DisplayName("Verify invalid transition rejections")
    void testInvalidTransitions() {
        // Cannot jump directly from DISCOVERED to ACTIVE without fetching & processing
        assertFalse(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.DISCOVERED, DocumentLifecycleStatus.ACTIVE));
        assertThrows(InvalidLifecycleTransitionException.class, () ->
                DocumentLifecycleStateMachine.validateTransition(DocumentLifecycleStatus.DISCOVERED, DocumentLifecycleStatus.ACTIVE));

        // Cannot jump directly from FETCHED to ACTIVE without processing
        assertFalse(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.FETCHED, DocumentLifecycleStatus.ACTIVE));

        // Cannot jump backwards from ACTIVE to FETCHED
        assertFalse(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.ACTIVE, DocumentLifecycleStatus.FETCHED));

        // Cannot transition from DELETING back to ACTIVE
        assertFalse(DocumentLifecycleStateMachine.isValidTransition(DocumentLifecycleStatus.DELETING, DocumentLifecycleStatus.ACTIVE));
    }

    @Test
    @DisplayName("Idempotent transition to same status is allowed")
    void testIdempotentSameStatus() {
        for (DocumentLifecycleStatus status : DocumentLifecycleStatus.values()) {
            assertTrue(DocumentLifecycleStateMachine.isValidTransition(status, status));
            assertDoesNotThrow(() -> DocumentLifecycleStateMachine.validateTransition(status, status));
        }
    }

    @ParameterizedTest
    @EnumSource(DocumentLifecycleStatus.class)
    @DisplayName("Only ACTIVE status is searchable; all others are non-searchable")
    void testSearchabilityInvariants(DocumentLifecycleStatus status) {
        if (status == DocumentLifecycleStatus.ACTIVE) {
            assertTrue(status.isSearchable(), "ACTIVE documents must be searchable");
        } else {
            assertFalse(status.isSearchable(), status + " documents must NOT be searchable");
        }
    }

    @Test
    @DisplayName("CrawledDocument transitionTo() updates status and validates legality")
    void testCrawledDocumentTransition() {
        CrawledDocument doc = new CrawledDocument(
                "https://searchforge.dev/test",
                "searchforge.dev",
                "Test Title",
                "Test clean text body",
                "hash123",
                200,
                Instant.now()
        );

        assertEquals(DocumentLifecycleStatus.FETCHED, doc.getStatus());
        assertEquals(1, doc.getVersion());
        assertEquals(0, doc.getRetryCount());
        assertFalse(doc.isSearchable());

        // Valid transition: FETCHED -> PROCESSING
        doc.transitionTo(DocumentLifecycleStatus.PROCESSING);
        assertEquals(DocumentLifecycleStatus.PROCESSING, doc.getStatus());
        assertFalse(doc.isSearchable());

        // Valid transition: PROCESSING -> ACTIVE
        doc.transitionTo(DocumentLifecycleStatus.ACTIVE);
        assertEquals(DocumentLifecycleStatus.ACTIVE, doc.getStatus());
        assertTrue(doc.isSearchable());
        assertNotNull(doc.getLastProcessedAt());

        // Invalid transition: ACTIVE -> DISCOVERED should throw
        assertThrows(InvalidLifecycleTransitionException.class, () ->
                doc.transitionTo(DocumentLifecycleStatus.DISCOVERED));

        // State remains ACTIVE after failed transition attempt
        assertEquals(DocumentLifecycleStatus.ACTIVE, doc.getStatus());

        // Valid deletion: ACTIVE -> DELETING -> DELETED
        doc.transitionTo(DocumentLifecycleStatus.DELETING);
        assertEquals(DocumentLifecycleStatus.DELETING, doc.getStatus());
        assertFalse(doc.isSearchable());

        doc.transitionTo(DocumentLifecycleStatus.DELETED);
        assertEquals(DocumentLifecycleStatus.DELETED, doc.getStatus());
        assertFalse(doc.isSearchable());
        assertTrue(doc.getStatus().isTerminal());
    }
}
