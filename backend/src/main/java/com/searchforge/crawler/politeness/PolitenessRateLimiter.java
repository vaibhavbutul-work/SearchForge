package com.searchforge.crawler.politeness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces per-domain politeness crawl delays.
 *
 * Guarantees that successive HTTP requests directed to the same domain are separated
 * by at least the configured or robots-specified crawl delay.
 *
 * Uses fine-grained, per-domain synchronization locks so that rate limiting for one domain
 * never blocks or slows down concurrent requests targeting different domains.
 */
@Component
public class PolitenessRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PolitenessRateLimiter.class);

    private final Map<String, Long> lastAccessTimeByDomain = new ConcurrentHashMap<>();
    private final Map<String, Object> domainLocks = new ConcurrentHashMap<>();

    /**
     * Blocks the calling thread if necessary to ensure the minimum delay between
     * requests to the specified domain has elapsed.
     *
     * @param domain     The target domain (e.g. "example.com").
     * @param minDelayMs The minimum required pause in milliseconds.
     */
    public void delayIfNeeded(String domain, long minDelayMs) {
        if (domain == null || domain.isBlank() || minDelayMs <= 0) {
            return;
        }

        String normalizedDomain = domain.toLowerCase(Locale.ROOT);
        Object lock = domainLocks.computeIfAbsent(normalizedDomain, k -> new Object());

        synchronized (lock) {
            long now = System.currentTimeMillis();
            Long lastAccess = lastAccessTimeByDomain.get(normalizedDomain);

            if (lastAccess != null) {
                long elapsed = now - lastAccess;
                long sleepTime = minDelayMs - elapsed;

                if (sleepTime > 0) {
                    log.trace("Politeness throttle: sleeping for {} ms for domain '{}'", sleepTime, normalizedDomain);
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Politeness delay interrupted for domain '{}'", normalizedDomain);
                    }
                }
            }

            lastAccessTimeByDomain.put(normalizedDomain, System.currentTimeMillis());
        }
    }

    /**
     * Clears all recorded timestamps and domain locks (primarily for tests).
     */
    public void clear() {
        lastAccessTimeByDomain.clear();
        domainLocks.clear();
    }
}
