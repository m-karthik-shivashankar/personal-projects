package com.loadbalancer;

import java.util.Optional;

/**
 * Contract for a load-balancer that distributes incoming requests across a
 * pool of {@link Server} instances.
 */
public interface LoadBalancer {

    /**
     * Returns the next server to handle a request.
     *
     * @return an {@link Optional} containing the chosen server, or
     *         {@link Optional#empty()} when no server is available (all unhealthy
     *         or all token buckets exhausted)
     */
    Optional<Server> getNextServer();

    /**
     * Adds a server to the pool.
     *
     * @param server the server to add (must not be null)
     * @throws IllegalArgumentException if a server with the same id already exists
     */
    void addServer(Server server);

    /**
     * Removes a server from the pool.
     *
     * @param serverId the id of the server to remove
     * @throws LoadBalancerException if no server with the given id is registered
     */
    void removeServer(String serverId);

    /** Returns the total number of servers currently registered (healthy or not). */
    int getServerCount();
}
