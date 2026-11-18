package com.searchforge.crawler.parser;

import com.searchforge.crawler.fetcher.FetchedDocument;
import com.searchforge.crawler.url.UrlNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlParserTest {

    private HtmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new HtmlParser(new UrlNormalizer());
    }

    @Test
    @DisplayName("Should extract title and clean readable text from HTML")
    void shouldExtractTitleAndCleanText() {
        String html = """
                <!DOCTYPE html>
                <html>
                  <head>
                    <title>Java 21 Overview - SearchForge</title>
                  </head>
                  <body>
                    <h1>Java 21 Features</h1>
                    <p>Java 21 delivers Virtual Threads and Pattern Matching.</p>
                  </body>
                </html>
                """;

        FetchedDocument fetched = new FetchedDocument("https://example.com/java21", 200, "text/html", html, Instant.now());
        ParsedDocument parsed = parser.parse(fetched);

        assertEquals("Java 21 Overview - SearchForge", parsed.title());
        assertEquals("example.com", parsed.domain());
        assertTrue(parsed.cleanText().contains("Java 21 Features"));
        assertTrue(parsed.cleanText().contains("Java 21 delivers Virtual Threads and Pattern Matching."));
    }

    @Test
    @DisplayName("Should strip scripts, styles, navigation, and footer noise from body text")
    void shouldStripNoiseTags() {
        String html = """
                <html>
                  <head>
                    <title>Article Page</title>
                    <style>.hidden { display: none; } p { color: blue; }</style>
                  </head>
                  <body>
                    <header><a href="/home">Header Logo</a></header>
                    <nav><a href="/menu">Navigation Menu Items</a></nav>
                    <script type="text/javascript">
                      const tracker = "analytics.user.id";
                      window.onload = function() { console.log("tracked"); };
                    </script>
                    <main>
                      <h2>Valuable Knowledge</h2>
                      <p>This is the core content that should be indexed by the search engine.</p>
                    </main>
                    <aside>Related Sponsored Ads</aside>
                    <footer>Copyright 2026 Example Corp. All rights reserved.</footer>
                  </body>
                </html>
                """;

        FetchedDocument fetched = new FetchedDocument("https://example.com/article", 200, "text/html", html, Instant.now());
        ParsedDocument parsed = parser.parse(fetched);

        // Core text is present
        assertTrue(parsed.cleanText().contains("Valuable Knowledge"));
        assertTrue(parsed.cleanText().contains("This is the core content that should be indexed by the search engine."));

        // Noise and boilerplate are completely stripped
        assertFalse(parsed.cleanText().contains("Header Logo"), "header should be stripped");
        assertFalse(parsed.cleanText().contains("Navigation Menu Items"), "nav should be stripped");
        assertFalse(parsed.cleanText().contains("analytics.user.id"), "script should be stripped");
        assertFalse(parsed.cleanText().contains("color: blue"), "style should be stripped");
        assertFalse(parsed.cleanText().contains("Related Sponsored Ads"), "aside should be stripped");
        assertFalse(parsed.cleanText().contains("All rights reserved"), "footer should be stripped");
    }

    @Test
    @DisplayName("Should discover outbound links and resolve relative paths to absolute URLs")
    void shouldExtractAndResolveOutboundLinks() {
        String html = """
                <html>
                  <body>
                    <a href="/about">About Us</a>
                    <a href="guides/setup.html">Setup Guide</a>
                    <a href="https://external-resource.org/docs">External Docs</a>
                    <a href="https://example.com/contact#form">Contact Form</a>
                  </body>
                </html>
                """;

        FetchedDocument fetched = new FetchedDocument("https://example.com/docs/index.html", 200, "text/html", html, Instant.now());
        ParsedDocument parsed = parser.parse(fetched);

        List<String> links = parsed.outboundLinks();
        assertEquals(4, links.size());

        // Relative paths resolved to absolute URLs
        assertTrue(links.contains("https://example.com/about"));
        assertTrue(links.contains("https://example.com/docs/guides/setup.html"));
        assertTrue(links.contains("https://external-resource.org/docs"));
        assertTrue(links.contains("https://example.com/contact#form"));
    }

    @Test
    @DisplayName("Should compute accurate, 64-character SHA-256 fingerprint for duplicate detection")
    void shouldComputeConsistentSha256() {
        String text1 = "Exact same article content";
        String text2 = "Exact same article content";
        String text3 = "Different article content";

        String hash1 = parser.computeSha256(text1);
        String hash2 = parser.computeSha256(text2);
        String hash3 = parser.computeSha256(text3);

        assertEquals(64, hash1.length(), "SHA-256 hex string must be exactly 64 characters");
        assertEquals(hash1, hash2, "Identical content must produce identical hash");
        assertNotEquals(hash1, hash3, "Different content must produce different hash");
    }

    @Test
    @DisplayName("Should fallback to <h1> or 'Untitled' when <title> tag is missing")
    void shouldFallbackTitleWhenMissing() {
        String htmlWithH1 = "<html><body><h1>Primary Heading</h1><p>Text</p></body></html>";
        FetchedDocument fetchedH1 = new FetchedDocument("https://example.com/h1", 200, "text/html", htmlWithH1, Instant.now());
        assertEquals("Primary Heading", parser.parse(fetchedH1).title());

        String emptyHtml = "<html><body><p>Just text</p></body></html>";
        FetchedDocument fetchedEmpty = new FetchedDocument("https://example.com/empty", 200, "text/html", emptyHtml, Instant.now());
        assertEquals("Untitled", parser.parse(fetchedEmpty).title());
    }
}
