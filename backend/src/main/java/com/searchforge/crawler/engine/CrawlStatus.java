package com.searchforge.crawler.engine;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable telemetry snapshot of the CrawlEngine's current state and progress.
 *
 * @param state             Current lifecycle state (IDLE, RUNNING, STOPPED, COMPLETED).
 * @param pagesCrawled      Count of successfully fetched and persisted documents.
 * @param pagesFailed       Count of failed HTTP requests (4xx, 5xx, timeouts, drops).
 * @param frontierQueueSize Count of candidate URLs currently queued in the frontier.
 * @param startedAt         Timestamp when the current/last crawl run started.
 * @param elapsedDuration   Time elapsed since crawl start.
 */
public record CrawlStatus(
        CrawlState state,
        int pagesCrawled,
        int pagesFailed,
        int frontierQueueSize,
        Instant startedAt,
        Duration elapsedDuration
) {
}
