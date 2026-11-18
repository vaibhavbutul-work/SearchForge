package com.searchforge.crawler.url;

import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe filter that tracks visited URLs to prevent duplicate crawling.
 *
 * Employs a dual-layer check:
 * 1. High-speed in-memory set (ConcurrentHashMap.newKeySet()) for current crawl session.
 * 2. Persistent database check (CrawledDocumentRepository.existsByUrl()) across application restarts.
 */
@Component
public class VisitedFilter {

    private final Set<String> memoryVisited = ConcurrentHashMap.newKeySet();
    private final CrawledDocumentRepository repository;

    public VisitedFilter(CrawledDocumentRepository repository) {
        this.repository = repository;
    }

    /**
     * Checks whether a URL has already been visited or stored.
     *
     * @param url Canonical URL to check.
     * @return true if already visited; false otherwise.
     */
    public boolean isVisited(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        return memoryVisited.contains(url) || repository.existsByUrl(url);
    }

    /**
     * Atomically marks a URL as visited if it has not been visited before.
     *
     * @param url Canonical URL to record.
     * @return true if the URL was newly visited and recorded; false if it was already visited.
     */
    public boolean markVisited(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // Check persistent storage first (prevents re-crawling across restarts)
        if (repository.existsByUrl(url)) {
            memoryVisited.add(url);
            return false;
        }

        // Atomic add to thread-safe concurrent set
        return memoryVisited.add(url);
    }

    /**
     * Returns the count of distinct URLs visited during the current in-memory session.
     */
    public int getMemoryVisitedCount() {
        return memoryVisited.size();
    }

    /**
     * Clears the in-memory cache (primarily used between independent crawl runs and tests).
     */
    public void clearMemory() {
        memoryVisited.clear();
    }
}
