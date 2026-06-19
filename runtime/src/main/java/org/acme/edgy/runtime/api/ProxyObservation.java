package org.acme.edgy.runtime.api;

import io.vertx.httpproxy.ProxyContext;

/**
 * A typed handle representing an in-flight observation of a proxied request.
 * Created by {@link ProxyObserver#observe} and completed via {@link #end} or
 * {@link #error}. States of the observations are managed by the
 * {@link org.acme.edgy.runtime.interceptors.ObservingProxyInterceptor}.
 */
public interface ProxyObservation {

    ProxyObservation NOOP = new ProxyObservation() {
        @Override
        public void end(ProxyContext context) {
            // no-op
        }

        @Override
        public void error(ProxyContext context, Throwable error) {
            // no-op
        }
    };

    /**
     * Called when the proxied response completed successfully (non-5xx).
     */
    void end(ProxyContext context);

    /**
     * Called on server errors (5xx) or Vert.x {@link io.vertx.core.Future} failure.
     *
     * @param error the cause, or {@code null} for 5xx responses
     */
    void error(ProxyContext context, Throwable error);
}
