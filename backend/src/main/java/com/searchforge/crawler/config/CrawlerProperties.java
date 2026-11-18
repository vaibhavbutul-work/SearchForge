package com.searchforge.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Type-safe configuration properties for the SearchForge Web Crawler.
 * Maps to the "crawler.*" hierarchy in application.yml.
 *
 * Implemented as a Java 21 Record for immutability and thread safety.
 */
@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(
        String userAgent,
        List<String> seedUrls,
        List<String> allowedDomains,
        int maxDepth,
        int maxPages,
        long crawlDelayMs,
        int connectTimeoutMs,
        int readTimeoutMs,
        int concurrency
) {
    /**
     * Compact constructor providing defensive copying and robust defaults.
     */
    public CrawlerProperties {
        seedUrls = seedUrls != null ? List.copyOf(seedUrls) : List.of();
        allowedDomains = allowedDomains != null ? List.copyOf(allowedDomains) : List.of();
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "SearchForgeBot/1.0";
        }
        if (maxDepth < 0) {
            maxDepth = 0;
        }
        if (maxPages <= 0) {
            maxPages = 100;
        }
        if (crawlDelayMs < 0) {
            crawlDelayMs = 1000;
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 3000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 5000;
        }
        if (concurrency <= 0) {
            concurrency = 4;
        }
    }
}
