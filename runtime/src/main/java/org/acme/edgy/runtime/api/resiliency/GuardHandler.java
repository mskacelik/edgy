package org.acme.edgy.runtime.api.resiliency;

import java.util.concurrent.Callable;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyResponse;

/**
 * Guards proxied requests on a route by wrapping a {@link Callable} action
 * with fault tolerance patterns (rate limit, bulkhead, circuit breaker,
 * retry).
 *
 * @see SmallRyeFaultToleranceGuardHandler
 * @see org.acme.edgy.runtime.api.Route#setGuardHandler(GuardHandler)
 */
public interface GuardHandler {

    /**
     * Executes the given action wrapped with the configured resilience
     * patterns.
     *
     * @param action the proxy action to guard, never {@code null}
     * @return the guarded response future
     * @throws Exception if the guard rejects the action synchronously
     */
    Future<ProxyResponse> execute(Callable<Future<ProxyResponse>> action) throws Exception;

    /**
     * Indicates whether the request body must be buffered before execution
     * so that it can be re-sent on retries or replays.
     */
    boolean needsBuffering();
}
