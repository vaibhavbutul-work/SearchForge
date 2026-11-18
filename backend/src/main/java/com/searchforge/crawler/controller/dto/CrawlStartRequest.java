package com.searchforge.crawler.controller.dto;

import java.util.List;

/**
 * Request payload for initiating a crawl run via REST API.
 *
 * @param seedUrls Optional list of entry-point URLs. If null or empty, the crawler
 *                 falls back to default seeds configured in application.yml.
 */
public record CrawlStartRequest(
        List<String> seedUrls
) {
    public CrawlStartRequest {
        seedUrls = seedUrls != null ? List.copyOf(seedUrls) : List.of();
    }
}
