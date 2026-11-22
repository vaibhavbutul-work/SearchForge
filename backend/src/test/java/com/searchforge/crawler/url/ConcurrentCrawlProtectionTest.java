package com.searchforge.crawler.url;

import com.searchforge.crawler.config.CrawlerProperties;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcurrentCrawlProtectionTest {

    @Mock
    private CrawledDocumentRepository repository;

    private UrlTracker urlTracker;
    private UrlNormalizer normalizer;
    private VisitedFilter visitedFilter;
    private UrlFrontier frontier;
    private CrawlerProperties properties;

    @BeforeEach
    void setUp() {
        urlTracker = new UrlTracker();
        normalizer = new UrlNormalizer();
        visitedFilter = new VisitedFilter(repository, urlTracker);

        properties = new CrawlerProperties(
                "SearchForgeBot/1.0",
                List.of("https://example.com"),
                List.of("example.com"),
                3,    // maxDepth
                100,  // maxPages
                1000,
                3000,
                5000,
                4,
                3,    // maxRetries
                100L  // retryDelayMs
        );

        frontier = new UrlFrontier(properties, visitedFilter, normalizer, urlTracker);
        when(repository.existsByUrl(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("Should ensure exactly 1 worker claims in-flight lease when 20 virtual threads compete on identical URL")
    void shouldEnsureSingleWorkerClaimsInFlightUnderConcurrency() throws Exception {
        String targetUrl = "https://example.com/high-concurrency-target";
        assertTrue(frontier.enqueue(targetUrl, 0));

        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulClaims = new AtomicInteger(0);
        AtomicInteger rejectedClaims = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize all virtual threads to start at the exact same instant
                        if (urlTracker.claimInFlight(targetUrl)) {
                            successfulClaims.incrementAndGet();
                            Thread.sleep(20); // Simulate network I/O
                            urlTracker.markProcessed(targetUrl);
                        } else {
                            rejectedClaims.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Virtual thread failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent workers timed out");
        }

        assertEquals(1, successfulClaims.get(), "Exactly one worker must claim the in-flight lease");
        assertEquals(threadCount - 1, rejectedClaims.get(), "All other competing workers must be rejected");
        assertEquals(0, urlTracker.getInFlightCount(), "In-flight count must return to 0");
        assertEquals(1, urlTracker.getProcessedCount(), "Processed count must be exactly 1");
        assertEquals(UrlCrawlState.PROCESSED, urlTracker.getState(targetUrl).orElseThrow());
    }

    @Test
    @DisplayName("Should support retry state transition for transient failures up to maxRetries")
    void shouldSupportRetryStateTransitionsForTransientFailures() {
        String targetUrl = "https://example.com/transient-error-page";
        assertTrue(frontier.enqueue(targetUrl, 0));

        // Attempt 1: Claim and fail
        assertTrue(urlTracker.claimInFlight(targetUrl));
        boolean canRetry1 = urlTracker.markFailedOrRetry(targetUrl, 3, "HTTP 503 Service Unavailable");
        assertTrue(canRetry1, "Attempt 1 should be eligible for retry");
        assertEquals(UrlCrawlState.RETRY_ELIGIBLE, urlTracker.getState(targetUrl).orElseThrow());

        // Re-enqueue
        assertTrue(frontier.enqueue(targetUrl, 0));
        assertEquals(UrlCrawlState.QUEUED, urlTracker.getState(targetUrl).orElseThrow());

        // Attempt 2: Claim and fail
        assertTrue(urlTracker.claimInFlight(targetUrl));
        boolean canRetry2 = urlTracker.markFailedOrRetry(targetUrl, 3, "Connection Timeout");
        assertTrue(canRetry2, "Attempt 2 should be eligible for retry");
        assertEquals(UrlCrawlState.RETRY_ELIGIBLE, urlTracker.getState(targetUrl).orElseThrow());

        // Re-enqueue
        assertTrue(frontier.enqueue(targetUrl, 0));

        // Attempt 3: Claim and fail
        assertTrue(urlTracker.claimInFlight(targetUrl));
        boolean canRetry3 = urlTracker.markFailedOrRetry(targetUrl, 3, "Gateway Timeout");
        assertTrue(canRetry3, "Attempt 3 should be eligible for 3rd retry");

        // Re-enqueue
        assertTrue(frontier.enqueue(targetUrl, 0));

        // Attempt 4: Claim and fail (maxRetries = 3 reached)
        assertTrue(urlTracker.claimInFlight(targetUrl));
        boolean canRetry4 = urlTracker.markFailedOrRetry(targetUrl, 3, "Max retries reached");
        assertFalse(canRetry4, "Attempt 4 should exhaust retries and fail permanently");
        assertEquals(UrlCrawlState.FAILED, urlTracker.getState(targetUrl).orElseThrow());
        assertEquals(1, urlTracker.getFailedCount());

        // Any subsequent enqueue should be discarded
        assertFalse(frontier.enqueue(targetUrl, 0), "Permanently failed URL must not be re-enqueued");
    }

    @Test
    @DisplayName("Should release leaked in-flight claim if worker terminates unexpectedly")
    void shouldReleaseLeakedInFlightClaimOnWorkerInterruption() {
        String targetUrl = "https://example.com/interrupted-task";
        assertTrue(frontier.enqueue(targetUrl, 0));

        assertTrue(urlTracker.claimInFlight(targetUrl));
        assertEquals(1, urlTracker.getInFlightCount());
        assertEquals(UrlCrawlState.IN_FLIGHT, urlTracker.getState(targetUrl).orElseThrow());

        // Simulate crash / exception in worker: finally block cleans up
        urlTracker.releaseInFlightIfPending(targetUrl, "Worker virtual thread interrupted");

        assertEquals(0, urlTracker.getInFlightCount(), "In-flight count must be 0 after cleanup");
        assertEquals(UrlCrawlState.RETRY_ELIGIBLE, urlTracker.getState(targetUrl).orElseThrow(),
                "Leaked in-flight task must be converted to RETRY_ELIGIBLE for recovery");
    }

    @Test
    @DisplayName("Should deduplicate syntactically distinct but canonically equivalent URLs under concurrent enqueue")
    void shouldDeduplicateCanonicalEquivalentsConcurrently() throws Exception {
        List<String> equivalentVariants = List.of(
                "https://example.com/docs/intro",
                "HTTPS://EXAMPLE.COM/docs/intro",
                "https://example.com:443/docs/intro",
                "https://example.com/docs/intro/",
                "https://example.com/docs/intro#architecture",
                "https://example.com/docs/intro#faq"
        );

        int threadCount = equivalentVariants.size();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger enqueuedCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String variant : equivalentVariants) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                        if (frontier.enqueue(variant, 0)) {
                            enqueuedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail("Concurrent enqueue failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        assertEquals(1, enqueuedCount.get(), "Exactly one of the canonical variants must be enqueued");
        assertEquals(1, frontier.size(), "Frontier queue must contain exactly 1 task");

        CrawlTask task = frontier.poll().orElseThrow();
        assertEquals("https://example.com/docs/intro", task.url());
    }
}
