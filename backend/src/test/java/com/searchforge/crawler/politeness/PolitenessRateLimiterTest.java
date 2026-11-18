package com.searchforge.crawler.politeness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolitenessRateLimiterTest {

    private PolitenessRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new PolitenessRateLimiter();
    }

    @Test
    @DisplayName("Should enforce minimum delay between successive requests to the same domain")
    void shouldEnforceDelayForSameDomain() {
        String domain = "example.com";
        long requiredDelayMs = 60;

        long start = System.currentTimeMillis();
        limiter.delayIfNeeded(domain, requiredDelayMs);
        limiter.delayIfNeeded(domain, requiredDelayMs);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= requiredDelayMs, "Elapsed time (" + elapsed + "ms) must be >= " + requiredDelayMs + "ms");
    }

    @Test
    @DisplayName("Should not delay requests targeting different domains")
    void shouldNotDelayDifferentDomains() {
        long largeDelayMs = 500;

        long start = System.currentTimeMillis();
        limiter.delayIfNeeded("domain-alpha.com", largeDelayMs);
        // Calling a completely different domain should not wait on domain-alpha's delay window
        limiter.delayIfNeeded("domain-beta.com", largeDelayMs);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 200, "Different domain must not be blocked by another domain's delay (took " + elapsed + "ms)");
    }

    @Test
    @DisplayName("Should ignore null, blank, or zero-delay inputs safely")
    void shouldHandleEdgeCasesSafely() {
        assertDoesNotThrow(() -> limiter.delayIfNeeded(null, 100));
        assertDoesNotThrow(() -> limiter.delayIfNeeded("   ", 100));
        assertDoesNotThrow(() -> limiter.delayIfNeeded("example.com", 0));
        assertDoesNotThrow(() -> limiter.delayIfNeeded("example.com", -50));
    }
}
