package org.acme.edgy.runtime.api;

import io.vertx.httpproxy.ProxyContext;

public interface ProxyObservation {

    void end(ProxyContext context);

    void error(ProxyContext context, Throwable error);

    ProxyObservation NOOP = new ProxyObservation() {
        @Override
        public void end(ProxyContext context) {
        }

        @Override
        public void error(ProxyContext context, Throwable error) {
        }
    };
}
