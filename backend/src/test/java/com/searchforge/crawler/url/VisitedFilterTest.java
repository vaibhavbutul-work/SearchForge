package com.searchforge.crawler.url;

import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitedFilterTest {

    @Mock
    private CrawledDocumentRepository repository;

    private VisitedFilter filter;

    @BeforeEach
    void setUp() {
        filter = new VisitedFilter(repository);
    }

    @Test
    @DisplayName("Should detect and mark new URLs in memory atomically")
    void shouldTrackUrlsInMemory() {
        String url = "https://example.com/page1";
        when(repository.existsByUrl(url)).thenReturn(false);

        assertFalse(filter.isVisited(url), "URL should initially be unvisited");

        assertTrue(filter.markVisited(url), "First visit should return true");
        assertTrue(filter.isVisited(url), "URL should now be marked as visited");
        assertFalse(filter.markVisited(url), "Second visit to same URL must return false");
        assertEquals(1, filter.getMemoryVisitedCount());
    }

    @Test
    @DisplayName("Should recognize URLs already persisted in database from previous crawls")
    void shouldRecognizeDatabasePersistedUrls() {
        String dbUrl = "https://example.com/persisted-page";
        when(repository.existsByUrl(dbUrl)).thenReturn(true);

        assertTrue(filter.isVisited(dbUrl), "Should report visited because URL exists in database");
        assertFalse(filter.markVisited(dbUrl), "Should return false because URL was already persisted");
    }

    @Test
    @DisplayName("Should handle null and blank URLs safely")
    void shouldHandleNullAndBlankSafely() {
        assertTrue(filter.isVisited(null));
        assertTrue(filter.isVisited("   "));
        assertFalse(filter.markVisited(null));
        assertFalse(filter.markVisited("   "));
    }

    @Test
    @DisplayName("Should clear in-memory state")
    void shouldClearMemoryState() {
        when(repository.existsByUrl(anyString())).thenReturn(false);
        filter.markVisited("https://example.com/1");
        filter.markVisited("https://example.com/2");
        assertEquals(2, filter.getMemoryVisitedCount());

        filter.clearMemory();
        assertEquals(0, filter.getMemoryVisitedCount());
    }
}
