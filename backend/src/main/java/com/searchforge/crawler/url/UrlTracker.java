package com.searchforge.crawler.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe registry that tracks the lifecycle state and retry counts of crawled URLs.
 *
 * Guarantees atomic state transitions via ConcurrentHashMap.compute(), preventing
 * duplicate in-flight fetching by multiple Project Loom virtual threads and ensuring
 * that transient errors can be safely retried without premature deduplication.
 */
@Component
public class UrlTracker {

    private static final Logger log = LoggerFactory.getLogger(UrlTracker.class);

    public record UrlStateRecord(
            UrlCrawlState state,
            int retryCount,
            Instant lastUpdated,
            String failureReason
    ) {}

    private final Map<String, UrlStateRecord> records = new ConcurrentHashMap<>();

    /**
     * Atomically transitions a URL to QUEUED if it is not already queued, in-flight,
     * or successfully processed.
     *
     * @param url The canonical URL to enqueue.
     * @return true if the URL was transitioned to QUEUED; false if already known and active.
     */
    public boolean transitionToQueued(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        AtomicBoolean transitioned = new AtomicBoolean(false);
        records.compute(url, (key, existing) -> {
            if (existing == null) {
                transitioned.set(true);
                return new UrlStateRecord(UrlCrawlState.QUEUED, 0, Instant.now(), null);
            }
            if (existing.state() == UrlCrawlState.RETRY_ELIGIBLE || existing.state() == UrlCrawlState.DISCOVERED) {
                transitioned.set(true);
                return new UrlStateRecord(UrlCrawlState.QUEUED, existing.retryCount(), Instant.now(), null);
            }
            // Already QUEUED, IN_FLIGHT, PROCESSED, or FAILED: reject duplicate enqueuing
            log.trace("URL '{}' rejected for queuing: currently in state {}", url, existing.state());
            return existing;
        });

        return transitioned.get();
    }

    /**
     * Atomically claims an exclusive lease on a URL, transitioning it to IN_FLIGHT.
     *
     * @param url The canonical URL to lease.
     * @return true if successfully claimed by the calling worker; false if already in-flight or complete.
     */
    public boolean claimInFlight(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        AtomicBoolean claimed = new AtomicBoolean(false);
        records.compute(url, (key, existing) -> {
            if (existing == null) {
                claimed.set(true);
                return new UrlStateRecord(UrlCrawlState.IN_FLIGHT, 0, Instant.now(), null);
            }
            if (existing.state() == UrlCrawlState.QUEUED || existing.state() == UrlCrawlState.RETRY_ELIGIBLE) {
                claimed.set(true);
                return new UrlStateRecord(UrlCrawlState.IN_FLIGHT, existing.retryCount(), Instant.now(), null);
            }
            // Already IN_FLIGHT or PROCESSED: do not lease to another worker
            return existing;
        });

        return claimed.get();
    }

    /**
     * Marks a URL as successfully processed and persisted.
     */
    public void markProcessed(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        records.compute(url, (key, existing) -> {
            int retries = existing != null ? existing.retryCount() : 0;
            return new UrlStateRecord(UrlCrawlState.PROCESSED, retries, Instant.now(), null);
        });
    }

    /**
     * Records a crawl failure and determines whether the URL is eligible for retry.
     *
     * @param url        The failed URL.
     * @param maxRetries Configured maximum retry attempts.
     * @param reason     Diagnostic error message or HTTP status.
     * @return true if the URL is eligible for retry; false if retries are exhausted.
     */
    public boolean markFailedOrRetry(String url, int maxRetries, String reason) {
        if (url == null || url.isBlank()) {
            return false;
        }

        AtomicBoolean canRetry = new AtomicBoolean(false);
        records.compute(url, (key, existing) -> {
            int currentRetries = existing != null ? existing.retryCount() : 0;
            if (currentRetries < maxRetries) {
                canRetry.set(true);
                return new UrlStateRecord(UrlCrawlState.RETRY_ELIGIBLE, currentRetries + 1, Instant.now(), reason);
            } else {
                log.warn("URL '{}' exhausted max retries ({}): marked as FAILED ({})", url, maxRetries, reason);
                return new UrlStateRecord(UrlCrawlState.FAILED, currentRetries, Instant.now(), reason);
            }
        });

        return canRetry.get();
    }

    /**
     * Marks a URL as permanently failed without retry eligibility (e.g. 404 Not Found, 410 Gone).
     */
    public void markPermanentFailure(String url, String reason) {
        if (url == null || url.isBlank()) {
            return;
        }

        records.compute(url, (key, existing) -> {
            int retries = existing != null ? existing.retryCount() : 0;
            return new UrlStateRecord(UrlCrawlState.FAILED, retries, Instant.now(), reason);
        });
    }

    /**
     * Releases an IN_FLIGHT claim if the worker terminates unexpectedly or times out.
     */
    public void releaseInFlightIfPending(String url, String reason) {
        if (url == null || url.isBlank()) {
            return;
        }

        records.computeIfPresent(url, (key, existing) -> {
            if (existing.state() == UrlCrawlState.IN_FLIGHT) {
                log.warn("Releasing leaked IN_FLIGHT claim for '{}': {}", url, reason);
                return new UrlStateRecord(UrlCrawlState.RETRY_ELIGIBLE, existing.retryCount() + 1, Instant.now(), reason);
            }
            return existing;
        });
    }

    /**
     * Returns the current lifecycle state of the URL, if tracked.
     */
    public Optional<UrlCrawlState> getState(String url) {
        if (url == null) {
            return Optional.empty();
        }
        UrlStateRecord record = records.get(url);
        return record != null ? Optional.of(record.state()) : Optional.empty();
    }

    /**
     * Returns the full state record of the URL, if tracked.
     */
    public Optional<UrlStateRecord> getRecord(String url) {
        return Optional.ofNullable(records.get(url));
    }

    public int getInFlightCount() {
        return (int) records.values().stream()
                .filter(r -> r.state() == UrlCrawlState.IN_FLIGHT)
                .count();
    }

    public int getProcessedCount() {
        return (int) records.values().stream()
                .filter(r -> r.state() == UrlCrawlState.PROCESSED)
                .count();
    }

    public int getFailedCount() {
        return (int) records.values().stream()
                .filter(r -> r.state() == UrlCrawlState.FAILED)
                .count();
    }

    public int getRetryCount() {
        return (int) records.values().stream()
                .filter(r -> r.state() == UrlCrawlState.RETRY_ELIGIBLE)
                .count();
    }

    public int getTotalTrackedCount() {
        return records.size();
    }

    /**
     * Resets all in-memory tracking records (primarily for test isolation and fresh crawl runs).
     */
    public void clear() {
        records.clear();
    }
}
