package com.searchforge.crawler.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CrawlerPropertiesTest {

    @Autowired
    private CrawlerProperties properties;

    @Test
    @DisplayName("Should bind crawler configuration properties from test application.yml")
    void shouldBindPropertiesFromYaml() {
        assertNotNull(properties, "CrawlerProperties bean should be injected by Spring context");
        assertEquals("SearchForgeBot/1.0 (TestEnvironment)", properties.userAgent());
        assertEquals(List.of("http://localhost:8089/test"), properties.seedUrls());
        assertEquals(List.of("localhost"), properties.allowedDomains());
        assertEquals(1, properties.maxDepth());
        assertEquals(5, properties.maxPages());
        assertEquals(10L, properties.crawlDelayMs());
        assertEquals(1000, properties.connectTimeoutMs());
        assertEquals(1000, properties.readTimeoutMs());
        assertEquals(2, properties.concurrency());
    }

    @Test
    @DisplayName("Should enforce fallback defaults and defensive copying when initialized with invalid or null values")
    void shouldEnforceDefaultsAndImmutability() {
        CrawlerProperties fallback = new CrawlerProperties(
                null,
                null,
                null,
                -1,
                0,
                -50,
                0,
                -10,
                0,
                -1,
                -100L
        );

        assertEquals("SearchForgeBot/1.0", fallback.userAgent());
        assertTrue(fallback.seedUrls().isEmpty());
        assertTrue(fallback.allowedDomains().isEmpty());
        assertEquals(0, fallback.maxDepth());
        assertEquals(100, fallback.maxPages());
        assertEquals(1000L, fallback.crawlDelayMs());
        assertEquals(3000, fallback.connectTimeoutMs());
        assertEquals(5000, fallback.readTimeoutMs());
        assertEquals(4, fallback.concurrency());
        assertEquals(3, fallback.maxRetries());
        assertEquals(500L, fallback.retryDelayMs());

        CrawlerProperties fallbackExplicit = new CrawlerProperties(
                null, null, null, -1, 0, -50, 0, -10, -5, -1, -50L
        );
        assertEquals(4, fallbackExplicit.concurrency());
        assertEquals(3, fallbackExplicit.maxRetries());
        assertEquals(500L, fallbackExplicit.retryDelayMs());

        // Test immutability: List.copyOf prevents modification
        assertThrows(UnsupportedOperationException.class, () -> fallback.seedUrls().add("https://malicious.com"));
    }
}
