package org.acme.edgy.runtime.api;

import io.vertx.httpproxy.ProxyContext;

public interface ProxyObserver {

    ProxyObservation observe(ProxyContext context, Route route);
}
