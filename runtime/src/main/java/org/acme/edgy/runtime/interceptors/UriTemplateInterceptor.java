package org.acme.edgy.runtime.interceptors;

import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;

import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.utils.SegmentUtils;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;

public class UriTemplateInterceptor implements ProxyInterceptor {

    private static final String REQUEST_URI = SegmentUtils.REQUEST_URI;

    private final String originPath;
    private final UriTemplate uriTemplate;
    private final Route route;

    public UriTemplateInterceptor(Route route) {
        this.route = route;
        this.originPath = route.resolvedOriginPath();
        boolean hasUriTemplateVariables = originPath.indexOf(SegmentUtils.OPEN_BRACE) >= 0;
        this.uriTemplate = hasUriTemplateVariables ? UriTemplate.of(originPath) : null;
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        ProxyRequest proxyRequest = context.request();

        if (uriTemplate == null) {
            proxyRequest.setURI(originPath);
            return context.sendRequest();
        }

        Variables variables = Variables.variables()
                .set(REQUEST_URI, proxyRequest.getURI());

        route.extractPathVariables(proxyRequest.getURI())
                .forEach(variables::set);

        proxyRequest.proxiedRequest().params()
                .forEach((name, value) -> variables.set(name, urlEncode(value)));
        proxyRequest.setURI(uriTemplate.expandToString(variables));
        return context.sendRequest();
    }

}
