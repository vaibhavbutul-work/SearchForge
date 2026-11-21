package com.searchforge.lifecycle.change;

/**
 * Categorization of document content state changes detected during crawling or ingestion.
 */
public enum ChangeType {

    /**
     * URL is not present in storage. A completely new document to be persisted, indexed, and embedded.
     */
    NEW_DOCUMENT,

    /**
     * URL exists in storage and incoming content hash strictly matches the existing version.
     * Content has not changed; skip re-indexing, embedding, and reprocessing.
     */
    UNCHANGED,

    /**
     * URL exists in storage but incoming content hash differs from the stored document.
     * Content has been modified; requires version increment, incremental index update, and embedding refresh.
     */
    MODIFIED,

    /**
     * URL is new, but identical content hash already exists at another canonical URL.
     * Skipped per mirror-deduplication policy.
     */
    DUPLICATE_CONTENT_DIFFERENT_URL;

    /**
     * True if the document requires lexical reindexing and embedding regeneration.
     */
    public boolean requiresReindexing() {
        return this == NEW_DOCUMENT || this == MODIFIED;
    }
}
