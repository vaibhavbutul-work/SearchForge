package com.searchforge.crawler.url;

/**
 * Represents the distinct lifecycle states of a URL during web crawling.
 *
 * Guarantees state transparency across concurrent workers, prevents duplicate
 * in-flight fetching, and enables deterministic retry policies.
 */
public enum UrlCrawlState {
    /**
     * URL was discovered in seed configuration or parsed from an HTML document.
     */
    DISCOVERED,

    /**
     * URL passed normalization and deduplication checks and is waiting in the frontier queue.
     */
    QUEUED,

    /**
     * URL has been leased by an active worker and is currently being fetched and parsed.
     */
    IN_FLIGHT,

    /**
     * URL was successfully fetched, parsed, deduplicated by content hash, and persisted.
     */
    PROCESSED,

    /**
     * URL encountered a transient network or server error and is eligible for retry.
     */
    RETRY_ELIGIBLE,

    /**
     * URL permanently failed (e.g., HTTP 404/410, malformed protocol, or max retries exceeded).
     */
    FAILED
}
