package com.searchforge.crawler.fetcher;

import com.searchforge.crawler.config.CrawlerProperties;
import com.searchforge.crawler.security.IpRangeValidator;
import com.searchforge.crawler.security.SafeDnsResolver;
import com.searchforge.security.SecurityProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Downloads HTML documents over HTTP/HTTPS using Jsoup with industrial SSRF safeguards.
 *
 * Implements defensive production safeguards:
 * 1. Strict pre-flight DNS and IP range validation (blocks loopback, RFC 1918 private, link-local, cloud metadata).
 * 2. Controlled redirect following (manual loop up to maxRedirects) re-validating every redirect target IP.
 * 3. Redirect loop / cycle detection.
 * 4. Strict Content-Type inspection (rejects binary/media files like PDF, PNG, ZIP).
 * 5. Socket connect & read timeouts to prevent thread hanging.
 * 6. Max response body size limit (2MB) to prevent JVM heap exhaustion.
 * 7. Resilient error handling (HTTP 4xx, 5xx, or network drops never crash the engine).
 */
@Component
public class DocumentFetcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentFetcher.class);

    // 2 Megabytes safety ceiling for HTML pages
    private static final int MAX_BODY_SIZE_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_MAX_REDIRECTS = 3;

    private final CrawlerProperties properties;
    private final SafeDnsResolver dnsResolver;
    private final int maxRedirects;

    @Autowired
    public DocumentFetcher(CrawlerProperties properties,
                           SafeDnsResolver dnsResolver,
                           @Nullable SecurityProperties securityProperties) {
        this.properties = properties;
        this.dnsResolver = dnsResolver;
        this.maxRedirects = (securityProperties != null && securityProperties.getCrawler() != null)
                ? securityProperties.getCrawler().getMaxRedirects()
                : DEFAULT_MAX_REDIRECTS;
    }

    public DocumentFetcher(CrawlerProperties properties, SafeDnsResolver dnsResolver) {
        this(properties, dnsResolver, null);
    }

    /**
     * Backwards-compatible constructor for standalone tests using default permissive DNS (allows WireMock on localhost).
     */
    public DocumentFetcher(CrawlerProperties properties) {
        this(properties, new SafeDnsResolver(new IpRangeValidator(false), Set.of()), null);
    }

    /**
     * Executes an HTTP GET request to download a webpage with SSRF pre-flight and redirect protections.
     *
     * @param url The target URL to fetch.
     * @return Optional containing FetchedDocument on success, or Optional.empty() on failure.
     */
    public Optional<FetchedDocument> fetch(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        String currentUrl = url.trim();
        int redirectCount = 0;
        Set<String> visitedUrls = new HashSet<>();
        visitedUrls.add(currentUrl);

        while (redirectCount <= maxRedirects) {
            // Pre-flight SSRF and DNS check on current URL
            if (!dnsResolver.isSafeUrl(currentUrl)) {
                log.warn("Blocked fetch of unsafe or prohibited URL: '{}'", currentUrl);
                return Optional.empty();
            }

            log.debug("Fetching HTTP document: {}", currentUrl);

            try {
                Connection.Response response = Jsoup.connect(currentUrl)
                        .userAgent(properties.userAgent())
                        .timeout(properties.readTimeoutMs())
                        .maxBodySize(MAX_BODY_SIZE_BYTES)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .followRedirects(false) // Manual redirect loop prevents SSRF redirect bypass
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .execute();

                int statusCode = response.statusCode();

                // Handle HTTP redirects (301, 302, 303, 307, 308)
                if (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308) {
                    redirectCount++;
                    if (redirectCount > maxRedirects) {
                        log.warn("Discarding URL '{}': exceeded max redirect limit ({})", url, maxRedirects);
                        return Optional.empty();
                    }

                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        log.warn("Discarding URL '{}': redirect status {} with missing Location header", currentUrl, statusCode);
                        return Optional.empty();
                    }

                    // Resolve relative redirect against current URL
                    String targetUrl;
                    try {
                        URI baseUri = new URI(currentUrl);
                        targetUrl = baseUri.resolve(location.trim()).toString();
                    } catch (Exception e) {
                        log.warn("Discarding URL '{}': invalid redirect target '{}'", currentUrl, location);
                        return Optional.empty();
                    }

                    // Prevent redirect loops
                    if (visitedUrls.contains(targetUrl)) {
                        log.warn("Discarding URL '{}': redirect cycle detected -> '{}'", url, targetUrl);
                        return Optional.empty();
                    }

                    log.debug("Following safe redirect (hop {}/{}) from '{}' to '{}'",
                            redirectCount, maxRedirects, currentUrl, targetUrl);
                    visitedUrls.add(targetUrl);
                    currentUrl = targetUrl;
                    continue;
                }

                // Only process successful HTTP 200 responses
                if (statusCode != 200) {
                    log.debug("Discarding URL '{}': returned non-200 HTTP status {}", currentUrl, statusCode);
                    return Optional.empty();
                }

                // Inspect Content-Type to reject binary/media payloads
                String contentType = response.contentType();
                if (contentType == null || (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml"))) {
                    log.debug("Discarding URL '{}': unsupported Content-Type '{}'", currentUrl, contentType);
                    return Optional.empty();
                }

                String body = response.body();
                if (body == null || body.isBlank()) {
                    log.debug("Discarding URL '{}': empty body", currentUrl);
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
                log.warn("Failed to fetch URL '{}': {}", currentUrl, e.getMessage());
                return Optional.empty();
            }
        }

        log.warn("Discarding URL '{}': exceeded redirect limit", url);
        return Optional.empty();
    }
}
