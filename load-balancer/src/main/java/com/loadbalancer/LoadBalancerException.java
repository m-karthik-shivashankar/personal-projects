package com.loadbalancer;

/**
 * Thrown when the load balancer cannot fulfill a request — for example when
 * no healthy server has tokens remaining.
 */
public class LoadBalancerException extends RuntimeException {

    public LoadBalancerException(String message) {
        super(message);
    }

    public LoadBalancerException(String message, Throwable cause) {
        super(message, cause);
    }
}
