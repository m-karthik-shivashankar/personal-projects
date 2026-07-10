package com.loadbalancer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedLoadBalancerTest {

    // Use a fixed seed so weighted-distribution assertions are deterministic.
    private static final long SEED = 42L;

    private WeightedLoadBalancer lb;

    @BeforeEach
    void setUp() {
        lb = new WeightedLoadBalancer(100, 10.0, new Random(SEED));
    }

    // ---------------------------------------------------------------------- //
    //  addServer / getServerCount
    // ---------------------------------------------------------------------- //

    @Test
    void addServer_increasesCount() {
        assertEquals(0, lb.getServerCount());
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        assertEquals(1, lb.getServerCount());
        lb.addServer(new Server("s2", "localhost", 8002, 2));
        assertEquals(2, lb.getServerCount());
    }

    @Test
    void addServer_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> lb.addServer(null));
    }

    @Test
    void addServer_rejectsDuplicateId() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        assertThrows(IllegalArgumentException.class,
                () -> lb.addServer(new Server("s1", "other-host", 9999, 2)));
    }

    // ---------------------------------------------------------------------- //
    //  removeServer
    // ---------------------------------------------------------------------- //

    @Test
    void removeServer_decreasesCount() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        lb.addServer(new Server("s2", "localhost", 8002, 1));
        lb.removeServer("s1");
        assertEquals(1, lb.getServerCount());
    }

    @Test
    void removeServer_removesTokenBucket() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        lb.removeServer("s1");
        assertNull(lb.getTokenBucket("s1"));
    }

    @Test
    void removeServer_throwsForUnknownId() {
        assertThrows(LoadBalancerException.class, () -> lb.removeServer("unknown"));
    }

    @Test
    void removeServer_rejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> lb.removeServer(null));
    }

    // ---------------------------------------------------------------------- //
    //  getNextServer — empty / no healthy servers
    // ---------------------------------------------------------------------- //

    @Test
    void getNextServer_returnsEmpty_whenNoServersRegistered() {
        Optional<Server> result = lb.getNextServer();
        assertFalse(result.isPresent());
    }

    @Test
    void getNextServer_returnsEmpty_whenAllServersUnhealthy() {
        Server s1 = new Server("s1", "localhost", 8001, 1);
        s1.markUnhealthy();
        lb.addServer(s1);

        Optional<Server> result = lb.getNextServer();
        assertFalse(result.isPresent());
    }

    // ---------------------------------------------------------------------- //
    //  getNextServer — normal selection
    // ---------------------------------------------------------------------- //

    @Test
    void getNextServer_returnsSingleHealthyServer() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        Optional<Server> result = lb.getNextServer();

        assertTrue(result.isPresent());
        assertEquals("s1", result.get().getId());
    }

    @Test
    void getNextServer_onlyReturnsHealthyServers() {
        Server s1 = new Server("s1", "localhost", 8001, 1);
        Server s2 = new Server("s2", "localhost", 8002, 10); // heavy weight but unhealthy
        s2.markUnhealthy();
        lb.addServer(s1);
        lb.addServer(s2);

        // Run many times — only s1 should ever be returned
        for (int i = 0; i < 50; i++) {
            Optional<Server> result = lb.getNextServer();
            assertTrue(result.isPresent());
            assertEquals("s1", result.get().getId(),
                    "Unhealthy server s2 should never be selected");
        }
    }

    @Test
    void getNextServer_respectsWeights_higherWeightSelectedMoreOften() {
        // s2 has 4× the weight of s1 — after many draws it should dominate.
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        lb.addServer(new Server("s2", "localhost", 8002, 4));

        int countS1 = 0, countS2 = 0;
        int iterations = 5000;
        // Use a fresh LB with many tokens so token exhaustion doesn't interfere.
        WeightedLoadBalancer freshLb =
                new WeightedLoadBalancer(1_000_000, 100.0, new Random(SEED));
        freshLb.addServer(new Server("s1", "localhost", 8001, 1));
        freshLb.addServer(new Server("s2", "localhost", 8002, 4));

        for (int i = 0; i < iterations; i++) {
            Optional<Server> r = freshLb.getNextServer();
            assertTrue(r.isPresent());
            if ("s1".equals(r.get().getId())) countS1++;
            else countS2++;
        }

        // s2 should receive ~80 % of traffic (weight 4 out of 5 total).
        // Allow generous tolerance (±10 %) for the PRNG.
        double s2Ratio = (double) countS2 / iterations;
        assertTrue(s2Ratio > 0.70,
                "Expected s2 ratio > 70 %, got " + String.format("%.2f", s2Ratio * 100) + "%");
        assertTrue(s2Ratio < 0.90,
                "Expected s2 ratio < 90 %, got " + String.format("%.2f", s2Ratio * 100) + "%");
    }

    // ---------------------------------------------------------------------- //
    //  Token bucket integration
    // ---------------------------------------------------------------------- //

    @Test
    void getNextServer_consumesTokenFromBucket() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        long before = lb.getTokenBucket("s1").getAvailableTokens();
        lb.getNextServer();
        long after = lb.getTokenBucket("s1").getAvailableTokens();
        assertEquals(before - 1, after);
    }

    @Test
    void getNextServer_returnsEmpty_whenTokensExhausted() {
        // Tiny bucket with capacity 2
        WeightedLoadBalancer tinyLb = new WeightedLoadBalancer(2, 0.001, new Random(SEED));
        tinyLb.addServer(new Server("s1", "localhost", 8001, 1));

        // Drain the 2 tokens
        assertTrue(tinyLb.getNextServer().isPresent());
        assertTrue(tinyLb.getNextServer().isPresent());

        // Third request — no tokens left, no refill yet
        Optional<Server> result = tinyLb.getNextServer();
        assertFalse(result.isPresent(), "Expected empty when all token buckets exhausted");
    }

    @Test
    void getNextServer_fallsBackToOtherServer_whenFirstBucketExhausted() {
        // s1 has capacity 1, s2 has capacity 100
        WeightedLoadBalancer mixedLb =
                new WeightedLoadBalancer(1, 0.001, new Random(SEED));
        mixedLb.addServer(new Server("s1", "localhost", 8001, 1));
        mixedLb.addServer(new Server("s2", "localhost", 8002, 1));

        // Drain s1's single token directly
        mixedLb.getTokenBucket("s1").tryConsume();

        // Over many requests the only server with tokens is s2
        // (s1 has none; s2 starts full with 1 token).
        // At least one of the next requests should hit s2.
        boolean s2Hit = false;
        for (int i = 0; i < 5; i++) {
            Optional<Server> r = mixedLb.getNextServer();
            if (r.isPresent() && "s2".equals(r.get().getId())) {
                s2Hit = true;
                break;
            }
        }
        assertTrue(s2Hit, "Expected at least one request to fall back to s2");
    }

    // ---------------------------------------------------------------------- //
    //  getServers snapshot
    // ---------------------------------------------------------------------- //

    @Test
    void getServers_returnsUnmodifiableSnapshot() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        assertThrows(UnsupportedOperationException.class,
                () -> lb.getServers().add(new Server("s2", "localhost", 8002, 1)));
    }

    @Test
    void getServers_reflectsCurrentState() {
        lb.addServer(new Server("s1", "localhost", 8001, 1));
        lb.addServer(new Server("s2", "localhost", 8002, 2));
        lb.removeServer("s1");

        assertEquals(1, lb.getServers().size());
        assertEquals("s2", lb.getServers().get(0).getId());
    }

    // ---------------------------------------------------------------------- //
    //  Health-state changes after registration
    // ---------------------------------------------------------------------- //

    @Test
    void serverMarkedUnhealthyAfterRegistration_isNotSelected() {
        Server s1 = new Server("s1", "localhost", 8001, 1);
        Server s2 = new Server("s2", "localhost", 8002, 1);
        lb.addServer(s1);
        lb.addServer(s2);

        s1.markUnhealthy();

        for (int i = 0; i < 20; i++) {
            Optional<Server> r = lb.getNextServer();
            assertTrue(r.isPresent());
            assertEquals("s2", r.get().getId());
        }
    }

    @Test
    void serverMarkedHealthyAgain_becomesEligible() {
        Server s1 = new Server("s1", "localhost", 8001, 1);
        lb.addServer(s1);
        s1.markUnhealthy();

        assertFalse(lb.getNextServer().isPresent());

        s1.markHealthy();
        assertTrue(lb.getNextServer().isPresent());
    }
}
