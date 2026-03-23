package org.acme.edgy.runtime;

import static org.acme.edgy.runtime.api.utils.QueryParamUtils.appendUriQueries;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.hasQuery;
import static org.acme.edgy.runtime.api.utils.QueryParamUtils.urlEncode;

import java.util.Collection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.ResponseTransformer;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.SegmentUtils;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;

@ApplicationScoped
@DefaultBean
public class RouterConfigurator {

    private static final String REQUEST_URI = SegmentUtils.REQUEST_URI;

    @Inject
    RoutingConfiguration routingConfiguration;

    @Inject
    OriginHttpClientManager originHttpClientManager;

    void configure(@Observes Router router) {
        for (Route route : routingConfiguration.routes()) {
            HttpClient httpClient = originHttpClientManager.getOrCreateHttpClient(route.origin());

            HttpProxy proxy = HttpProxy.reverseProxy(httpClient)
                    .origin(route.origin().originRequestProvider());

            rerouteProxyRequestAndResolveUriTemplate(proxy, route);

            // to include query params from the original API Gateway URI
            propagateQueryParams(proxy);

            // response transformers
            applyResponseTransformers(route.responseTransformers(), proxy);

            // request transformers
            applyRequestTransformers(route.requestTransformers(), proxy);

            registerVertxRoute(router, route, proxy);
        }
    }

    private void propagateQueryParams(HttpProxy proxy) {
        proxy.addInterceptor(new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                ProxyRequest proxyRequest = context.request();
                String originUri = proxyRequest.getURI();
                String encodedQueryOfApiGatewayUri = context.request().proxiedRequest().query();
                if (encodedQueryOfApiGatewayUri == null && !hasQuery(originUri)) {
                    // no queries present
                    return context.sendRequest();
                }
                if (encodedQueryOfApiGatewayUri != null) {
                    // appends originalAPIGatewayURI query params into the originUri
                    originUri = appendUriQueries(originUri, encodedQueryOfApiGatewayUri);
                }
                proxyRequest.setURI(originUri);
                return context.sendRequest();
            }
        });
    }

    private void rerouteProxyRequestAndResolveUriTemplate(HttpProxy proxy, Route route) {
        String originPath = route.resolvedOriginPath();
        boolean hasUriTemplateVariables = originPath.indexOf(SegmentUtils.OPEN_BRACE) >= 0;
        UriTemplate uriTemplate = hasUriTemplateVariables ? UriTemplate.of(originPath) : null;
        proxy.addInterceptor(new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                ProxyRequest proxyRequest = context.request();

                if (!hasUriTemplateVariables) {
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
        });
    }

    private void applyRequestTransformers(Collection<RequestTransformer> requestTransformers, HttpProxy proxy) {
        for (RequestTransformer requestTransformer : requestTransformers) {
            proxy.addInterceptor(new ProxyInterceptor() {
                @Override
                public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
                    return requestTransformer.apply(context);
                }
            });
        }
    }

    private void applyResponseTransformers(Collection<ResponseTransformer> responseTransformers,
            HttpProxy proxy) {
        for (ResponseTransformer responseTransformer : responseTransformers) {
            proxy.addInterceptor(new ProxyInterceptor() {
                @Override
                public Future<Void> handleProxyResponse(ProxyContext context) {
                    return responseTransformer.apply(context);
                }
            });
        }
    }

    private void registerVertxRoute(Router router, Route edgyRoute, HttpProxy proxy) {
        var vertxRoute = edgyRoute.needsRegexRouting()
                ? router.routeWithRegex(edgyRoute.resolvedPath())
                : router.route(edgyRoute.resolvedPath());
        ProxyHandler proxyHandler = ProxyHandler.create(proxy);
        vertxRoute.handler(rc -> {
            if (edgyRoute.predicate().test(rc)) {
                proxyHandler.handle(rc);
                return;
            }
            // if the predicate does not match, it will sequentially try the next route
            // (with the same Path), if it exists
            rc.next();
        });
    }
}
