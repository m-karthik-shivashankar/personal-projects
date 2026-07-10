package com.loadbalancer;

import java.util.Objects;

/**
 * Represents a backend server managed by the load balancer.
 *
 * <p>Each server has a unique {@code id}, network address ({@code host} and
 * {@code port}), a {@code weight} that controls how much traffic it receives
 * relative to other servers, and a {@code healthy} flag that can be toggled to
 * take the server in and out of rotation.
 */
public class Server {

    private final String id;
    private final String host;
    private final int port;
    private final int weight;
    private volatile boolean healthy;

    /**
     * Creates a new server that is healthy by default.
     *
     * @param id     unique identifier (must not be null or blank)
     * @param host   hostname or IP address
     * @param port   TCP port (1–65535)
     * @param weight relative traffic weight; must be &gt; 0
     */
    public Server(String id, String host, int port, int weight) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Server id must not be null or blank");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server host must not be null or blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be > 0, got: " + weight);
        }
        this.id = id;
        this.host = host;
        this.port = port;
        this.weight = weight;
        this.healthy = true;
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isHealthy() {
        return healthy;
    }

    /** Mark the server as healthy (puts it back into rotation). */
    public void markHealthy() {
        this.healthy = true;
    }

    /** Mark the server as unhealthy (removes it from rotation). */
    public void markUnhealthy() {
        this.healthy = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Server)) return false;
        Server server = (Server) o;
        return Objects.equals(id, server.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Server{id='" + id + "', host='" + host + "', port=" + port
                + ", weight=" + weight + ", healthy=" + healthy + "}";
    }
}
