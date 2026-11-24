package com.searchforge.security.ratelimit;

/**
 * Thread-safe implementation of the Token Bucket rate limiting algorithm.
 *
 * Uses monotonic nanosecond timing (System.nanoTime) to remain resilient against
 * system clock jumps, NTP synchronizations, and leap seconds.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillTokensPerSecond;
    private double tokens;
    private long lastRefillNanos;

    /**
     * Initializes a TokenBucket with burst capacity and refill rate.
     *
     * @param capacity              Maximum token capacity (burst allowance).
     * @param refillTokensPerSecond Refill rate in tokens per second.
     */
    public TokenBucket(long capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive: " + refillTokensPerSecond);
        }
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Attempts to consume a single token.
     *
     * @return true if a token was available and consumed; false if rate limit is exceeded.
     */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * Returns the current number of available tokens rounded down to whole integers.
     */
    public synchronized long getRemainingTokens() {
        refill();
        return (long) Math.floor(tokens);
    }

    /**
     * Calculates the estimated duration in seconds until at least one token becomes available.
     * Used for populating standard HTTP 'Retry-After' response headers.
     */
    public synchronized long getSecondsUntilNextRefill() {
        refill();
        if (tokens >= 1.0) {
            return 0;
        }
        double needed = 1.0 - tokens;
        long seconds = (long) Math.ceil(needed / refillTokensPerSecond);
        return Math.max(1, seconds);
    }

    /**
     * Refills tokens based on elapsed nanoseconds since last calculation.
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos > 0) {
            double tokensToAdd = (elapsedNanos / 1_000_000_000.0) * refillTokensPerSecond;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillNanos = now;
        }
    }

    public long getCapacity() {
        return capacity;
    }

    public double getRefillTokensPerSecond() {
        return refillTokensPerSecond;
    }
}
