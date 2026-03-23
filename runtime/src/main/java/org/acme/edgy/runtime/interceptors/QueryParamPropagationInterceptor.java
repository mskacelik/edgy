package org.acme.edgy.runtime.interceptors;

import static org.acme.edgy.runtime.api.utils.QueryParamUtils.appendUriQueries;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.hasQuery;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

public class QueryParamPropagationInterceptor implements ProxyInterceptor {

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        ProxyRequest proxyRequest = context.request();
        String originUri = proxyRequest.getURI();
        String encodedQueryOfApiGatewayUri = proxyRequest.proxiedRequest().query();
        if (encodedQueryOfApiGatewayUri == null && !hasQuery(originUri)) {
            return context.sendRequest();
        }
        if (encodedQueryOfApiGatewayUri != null) {
            originUri = appendUriQueries(originUri, encodedQueryOfApiGatewayUri);
        }
        proxyRequest.setURI(originUri);
        return context.sendRequest();
    }

}
