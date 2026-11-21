package com.searchforge.lifecycle.model;

/**
 * Thrown when an illegal lifecycle state transition is attempted on a document.
 */
public class InvalidLifecycleTransitionException extends IllegalStateException {

    private final DocumentLifecycleStatus from;
    private final DocumentLifecycleStatus to;

    public InvalidLifecycleTransitionException(DocumentLifecycleStatus from, DocumentLifecycleStatus to) {
        super("Invalid document lifecycle transition from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public DocumentLifecycleStatus getFrom() {
        return from;
    }

    public DocumentLifecycleStatus getTo() {
        return to;
    }
}
