package com.searchforge.crawler.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Normalizes (canonicalizes) URLs to prevent duplicate crawling and resolve relative links.
 * Follows RFC 3986 canonicalization recommendations.
 */
@Component
public class UrlNormalizer {

    private static final Logger log = LoggerFactory.getLogger(UrlNormalizer.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    /**
     * Normalizes a raw URL string against an optional base URL.
     *
     * Steps performed:
     * 1. Resolves relative links against the base URL (if provided).
     * 2. Validates that the scheme is HTTP or HTTPS.
     * 3. Lowercases the scheme and host.
     * 4. Strips URL fragments (#anchor).
     * 5. Removes default ports (80 for http, 443 for https).
     * 6. Trims trailing slashes for non-root paths.
     *
     * @param rawUrl  The raw hyperlink extracted from HTML or user input.
     * @param baseUrl The base URL of the document where the link was found (optional, may be null).
     * @return Optional containing the normalized canonical URL, or Optional.empty() if invalid.
     */
    public Optional<String> normalize(String rawUrl, String baseUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawUrl.trim();

        // Fast-path filter for common non-HTTP URI schemes
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") ||
                lower.startsWith("tel:") || lower.startsWith("data:") || lower.startsWith("ftp:")) {
            return Optional.empty();
        }

        try {
            URI resolvedUri;
            if (baseUrl != null && !baseUrl.isBlank()) {
                URI base = new URI(baseUrl.trim());
                resolvedUri = base.resolve(trimmed);
            } else {
                resolvedUri = new URI(trimmed);
            }

            // Must have a valid scheme and host
            String scheme = resolvedUri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }

            String host = resolvedUri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }

            // Lowercase scheme and host
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            String normalizedHost = host.toLowerCase(Locale.ROOT);

            // Strip default ports
            int port = resolvedUri.getPort();
            if ((port == 80 && "http".equals(normalizedScheme)) || (port == 443 && "https".equals(normalizedScheme))) {
                port = -1;
            }

            // Clean path: normalize trailing slash
            String path = resolvedUri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // Reconstruct URI without fragment (#anchor)
            URI canonical = new URI(
                    normalizedScheme,
                    resolvedUri.getUserInfo(),
                    normalizedHost,
                    port,
                    path,
                    resolvedUri.getQuery(),
                    null // Strip fragment
            );

            return Optional.of(canonical.toASCIIString());
        } catch (URISyntaxException | IllegalArgumentException e) {
            log.debug("Discarding malformed URL '{}' (base: '{}'): {}", rawUrl, baseUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the canonical lowercase hostname/domain from a URL.
     *
     * @param url The URL string.
     * @return Optional containing the domain, or Optional.empty() if invalid.
     */
    public Optional<String> extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return Optional.of(host.toLowerCase(Locale.ROOT));
            }
            return Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
