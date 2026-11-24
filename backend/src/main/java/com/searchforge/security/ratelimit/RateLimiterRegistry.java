package com.searchforge.security.ratelimit;

import com.searchforge.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and lifecycle manager for per-client TokenBucket rate limiters.
 *
 * Partitions buckets across request tiers (Search vs Admin) and client IPs.
 * Includes defensive capacity bounds to prevent memory leaks from high cardinality client IPs.
 */
@Component
public class RateLimiterRegistry {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterRegistry.class);
    private static final int MAX_REGISTRY_ENTRIES = 10_000;

    public enum Tier {
        SEARCH,
        ADMIN
    }

    private final SecurityProperties properties;
    private final Map<String, TokenBucketEntry> buckets = new ConcurrentHashMap<>();

    private record TokenBucketEntry(TokenBucket bucket, long lastAccessNanos) {}

    public RateLimiterRegistry(SecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Retrieves or instantiates the TokenBucket for a specific client IP and endpoint tier.
     *
     * @param clientIp Client IP address.
     * @param tier     Target API tier (SEARCH or ADMIN).
     * @return Active TokenBucket instance.
     */
    public TokenBucket getBucket(String clientIp, Tier tier) {
        String key = tier.name() + ":" + clientIp;
        long now = System.nanoTime();

        // Prevent unbounded memory growth under distributed scanning attacks
        if (buckets.size() > MAX_REGISTRY_ENTRIES) {
            pruneStaleBuckets();
        }

        TokenBucketEntry entry = buckets.compute(key, (k, existing) -> {
            if (existing != null) {
                return new TokenBucketEntry(existing.bucket(), now);
            }
            TokenBucket newBucket = createBucketForTier(tier);
            return new TokenBucketEntry(newBucket, now);
        });

        return entry.bucket();
    }

    /**
     * Resolves the true client IP address, safely extracting from proxy headers
     * (X-Forwarded-For, X-Real-IP) with fallback to remote address.
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // In a proxy chain, the first IP is the original client
            int commaIndex = xForwardedFor.indexOf(',');
            if (commaIndex != -1) {
                return xForwardedFor.substring(0, commaIndex).trim();
            }
            return xForwardedFor.trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
    }

    private TokenBucket createBucketForTier(Tier tier) {
        SecurityProperties.RateLimitSecurity config = properties.getRateLimit();
        long burst;
        double refillPerSecond;

        if (tier == Tier.ADMIN) {
            burst = config.getAdminBurstCapacity();
            refillPerSecond = config.getAdminRequestsPerMinute() / 60.0;
        } else {
            burst = config.getSearchBurstCapacity();
            refillPerSecond = config.getSearchRequestsPerMinute() / 60.0;
        }

        return new TokenBucket(burst, refillPerSecond);
    }

    private synchronized void pruneStaleBuckets() {
        if (buckets.size() <= MAX_REGISTRY_ENTRIES) {
            return;
        }
        log.debug("RateLimiterRegistry size ({}) exceeded threshold. Pruning inactive buckets.", buckets.size());
        long cutoff = System.nanoTime() - (15L * 60 * 1_000_000_000L); // 15 minutes inactive
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessNanos() < cutoff);
    }

    /**
     * Clears all registered buckets (useful in test setups).
     */
    public void reset() {
        buckets.clear();
    }

    public int size() {
        return buckets.size();
    }
}
