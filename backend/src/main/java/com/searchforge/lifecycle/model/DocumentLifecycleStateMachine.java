package com.searchforge.lifecycle.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * State machine defining and enforcing valid lifecycle transitions for documents.
 * Prevents invalid state jumps and guarantees consistency across crawling, indexing,
 * updating, failure recovery, and deletion.
 */
public final class DocumentLifecycleStateMachine {

    private static final Map<DocumentLifecycleStatus, Set<DocumentLifecycleStatus>> VALID_TRANSITIONS;

    static {
        Map<DocumentLifecycleStatus, Set<DocumentLifecycleStatus>> transitions = new EnumMap<>(DocumentLifecycleStatus.class);

        // DISCOVERED: URL found, can be fetched, entered into processing, or fail
        transitions.put(DocumentLifecycleStatus.DISCOVERED, EnumSet.of(
                DocumentLifecycleStatus.FETCHED,
                DocumentLifecycleStatus.PROCESSING,
                DocumentLifecycleStatus.FAILED,
                DocumentLifecycleStatus.DELETED
        ));

        // FETCHED: HTML payload downloaded, can enter processing or fail
        transitions.put(DocumentLifecycleStatus.FETCHED, EnumSet.of(
                DocumentLifecycleStatus.PROCESSING,
                DocumentLifecycleStatus.FAILED,
                DocumentLifecycleStatus.DELETED
        ));

        // PROCESSING: Ingesting text, can proceed to indexing, direct activation, or fail
        transitions.put(DocumentLifecycleStatus.PROCESSING, EnumSet.of(
                DocumentLifecycleStatus.INDEXING,
                DocumentLifecycleStatus.EMBEDDING,
                DocumentLifecycleStatus.ACTIVE,
                DocumentLifecycleStatus.FAILED,
                DocumentLifecycleStatus.DELETING,
                DocumentLifecycleStatus.DELETED
        ));

        // INDEXING: Lexical postings being saved, can proceed to embedding, active, or fail
        transitions.put(DocumentLifecycleStatus.INDEXING, EnumSet.of(
                DocumentLifecycleStatus.EMBEDDING,
                DocumentLifecycleStatus.ACTIVE,
                DocumentLifecycleStatus.FAILED,
                DocumentLifecycleStatus.DELETING,
                DocumentLifecycleStatus.DELETED
        ));

        // EMBEDDING: Vector being computed and saved, can transition to active or fail
        transitions.put(DocumentLifecycleStatus.EMBEDDING, EnumSet.of(
                DocumentLifecycleStatus.ACTIVE,
                DocumentLifecycleStatus.FAILED,
                DocumentLifecycleStatus.DELETING,
                DocumentLifecycleStatus.DELETED
        ));

        // ACTIVE: Fully searchable, can be re-processed on content change or deleted
        transitions.put(DocumentLifecycleStatus.ACTIVE, EnumSet.of(
                DocumentLifecycleStatus.PROCESSING,
                DocumentLifecycleStatus.DELETING,
                DocumentLifecycleStatus.DELETED
        ));

        // FAILED: Can be retried, directly re-processed, or deleted
        transitions.put(DocumentLifecycleStatus.FAILED, EnumSet.of(
                DocumentLifecycleStatus.RETRY,
                DocumentLifecycleStatus.PROCESSING,
                DocumentLifecycleStatus.DELETING,
                DocumentLifecycleStatus.DELETED
        ));

        // RETRY: Scheduled for retry, transitions to processing, fails again, or is deleted
        transitions.put(DocumentLifecycleStatus.RETRY, EnumSet.of(
                DocumentLifecycleStatus.PROCESSING,
                DocumentLifecycleStatus.FAILED,
                DocumentLifecycleStatus.DELETING,
                DocumentLifecycleStatus.DELETED
        ));

        // DELETING: Soft-lock state, transitions to terminal DELETED or FAILED
        transitions.put(DocumentLifecycleStatus.DELETING, EnumSet.of(
                DocumentLifecycleStatus.DELETED,
                DocumentLifecycleStatus.FAILED
        ));

        // DELETED: Terminal state. No transitions allowed.
        transitions.put(DocumentLifecycleStatus.DELETED, Collections.emptySet());

        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private DocumentLifecycleStateMachine() {
        // Utility class
    }

    /**
     * Checks if transitioning from 'from' status to 'to' status is logically valid.
     */
    public static boolean isValidTransition(DocumentLifecycleStatus from, DocumentLifecycleStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true; // No-op idempotent transition
        }
        Set<DocumentLifecycleStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Validates that transitioning from 'from' status to 'to' status is legal.
     * Throws InvalidLifecycleTransitionException if the transition is prohibited.
     */
    public static void validateTransition(DocumentLifecycleStatus from, DocumentLifecycleStatus to) {
        if (!isValidTransition(from, to)) {
            throw new InvalidLifecycleTransitionException(from, to);
        }
    }

    /**
     * Returns an unmodifiable set of all valid target statuses from the given source status.
     */
    public static Set<DocumentLifecycleStatus> getAllowedTransitions(DocumentLifecycleStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return VALID_TRANSITIONS.getOrDefault(from, Collections.emptySet());
    }
}
