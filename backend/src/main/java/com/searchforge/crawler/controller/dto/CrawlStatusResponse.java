package com.searchforge.crawler.controller.dto;

import com.searchforge.crawler.engine.CrawlState;
import com.searchforge.crawler.engine.CrawlStatus;

import java.time.Instant;

/**
 * REST response representing a real-time operational telemetry snapshot of the crawler.
 */
public record CrawlStatusResponse(
        CrawlState state,
        boolean isRunning,
        int pagesCrawled,
        int pagesFailed,
        int frontierQueueSize,
        Instant startedAt,
        long elapsedSeconds
) {
    public static CrawlStatusResponse from(CrawlStatus status) {
        return new CrawlStatusResponse(
                status.state(),
                status.isRunning(),
                status.pagesCrawled(),
                status.pagesFailed(),
                status.frontierQueueSize(),
                status.startedAt(),
                status.elapsedDuration() != null ? status.elapsedDuration().toSeconds() : 0L
        );
    }
}
