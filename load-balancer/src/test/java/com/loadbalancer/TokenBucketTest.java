package com.loadbalancer;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    // ---------------------------------------------------------------------- //
    //  Basic construction
    // ---------------------------------------------------------------------- //

    @Test
    void newBucket_startsFullyLoaded() {
        TokenBucket bucket = new TokenBucket(10, 1.0);
        assertEquals(10, bucket.getAvailableTokens());
    }

    @Test
    void constructor_rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 1.0));
    }

    @Test
    void constructor_rejectsNonPositiveRefillRate() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, -5.0));
    }

    @Test
    void getCapacity_returnsConfiguredCapacity() {
        TokenBucket bucket = new TokenBucket(20, 5.0);
        assertEquals(20, bucket.getCapacity());
    }

    @Test
    void getRefillRate_returnsConfiguredRate() {
        TokenBucket bucket = new TokenBucket(10, 3.5);
        assertEquals(3.5, bucket.getRefillRatePerSecond());
    }

    // ---------------------------------------------------------------------- //
    //  Consumption
    // ---------------------------------------------------------------------- //

    @Test
    void tryConsume_returnsTrue_whenTokensAvailable() {
        TokenBucket bucket = new TokenBucket(5, 1.0);
        assertTrue(bucket.tryConsume());
    }

    @Test
    void tryConsume_decrementsAvailableTokens() {
        TokenBucket bucket = new TokenBucket(5, 1.0);
        bucket.tryConsume();
        assertEquals(4, bucket.getAvailableTokens());
    }

    @Test
    void tryConsume_drainsBucket_thenReturnsFalse() {
        TokenBucket bucket = new TokenBucket(3, 1.0);

        assertTrue(bucket.tryConsume()); // 2 left
        assertTrue(bucket.tryConsume()); // 1 left
        assertTrue(bucket.tryConsume()); // 0 left
        assertFalse(bucket.tryConsume()); // empty — should fail
    }

    @Test
    void tryConsume_emptyBucket_returns_zero_available_tokens() {
        TokenBucket bucket = new TokenBucket(2, 1.0);
        bucket.tryConsume();
        bucket.tryConsume();
        assertEquals(0, bucket.getAvailableTokens());
    }

    // ---------------------------------------------------------------------- //
    //  Refill via injectable clock
    // ---------------------------------------------------------------------- //

    @Test
    void refill_addsTokensOverTime() {
        // Start at T=0
        Instant start = Instant.ofEpochSecond(0);

        // After 5 seconds at 2 tokens/s a drained bucket of capacity 10 should
        // have min(10, 5*2) = 10 tokens again.
        MutableClock clock = new MutableClock(start);
        TokenBucket bucket = new TokenBucket(10, 2.0, clock);

        // Drain all 10 tokens
        for (int i = 0; i < 10; i++) {
            bucket.tryConsume();
        }
        assertEquals(0, bucket.getAvailableTokens());

        // Advance 5 seconds
        clock.advance(5_000);
        assertEquals(10, bucket.getAvailableTokens());
    }

    @Test
    void refill_doesNotExceedCapacity() {
        MutableClock clock = new MutableClock(Instant.ofEpochSecond(0));
        TokenBucket bucket = new TokenBucket(5, 100.0, clock); // very fast refill

        // Drain
        for (int i = 0; i < 5; i++) bucket.tryConsume();

        // Advance by 60 seconds — would add 6000 tokens, but cap is 5
        clock.advance(60_000);
        assertEquals(5, bucket.getAvailableTokens());
    }

    @Test
    void refill_partialSecond_addsPartialTokens() {
        MutableClock clock = new MutableClock(Instant.ofEpochSecond(0));
        TokenBucket bucket = new TokenBucket(10, 2.0, clock);

        // Drain all
        for (int i = 0; i < 10; i++) bucket.tryConsume();

        // Advance 500 ms — should restore 2 * 0.5 = 1 token
        clock.advance(500);
        assertEquals(1, bucket.getAvailableTokens());
    }

    // ---------------------------------------------------------------------- //
    //  Helper: mutable clock for deterministic time control
    // ---------------------------------------------------------------------- //

    private static class MutableClock extends Clock {
        private long epochMillis;

        MutableClock(Instant start) {
            this.epochMillis = start.toEpochMilli();
        }

        void advance(long millis) {
            epochMillis += millis;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(epochMillis);
        }
    }
}
