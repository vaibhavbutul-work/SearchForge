package com.searchforge.security.ratelimit;

import com.searchforge.security.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * High-performance Web Filter enforcing Token Bucket rate limiting on API endpoints.
 *
 * Runs before authentication and controller handlers to protect SearchForge from:
 * 1. Algorithmic Denial-of-Service and query flooding on search/suggest/autocomplete endpoints.
 * 2. High-frequency brute-force attempts on administrative endpoints and API keys.
 * 3. Infrastructure and memory starvation.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiterRegistry registry;
    private final SecurityProperties properties;

    public RateLimitingFilter(RateLimiterRegistry registry, SecurityProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // If rate limiting is disabled globally in configuration
        if (properties == null || properties.getRateLimit() == null || !properties.getRateLimit().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Only enforce on API endpoints (bypass actuator, swagger, static resources)
        if (path == null || !path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiterRegistry.Tier tier = isAdminEndpoint(path)
                ? RateLimiterRegistry.Tier.ADMIN
                : RateLimiterRegistry.Tier.SEARCH;

        String clientIp = registry.resolveClientIp(request);
        TokenBucket bucket = registry.getBucket(clientIp, tier);

        if (bucket.tryConsume()) {
            // Request permitted: attach standard rate limiting telemetry headers
            response.setHeader(HEADER_LIMIT, String.valueOf(bucket.getCapacity()));
            response.setHeader(HEADER_REMAINING, String.valueOf(bucket.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Request throttled: reject with HTTP 429 Too Many Requests
            long retryAfter = bucket.getSecondsUntilNextRefill();
            log.warn("Rate limit exceeded for client IP '{}' on '{}' [tier={}] - rejecting with 429 (Retry-After: {}s)",
                    clientIp, path, tier, retryAfter);

            response.setStatus(429); // HttpStatus.TOO_MANY_REQUESTS
            response.setHeader(HEADER_LIMIT, String.valueOf(bucket.getCapacity()));
            response.setHeader(HEADER_REMAINING, "0");
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            String json = String.format("""
                    {
                      "timestamp": "%s",
                      "status": 429,
                      "error": "Too Many Requests",
                      "message": "Rate limit exceeded. Try again in %d seconds.",
                      "path": "%s"
                    }
                    """, Instant.now(), retryAfter, path);

            response.getWriter().write(json);
        }
    }

    private boolean isAdminEndpoint(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/v1/crawler") ||
               path.startsWith("/api/v1/lifecycle") ||
               path.equals("/api/v1/index/build") ||
               path.equals("/api/v1/index/rebuild") ||
               path.equals("/api/v1/index/warmup");
    }
}
