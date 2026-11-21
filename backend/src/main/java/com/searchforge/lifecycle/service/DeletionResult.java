package com.searchforge.lifecycle.service;

/**
 * Result record of a document deletion and cleanup operation.
 */
public record DeletionResult(
        Status status,
        Long documentId,
        String url,
        boolean hardDeleted,
        String message
) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        FAILED
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public static DeletionResult success(Long documentId, String url, boolean hardDeleted) {
        String msg = hardDeleted
                ? "Document and all dependent lexical and vector representations permanently purged"
                : "Document marked DELETED and all search representations purged";
        return new DeletionResult(Status.SUCCESS, documentId, url, hardDeleted, msg);
    }

    public static DeletionResult notFound(Long documentId) {
        return new DeletionResult(Status.NOT_FOUND, documentId, null, false, "Document ID " + documentId + " not found");
    }

    public static DeletionResult failure(Long documentId, String message) {
        return new DeletionResult(Status.FAILED, documentId, null, false, message);
    }
}
