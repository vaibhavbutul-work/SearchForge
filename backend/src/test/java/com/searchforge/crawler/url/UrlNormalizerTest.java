package com.searchforge.crawler.url;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizerTest {

    private UrlNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new UrlNormalizer();
    }

    @Test
    @DisplayName("Should resolve relative paths against base URL")
    void shouldResolveRelativeUrls() {
        String base = "https://example.com/docs/guides/intro.html";

        assertEquals(
                Optional.of("https://example.com/docs/guides/setup.html"),
                normalizer.normalize("setup.html", base)
        );
        assertEquals(
                Optional.of("https://example.com/docs/index.html"),
                normalizer.normalize("../index.html", base)
        );
        assertEquals(
                Optional.of("https://example.com/about"),
                normalizer.normalize("/about", base)
        );
    }

    @Test
    @DisplayName("Should strip URL fragments and normalize trailing slashes")
    void shouldStripFragmentAndTrailingSlash() {
        assertEquals(
                Optional.of("https://example.com/page"),
                normalizer.normalize("https://example.com/page#section-3", null)
        );
        assertEquals(
                Optional.of("https://example.com/docs"),
                normalizer.normalize("https://example.com/docs/", null)
        );
        assertEquals(
                Optional.of("https://example.com/"),
                normalizer.normalize("https://example.com/", null)
        );
    }

    @Test
    @DisplayName("Should lowercase scheme and hostname while preserving path casing")
    void shouldLowercaseSchemeAndHost() {
        assertEquals(
                Optional.of("http://example.com/CamelCasePath"),
                normalizer.normalize("HTTP://EXAMPLE.COM/CamelCasePath", null)
        );
    }

    @Test
    @DisplayName("Should strip default ports (80 for http, 443 for https) but keep custom ports")
    void shouldStripDefaultPorts() {
        assertEquals(
                Optional.of("http://example.com/page"),
                normalizer.normalize("http://example.com:80/page", null)
        );
        assertEquals(
                Optional.of("https://example.com/page"),
                normalizer.normalize("https://example.com:443/page", null)
        );
        assertEquals(
                Optional.of("http://example.com:8080/page"),
                normalizer.normalize("http://example.com:8080/page", null)
        );
    }

    @Test
    @DisplayName("Should reject non-HTTP schemes, mailto, javascript, and malformed strings")
    void shouldRejectNonHttpSchemes() {
        assertTrue(normalizer.normalize("javascript:alert(1)", "https://example.com").isEmpty());
        assertTrue(normalizer.normalize("mailto:dev@example.com", "https://example.com").isEmpty());
        assertTrue(normalizer.normalize("tel:+1234567890", "https://example.com").isEmpty());
        assertTrue(normalizer.normalize("ftp://files.example.com", null).isEmpty());
        assertTrue(normalizer.normalize("", null).isEmpty());
        assertTrue(normalizer.normalize("   ", null).isEmpty());
        assertTrue(normalizer.normalize(null, null).isEmpty());
    }

    @Test
    @DisplayName("Should correctly extract domain from valid URL")
    void shouldExtractDomain() {
        assertEquals(
                Optional.of("docs.example.com"),
                normalizer.extractDomain("https://DOCS.EXAMPLE.COM:8443/path/to/resource?q=1")
        );
        assertTrue(normalizer.extractDomain("not-a-valid-url").isEmpty());
    }
}
