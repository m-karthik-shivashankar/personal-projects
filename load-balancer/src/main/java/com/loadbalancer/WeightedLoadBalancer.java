package com.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * A {@link LoadBalancer} that selects backend servers using <em>weighted
 * random</em> selection combined with per-server <em>token-bucket</em> rate
 * limiting.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Each healthy server is assigned a weight. A server with weight 3 is
 *       three times as likely to be chosen as a server with weight 1.</li>
 *   <li>Every server has its own {@link TokenBucket}. Before a server is
 *       returned, one token is consumed from its bucket. If the bucket is
 *       empty the server is skipped and the algorithm tries the next
 *       candidate (in weighted order).</li>
 *   <li>If all servers have exhausted their tokens or none are healthy,
 *       {@link #getNextServer()} returns {@link Optional#empty()}.</li>
 * </ol>
 *
 * <p>All public methods are thread-safe.
 */
public class WeightedLoadBalancer implements LoadBalancer {

    private final long tokenCapacity;
    private final double tokenRefillRate;
    private final Random random;

    /** Guards both {@code servers} and {@code tokenBuckets}. */
    private final Object lock = new Object();

    private final List<Server> servers;
    private final Map<String, TokenBucket> tokenBuckets;

    /**
     * Creates a load balancer whose per-server token buckets each have the
     * given capacity and refill rate.
     *
     * @param tokenCapacity    max burst size (tokens per bucket)
     * @param tokenRefillRate  tokens added per second to each bucket
     */
    public WeightedLoadBalancer(long tokenCapacity, double tokenRefillRate) {
        this(tokenCapacity, tokenRefillRate, new Random());
    }

    /**
     * Package-private constructor that accepts an injectable {@link Random}
     * for deterministic unit tests.
     */
    WeightedLoadBalancer(long tokenCapacity, double tokenRefillRate, Random random) {
        if (tokenCapacity <= 0) {
            throw new IllegalArgumentException("tokenCapacity must be > 0");
        }
        if (tokenRefillRate <= 0) {
            throw new IllegalArgumentException("tokenRefillRate must be > 0");
        }
        this.tokenCapacity = tokenCapacity;
        this.tokenRefillRate = tokenRefillRate;
        this.random = random;
        this.servers = new ArrayList<>();
        this.tokenBuckets = new HashMap<>();
    }

    // --------------------------------------------------------------------- //
    //  LoadBalancer interface
    // --------------------------------------------------------------------- //

    /**
     * {@inheritDoc}
     *
     * <p>Builds a weighted list of healthy servers and tries each in turn
     * (without replacement) until one with tokens is found. Runs in
     * O(n × max-weight) time where n is the number of healthy servers.
     */
    @Override
    public Optional<Server> getNextServer() {
        synchronized (lock) {
            List<Server> candidates = buildWeightedCandidateList();
            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            // Shuffle so attempts are in random weighted order rather than
            // always starting from the front of the list.
            Collections.shuffle(candidates, random);

            for (Server candidate : candidates) {
                TokenBucket bucket = tokenBuckets.get(candidate.getId());
                if (bucket != null && bucket.tryConsume()) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addServer(Server server) {
        if (server == null) {
            throw new IllegalArgumentException("Server must not be null");
        }
        synchronized (lock) {
            for (Server existing : servers) {
                if (existing.getId().equals(server.getId())) {
                    throw new IllegalArgumentException(
                            "Server with id '" + server.getId() + "' is already registered");
                }
            }
            servers.add(server);
            tokenBuckets.put(server.getId(), new TokenBucket(tokenCapacity, tokenRefillRate));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void removeServer(String serverId) {
        if (serverId == null) {
            throw new IllegalArgumentException("serverId must not be null");
        }
        synchronized (lock) {
            boolean removed = servers.removeIf(s -> s.getId().equals(serverId));
            if (!removed) {
                throw new LoadBalancerException(
                        "No server with id '" + serverId + "' is registered");
            }
            tokenBuckets.remove(serverId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public int getServerCount() {
        synchronized (lock) {
            return servers.size();
        }
    }

    /**
     * Returns an unmodifiable snapshot of all registered servers (healthy or not).
     */
    public List<Server> getServers() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(servers));
        }
    }

    /**
     * Returns the {@link TokenBucket} associated with the given server id, or
     * {@code null} if no such server is registered.
     */
    public TokenBucket getTokenBucket(String serverId) {
        synchronized (lock) {
            return tokenBuckets.get(serverId);
        }
    }

    // --------------------------------------------------------------------- //
    //  Internal helpers
    // --------------------------------------------------------------------- //

    /**
     * Builds an expanded list of healthy servers where each server appears
     * {@code weight} times. This gives an O(n × max-weight) shuffle-based
     * weighted selection that is easy to reason about and test.
     */
    private List<Server> buildWeightedCandidateList() {
        List<Server> candidates = new ArrayList<>();
        for (Server server : servers) {
            if (server.isHealthy()) {
                for (int i = 0; i < server.getWeight(); i++) {
                    candidates.add(server);
                }
            }
        }
        return candidates;
    }
}
