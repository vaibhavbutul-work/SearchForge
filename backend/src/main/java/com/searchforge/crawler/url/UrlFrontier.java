package com.searchforge.crawler.url;

import com.searchforge.crawler.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * URL Frontier implementing a thread-safe, lock-free Breadth-First Search (BFS) queue.
 *
 * Responsibilities:
 * 1. Manages candidate URLs scheduled for crawling.
 * 2. Enforces crawl depth limits (depth <= maxDepth).
 * 3. Enforces domain boundaries against the allowed domains whitelist.
 * 4. Coordinates with VisitedFilter to discard previously visited URLs.
 */
@Component
public class UrlFrontier {

    private static final Logger log = LoggerFactory.getLogger(UrlFrontier.class);

    private final Queue<CrawlTask> queue = new ConcurrentLinkedQueue<>();
    private final CrawlerProperties properties;
    private final VisitedFilter visitedFilter;
    private final UrlNormalizer urlNormalizer;

    public UrlFrontier(CrawlerProperties properties,
                       VisitedFilter visitedFilter,
                       UrlNormalizer urlNormalizer) {
        this.properties = properties;
        this.visitedFilter = visitedFilter;
        this.urlNormalizer = urlNormalizer;
    }

    /**
     * Attempts to normalize, validate, deduplicate, and enqueue a URL for crawling.
     *
     * @param rawUrl  The raw URL to enqueue.
     * @param depth   The crawl hop depth (seed is 0).
     * @param baseUrl Base URL for relative link resolution (optional).
     * @return true if the URL was successfully validated and enqueued; false if discarded.
     */
    public boolean enqueue(String rawUrl, int depth, String baseUrl) {
        if (depth > properties.maxDepth()) {
            log.trace("Discarding URL '{}': depth {} exceeds maxDepth {}", rawUrl, depth, properties.maxDepth());
            return false;
        }

        Optional<String> normalizedOpt = urlNormalizer.normalize(rawUrl, baseUrl);
        if (normalizedOpt.isEmpty()) {
            return false;
        }

        String normalizedUrl = normalizedOpt.get();

        // Enforce domain whitelist
        Optional<String> domainOpt = urlNormalizer.extractDomain(normalizedUrl);
        if (domainOpt.isEmpty()) {
            return false;
        }

        String domain = domainOpt.get();
        if (!properties.allowedDomains().isEmpty() && !properties.allowedDomains().contains(domain)) {
            log.trace("Discarding URL '{}': domain '{}' not in allowedDomains", normalizedUrl, domain);
            return false;
        }

        // Check deduplication (both in-memory and database)
        if (!visitedFilter.markVisited(normalizedUrl)) {
            log.trace("Discarding URL '{}': already visited or enqueued", normalizedUrl);
            return false;
        }

        boolean offered = queue.offer(new CrawlTask(normalizedUrl, depth));
        if (offered) {
            log.debug("Enqueued [depth={}]: {}", depth, normalizedUrl);
        }
        return offered;
    }

    /**
     * Convenience overload for absolute URLs.
     */
    public boolean enqueue(String rawUrl, int depth) {
        return enqueue(rawUrl, depth, null);
    }

    /**
     * Retrieves and removes the next URL task in BFS order.
     *
     * @return Optional containing the next CrawlTask, or Optional.empty() if the frontier is empty.
     */
    public Optional<CrawlTask> poll() {
        return Optional.ofNullable(queue.poll());
    }

    /**
     * Current count of pending URLs in the frontier queue.
     */
    public int size() {
        return queue.size();
    }

    /**
     * Checks if the frontier has any pending URLs.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Clears all pending URLs from the frontier queue.
     */
    public void clear() {
        queue.clear();
    }
}
