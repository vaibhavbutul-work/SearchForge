package com.searchforge.crawler.politeness;

import com.searchforge.crawler.config.CrawlerProperties;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for fetching, parsing, caching, and evaluating robots.txt rules.
 * Strictly adheres to the Robots Exclusion Protocol (RFC 9309).
 */
@Component
public class RobotsTxtService {

    private static final Logger log = LoggerFactory.getLogger(RobotsTxtService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final CrawlerProperties properties;
    private final com.searchforge.crawler.security.SafeDnsResolver dnsResolver;
    private final Map<String, RobotsTxtRules> cache = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public RobotsTxtService(CrawlerProperties properties, com.searchforge.crawler.security.SafeDnsResolver dnsResolver) {
        this.properties = properties;
        this.dnsResolver = dnsResolver;
    }

    public RobotsTxtService(CrawlerProperties properties) {
        this(properties, new com.searchforge.crawler.security.SafeDnsResolver(new com.searchforge.crawler.security.IpRangeValidator(false), Set.of()));
    }

    /**
     * Checks if the given URL is permitted for crawling according to the domain's robots.txt.
     *
     * @param targetUrl The full canonical URL to check.
     * @return true if allowed; false if disallowed.
     */
    public boolean isAllowed(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(targetUrl.trim());
            String domain = uri.getHost();
            if (domain == null || domain.isBlank()) {
                return false;
            }

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                path = path + "?" + uri.getRawQuery();
            }

            RobotsTxtRules rules = getRulesForDomain(uri.getScheme(), domain, uri.getPort());
            boolean allowed = rules.isAllowed(path);

            if (!allowed) {
                log.debug("URL '{}' disallowed by robots.txt rules for domain '{}'", targetUrl, domain);
            }
            return allowed;
        } catch (URISyntaxException e) {
            log.warn("Invalid URL syntax when checking robots.txt for '{}': {}", targetUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves any custom Crawl-delay (in milliseconds) declared in the domain's robots.txt.
     *
     * @param targetUrl Any URL belonging to the domain.
     * @return Optional containing crawl delay in milliseconds, if specified.
     */
    public Optional<Long> getCrawlDelayMs(String targetUrl) {
        try {
            URI uri = new URI(targetUrl.trim());
            String domain = uri.getHost();
            if (domain == null) {
                return Optional.empty();
            }
            RobotsTxtRules rules = getRulesForDomain(uri.getScheme(), domain, uri.getPort());
            return rules.getCrawlDelayMs();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves rules from the in-memory cache or downloads them if missing or expired.
     */
    RobotsTxtRules getRulesForDomain(String scheme, String domain, int port) {
        String cacheKey = domain.toLowerCase(Locale.ROOT);
        RobotsTxtRules existing = cache.get(cacheKey);

        if (existing != null && Duration.between(existing.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return existing;
        }

        RobotsTxtRules fetchedRules = fetchAndParseRobotsTxt(scheme, domain, port);
        cache.put(cacheKey, fetchedRules);
        return fetchedRules;
    }

    private RobotsTxtRules fetchAndParseRobotsTxt(String scheme, String domain, int port) {
        String baseScheme = (scheme != null && !scheme.isBlank()) ? scheme.toLowerCase(Locale.ROOT) : "https";
        String robotsUrl = baseScheme + "://" + domain + (port > 0 ? ":" + port : "") + "/robots.txt";

        if (dnsResolver != null && !dnsResolver.isSafeUrl(robotsUrl)) {
            log.warn("Blocked SSRF attempt: Prohibited or unsafe robots.txt URL '{}'", robotsUrl);
            return RobotsTxtRules.disallowAll();
        }

        log.debug("Fetching robots.txt from: {}", robotsUrl);

        try {
            Connection.Response response = Jsoup.connect(robotsUrl)
                    .userAgent(properties.userAgent())
                    .timeout(properties.connectTimeoutMs())
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .execute();

            int statusCode = response.statusCode();

            // 404 (Not Found) or 410 (Gone): site does not restrict crawling
            if (statusCode == 404 || statusCode == 410) {
                log.debug("robots.txt returned HTTP {} for '{}'. Permitting all crawling.", statusCode, domain);
                return RobotsTxtRules.allowAll();
            }

            // 5xx Server errors: fail-safe, allow with standard delay
            if (statusCode >= 500) {
                log.warn("robots.txt returned server error HTTP {} for '{}'. Falling back to allowAll.", statusCode, domain);
                return RobotsTxtRules.allowAll();
            }

            if (statusCode == 200) {
                return parseRobotsTxtContent(response.body());
            }

            return RobotsTxtRules.allowAll();
        } catch (IOException e) {
            log.warn("Unable to fetch robots.txt for '{}' ({}): {}. Permitting all.", domain, robotsUrl, e.getMessage());
            return RobotsTxtRules.allowAll();
        }
    }

    /**
     * Parses the raw robots.txt text content into a structured RobotsTxtRules record.
     */
    RobotsTxtRules parseRobotsTxtContent(String content) {
        if (content == null || content.isBlank()) {
            return RobotsTxtRules.allowAll();
        }

        List<String> botDisallow = new ArrayList<>();
        List<String> botAllow = new ArrayList<>();
        Long botCrawlDelay = null;

        List<String> wildcardDisallow = new ArrayList<>();
        List<String> wildcardAllow = new ArrayList<>();
        Long wildcardCrawlDelay = null;

        boolean inTargetBotSection = false;
        boolean inWildcardSection = false;
        boolean foundTargetBotSection = false;

        String botIdentifier = "searchforgebot";

        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            // Strip comments
            int commentIndex = line.indexOf('#');
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex);
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }

            String directive = line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colonIndex + 1).trim();

            if ("user-agent".equals(directive)) {
                String agent = value.toLowerCase(Locale.ROOT);
                if (agent.contains(botIdentifier)) {
                    inTargetBotSection = true;
                    inWildcardSection = false;
                    foundTargetBotSection = true;
                } else if ("*".equals(agent)) {
                    inWildcardSection = true;
                    inTargetBotSection = false;
                } else {
                    inTargetBotSection = false;
                    inWildcardSection = false;
                }
            } else if ("disallow".equals(directive)) {
                if (!value.isEmpty()) {
                    if (inTargetBotSection) {
                        botDisallow.add(value);
                    } else if (inWildcardSection) {
                        wildcardDisallow.add(value);
                    }
                }
            } else if ("allow".equals(directive)) {
                if (!value.isEmpty()) {
                    if (inTargetBotSection) {
                        botAllow.add(value);
                    } else if (inWildcardSection) {
                        wildcardAllow.add(value);
                    }
                }
            } else if ("crawl-delay".equals(directive)) {
                try {
                    long delaySec = Long.parseLong(value);
                    long delayMs = delaySec * 1000L;
                    if (inTargetBotSection) {
                        botCrawlDelay = delayMs;
                    } else if (inWildcardSection) {
                        wildcardCrawlDelay = delayMs;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed crawl-delay values
                }
            }
        }

        // Specific bot rules take full precedence over generic wildcard rules
        if (foundTargetBotSection && (!botDisallow.isEmpty() || !botAllow.isEmpty() || botCrawlDelay != null)) {
            return new RobotsTxtRules(botDisallow, botAllow, botCrawlDelay, Instant.now());
        }

        return new RobotsTxtRules(wildcardDisallow, wildcardAllow, wildcardCrawlDelay, Instant.now());
    }

    /**
     * Clears the in-memory cache (primarily for tests).
     */
    public void clearCache() {
        cache.clear();
    }
}
