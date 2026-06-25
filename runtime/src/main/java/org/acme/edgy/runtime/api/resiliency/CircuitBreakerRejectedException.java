package org.acme.edgy.runtime.api.resiliency;

/**
 * Thrown when a request is rejected because the circuit breaker is open.
 * <p>
 * This exception is used as a signal, not a diagnostic error, so it suppresses
 * stack trace capture. A single cached {@link #INSTANCE} is provided to avoid
 * allocation on the hot path.
 */
public class CircuitBreakerRejectedException extends RuntimeException {

    public static final CircuitBreakerRejectedException INSTANCE = new CircuitBreakerRejectedException();

    private CircuitBreakerRejectedException() {
        super(null, null, false, false);
    }
}
