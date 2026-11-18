package com.searchforge.crawler.parser;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Structured document output produced by HtmlParser.
 * Contains extracted metadata, clean readable text, SHA-256 fingerprint, and discovered outbound links.
 *
 * @param url           Canonical URL of the parsed webpage.
 * @param domain        Host/domain of the webpage.
 * @param title         Extracted page title.
 * @param cleanText     Readable text body with HTML tags, scripts, and navigation stripped.
 * @param contentHash   SHA-256 hex digest of the clean text.
 * @param httpStatusCode The HTTP status code received (200).
 * @param outboundLinks Discovered hyperlinks (resolved to absolute URLs).
 * @param crawledAt     Timestamp of parsing.
 */
public record ParsedDocument(
        String url,
        String domain,
        String title,
        String cleanText,
        String contentHash,
        int httpStatusCode,
        List<String> outboundLinks,
        Instant crawledAt
) {
    public ParsedDocument {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        outboundLinks = outboundLinks != null ? List.copyOf(outboundLinks) : List.of();
        crawledAt = crawledAt != null ? crawledAt : Instant.now();
    }
}
