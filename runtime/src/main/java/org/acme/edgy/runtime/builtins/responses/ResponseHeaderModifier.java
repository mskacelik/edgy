package org.acme.edgy.runtime.builtins.responses;

import org.acme.edgy.runtime.api.ResponseTransformer;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;

public class ResponseHeaderModifier implements ResponseTransformer {
    private final String headerName;
    private final String newValue;

    public ResponseHeaderModifier(String headerName, String newValue) {
        this.headerName = headerName;
        this.newValue = newValue;
    }

    @Override
    public Future<Void> apply(ProxyContext proxyContext) {
        if (proxyContext.response().headers().contains(headerName)) {
            proxyContext.response().headers().set(headerName, newValue);
        }
        return proxyContext.sendResponse();
    }
}
