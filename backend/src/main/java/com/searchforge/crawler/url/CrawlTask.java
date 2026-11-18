package com.searchforge.crawler.url;

import java.util.Objects;

/**
 * Immutable task representing a URL to be crawled at a specific BFS depth.
 *
 * @param url   The canonicalized, validated URL.
 * @param depth The current hop depth from the seed URL (seed = 0).
 */
public record CrawlTask(String url, int depth) {

    public CrawlTask {
        Objects.requireNonNull(url, "url must not be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (depth < 0) {
            depth = 0;
        }
    }
}
