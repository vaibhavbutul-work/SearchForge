package com.searchforge.crawler.fetcher;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of a raw HTTP document fetched over the network.
 *
 * @param url         The canonical request URL.
 * @param statusCode  The HTTP response status code (e.g. 200).
 * @param contentType The Content-Type response header (e.g. "text/html; charset=UTF-8").
 * @param rawHtml     The raw HTML payload downloaded from the web server.
 * @param fetchedAt   Timestamp when the document was received.
 */
public record FetchedDocument(
        String url,
        int statusCode,
        String contentType,
        String rawHtml,
        Instant fetchedAt
) {
    public FetchedDocument {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(rawHtml, "rawHtml must not be null");
        fetchedAt = fetchedAt != null ? fetchedAt : Instant.now();
    }
}
