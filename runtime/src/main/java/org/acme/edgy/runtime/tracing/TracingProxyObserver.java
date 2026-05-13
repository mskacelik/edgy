package org.acme.edgy.runtime.tracing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.vertx.httpproxy.ProxyContext;

@ApplicationScoped
public class TracingProxyObserver implements ProxyObserver {

    private static final String SPAN_NAME_PREFIX = "edgy.proxy";

    @Inject
    Tracer tracer;

    @Override
    public ProxyObservation observe(ProxyContext context, Route route) {
        Span span = tracer.spanBuilder(SPAN_NAME_PREFIX + " " + route.path())
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, context.request().getMethod().name())
                .setAttribute(UrlAttributes.URL_PATH, context.request().proxiedRequest().uri())
                .setAttribute(ServerAttributes.SERVER_ADDRESS, route.origin().host())
                .setAttribute(ServerAttributes.SERVER_PORT, (long) route.origin().port())
                .startSpan();
        return new TracingProxyObservation(span);
    }
}
