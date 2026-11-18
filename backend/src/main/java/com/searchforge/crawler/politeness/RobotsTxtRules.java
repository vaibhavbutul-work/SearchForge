package com.searchforge.crawler.politeness;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable representation of parsed robots.txt rules for a specific domain.
 * Evaluates path accessibility according to RFC 9309 (Robots Exclusion Protocol).
 */
public record RobotsTxtRules(
        List<String> disallowedPrefixes,
        List<String> allowedPrefixes,
        Long crawlDelayMs,
        Instant fetchedAt
) {
    public RobotsTxtRules {
        disallowedPrefixes = disallowedPrefixes != null ? List.copyOf(disallowedPrefixes) : List.of();
        allowedPrefixes = allowedPrefixes != null ? List.copyOf(allowedPrefixes) : List.of();
        fetchedAt = fetchedAt != null ? fetchedAt : Instant.now();
    }

    /**
     * Factory method creating a permissive ruleset (used when robots.txt is 404 or missing).
     */
    public static RobotsTxtRules allowAll() {
        return new RobotsTxtRules(Collections.emptyList(), Collections.emptyList(), null, Instant.now());
    }

    /**
     * Factory method creating a fully restrictive ruleset (used on persistent 5xx errors).
     */
    public static RobotsTxtRules disallowAll() {
        return new RobotsTxtRules(List.of("/"), Collections.emptyList(), null, Instant.now());
    }

    /**
     * Determines if a given path is permitted for crawling according to RFC 9309.
     * Rule: Longest matching prefix between Allow and Disallow wins.
     * If matches are equal in length, Allow wins.
     *
     * @param path The URL path (e.g. "/admin/dashboard").
     * @return true if crawling is permitted; false if disallowed.
     */
    public boolean isAllowed(String path) {
        String testPath = (path == null || path.isBlank()) ? "/" : path.trim();

        int maxDisallowMatch = 0;
        for (String prefix : disallowedPrefixes) {
            if (!prefix.isEmpty() && testPath.startsWith(prefix)) {
                maxDisallowMatch = Math.max(maxDisallowMatch, prefix.length());
            }
        }

        int maxAllowMatch = 0;
        for (String prefix : allowedPrefixes) {
            if (!prefix.isEmpty() && testPath.startsWith(prefix)) {
                maxAllowMatch = Math.max(maxAllowMatch, prefix.length());
            }
        }

        // If no rules matched, path is allowed by default
        if (maxDisallowMatch == 0 && maxAllowMatch == 0) {
            return true;
        }

        // Longest match wins. If lengths are equal, Allow takes precedence.
        return maxAllowMatch >= maxDisallowMatch;
    }

    public Optional<Long> getCrawlDelayMs() {
        return Optional.ofNullable(crawlDelayMs);
    }
}
