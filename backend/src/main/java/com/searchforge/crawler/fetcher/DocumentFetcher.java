package com.searchforge.crawler.fetcher;

import com.searchforge.crawler.config.CrawlerProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Downloads HTML documents over HTTP/HTTPS using Jsoup.
 *
 * Implements defensive production safeguards:
 * 1. Strict Content-Type inspection (rejects binary/media files like PDF, PNG, ZIP).
 * 2. Socket connect & read timeouts to prevent thread hanging.
 * 3. Max response body size limit (2MB) to prevent JVM heap exhaustion.
 * 4. Resilient error handling (HTTP 4xx, 5xx, or network drops never crash the engine).
 */
@Component
public class DocumentFetcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentFetcher.class);

    // 2 Megabytes safety ceiling for HTML pages
    private static final int MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024;

    private final CrawlerProperties properties;

    public DocumentFetcher(CrawlerProperties properties) {
        this.properties = properties;
    }

    /**
     * Executes an HTTP GET request to download a webpage.
     *
     * @param url The target URL to fetch.
     * @return Optional containing FetchedDocument on success, or Optional.empty() on failure.
     */
    public Optional<FetchedDocument> fetch(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        log.debug("Fetching HTTP document: {}", url);

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(properties.userAgent())
                    .timeout(properties.readTimeoutMs())
                    .maxBodySize(MAX_BODY_SIZE_BYTES)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute();

            int statusCode = response.statusCode();

            // Only process successful HTTP 200 responses
            if (statusCode != 200) {
                log.debug("Discarding URL '{}': returned non-200 HTTP status {}", url, statusCode);
                return Optional.empty();
            }

            // Inspect Content-Type to reject binary/media payloads
            String contentType = response.contentType();
            if (contentType == null || (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml"))) {
                log.debug("Discarding URL '{}': unsupported Content-Type '{}'", url, contentType);
                return Optional.empty();
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                log.debug("Discarding URL '{}': empty body", url);
                return Optional.empty();
            }

            return Optional.of(new FetchedDocument(
                    url,
                    statusCode,
                    contentType,
                    body,
                    Instant.now()
            ));

        } catch (IOException e) {
            log.warn("Failed to fetch URL '{}': {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
