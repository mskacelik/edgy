package org.acme.edgy.runtime.builtins.requests;

import org.acme.edgy.runtime.api.RequestTransformer;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestHeaderModifier implements RequestTransformer {
    private final String headerName;
    private final String newValue;

    public RequestHeaderModifier(String headerName, String newValue) {
        this.headerName = headerName;
        this.newValue = newValue;
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        if (proxyContext.request().headers().contains(headerName)) {
            proxyContext.request().headers().set(headerName, newValue);
        }
        return proxyContext.sendRequest();
    }
}
