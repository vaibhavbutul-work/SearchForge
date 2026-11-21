package com.searchforge.lifecycle.change;

/**
 * Immutable evaluation result produced by the DocumentChangeDetector.
 */
public record ChangeDetectionResult(
        ChangeType changeType,
        String normalizedUrl,
        String contentHash,
        Long existingDocumentId,
        Integer currentVersion,
        Integer targetVersion,
        boolean titleChanged,
        String reason
) {
    public boolean requiresReindexing() {
        return changeType != null && changeType.requiresReindexing();
    }

    public static ChangeDetectionResult newDocument(String normalizedUrl, String contentHash) {
        return new ChangeDetectionResult(
                ChangeType.NEW_DOCUMENT,
                normalizedUrl,
                contentHash,
                null,
                null,
                1,
                false,
                "New document discovered; initial indexing required"
        );
    }

    public static ChangeDetectionResult unchanged(String normalizedUrl, String contentHash, Long docId, int version, boolean titleChanged) {
        String reason = titleChanged
                ? "Content unchanged (hash match); minor metadata update only"
                : "Content identical to existing version (hash match); skipping reprocessing";
        return new ChangeDetectionResult(
                ChangeType.UNCHANGED,
                normalizedUrl,
                contentHash,
                docId,
                version,
                version,
                titleChanged,
                reason
        );
    }

    public static ChangeDetectionResult modified(String normalizedUrl, String contentHash, Long docId, int currentVersion) {
        return new ChangeDetectionResult(
                ChangeType.MODIFIED,
                normalizedUrl,
                contentHash,
                docId,
                currentVersion,
                currentVersion + 1,
                true,
                "Content modified (hash delta detected); updating version from " + currentVersion + " to " + (currentVersion + 1)
        );
    }

    public static ChangeDetectionResult duplicateContentDifferentUrl(String normalizedUrl, String contentHash) {
        return new ChangeDetectionResult(
                ChangeType.DUPLICATE_CONTENT_DIFFERENT_URL,
                normalizedUrl,
                contentHash,
                null,
                null,
                null,
                false,
                "Identical content already persisted under a different URL; skipping duplicate"
        );
    }
}
