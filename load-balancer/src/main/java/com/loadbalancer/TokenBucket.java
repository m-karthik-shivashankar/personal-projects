package com.loadbalancer;

import java.time.Clock;

/**
 * Thread-safe token-bucket implementation used to rate-limit requests to a
 * single backend server.
 *
 * <p>Tokens accumulate at {@code refillRatePerSecond} up to {@code capacity}.
 * Each call to {@link #tryConsume()} attempts to consume one token. If a token
 * is available the call returns {@code true} and the token is deducted;
 * otherwise it returns {@code false} immediately (no blocking).
 */
public class TokenBucket {

    private final long capacity;
    private final double refillRatePerSecond;
    private final Clock clock;

    private double tokens;
    private long lastRefillNanos;

    /**
     * Creates a new token bucket that starts full.
     *
     * @param capacity             maximum number of tokens (burst size)
     * @param refillRatePerSecond  tokens added per second (sustain rate); must be &gt; 0
     */
    public TokenBucket(long capacity, double refillRatePerSecond) {
        this(capacity, refillRatePerSecond, Clock.systemUTC());
    }

    /**
     * Package-private constructor that accepts an injectable {@link Clock} for
     * deterministic unit tests.
     */
    TokenBucket(long capacity, double refillRatePerSecond, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0, got: " + capacity);
        }
        if (refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be > 0, got: " + refillRatePerSecond);
        }
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefillNanos = clock.instant().toEpochMilli() * 1_000_000L;
    }

    /**
     * Attempts to consume one token.
     *
     * @return {@code true} if a token was available and consumed; {@code false}
     *         if the bucket is empty
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
     * Returns the number of tokens currently available (rounded down to the
     * nearest whole token).
     */
    public synchronized long getAvailableTokens() {
        refill();
        return (long) tokens;
    }

    /** Returns the maximum capacity of this bucket. */
    public long getCapacity() {
        return capacity;
    }

    /** Returns the sustained refill rate in tokens per second. */
    public double getRefillRatePerSecond() {
        return refillRatePerSecond;
    }

    // --------------------------------------------------------------------- //
    //  Internal helpers
    // --------------------------------------------------------------------- //

    private void refill() {
        long nowNanos = clock.instant().toEpochMilli() * 1_000_000L;
        double elapsedSeconds = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds > 0) {
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillRatePerSecond);
            lastRefillNanos = nowNanos;
        }
    }
}
