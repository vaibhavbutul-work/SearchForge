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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlFrontierTest {

    @Mock
    private CrawledDocumentRepository repository;

    private UrlFrontier frontier;
    private VisitedFilter visitedFilter;
    private UrlNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new UrlNormalizer();
        visitedFilter = new VisitedFilter(repository);
        when(repository.existsByUrl(anyString())).thenReturn(false);

        CrawlerProperties properties = new CrawlerProperties(
                "SearchForgeBot/1.0",
                List.of("https://example.com"),
                List.of("example.com"),
                2,   // maxDepth
                100, // maxPages
                1000,
                3000,
                5000,
                2,
                3,
                500L
        );

        frontier = new UrlFrontier(properties, visitedFilter, normalizer);
    }

    @Test
    @DisplayName("Should enqueue and poll URLs in strict FIFO BFS order")
    void shouldEnqueueAndPollInFifoOrder() {
        assertTrue(frontier.enqueue("https://example.com/page-1", 0));
        assertTrue(frontier.enqueue("https://example.com/page-2", 0));
        assertTrue(frontier.enqueue("https://example.com/page-3", 1));

        assertEquals(3, frontier.size());

        Optional<CrawlTask> first = frontier.poll();
        assertTrue(first.isPresent());
        assertEquals("https://example.com/page-1", first.get().url());
        assertEquals(0, first.get().depth());

        Optional<CrawlTask> second = frontier.poll();
        assertTrue(second.isPresent());
        assertEquals("https://example.com/page-2", second.get().url());

        Optional<CrawlTask> third = frontier.poll();
        assertTrue(third.isPresent());
        assertEquals("https://example.com/page-3", third.get().url());

        assertTrue(frontier.isEmpty());
        assertTrue(frontier.poll().isEmpty());
    }

    @Test
    @DisplayName("Should discard URLs whose depth exceeds maxDepth")
    void shouldDiscardUrlsExceedingMaxDepth() {
        assertTrue(frontier.enqueue("https://example.com/depth-2", 2));
        assertFalse(frontier.enqueue("https://example.com/depth-3", 3), "Depth 3 exceeds maxDepth 2");
        assertEquals(1, frontier.size());
    }

    @Test
    @DisplayName("Should reject URLs belonging to foreign domains not in allowed-domains whitelist")
    void shouldRejectDisallowedDomains() {
        assertTrue(frontier.enqueue("https://example.com/valid", 0));
        assertFalse(frontier.enqueue("https://foreign-domain.com/evil", 0), "Foreign domain must be rejected");
        assertEquals(1, frontier.size());
    }

    @Test
    @DisplayName("Should deduplicate URLs so the same page is not enqueued twice")
    void shouldDeduplicateUrls() {
        assertTrue(frontier.enqueue("https://example.com/home", 0));
        assertFalse(frontier.enqueue("https://example.com/home", 0), "Duplicate exact URL must be rejected");
        assertFalse(frontier.enqueue("https://example.com/home#anchor", 1), "URL with fragment normalizing to /home must be rejected");
        assertEquals(1, frontier.size());
    }

    @Test
    @DisplayName("Should resolve relative links against base URL when enqueuing")
    void shouldResolveRelativeLinksWhenEnqueuing() {
        assertTrue(frontier.enqueue("/docs/overview.html", 1, "https://example.com/index.html"));
        Optional<CrawlTask> task = frontier.poll();
        assertTrue(task.isPresent());
        assertEquals("https://example.com/docs/overview.html", task.get().url());
    }
}
