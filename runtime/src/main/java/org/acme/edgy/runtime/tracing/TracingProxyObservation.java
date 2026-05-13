package org.acme.edgy.runtime.tracing;

import static org.acme.edgy.runtime.api.utils.StatusCode.isServerError;

import org.acme.edgy.runtime.api.ProxyObservation;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.semconv.HttpAttributes;
import io.vertx.httpproxy.ProxyContext;

class TracingProxyObservation implements ProxyObservation {

    private final Span span;

    TracingProxyObservation(Span span) {
        this.span = span;
    }

    @Override
    public void end(ProxyContext context) {
        if (context.response() != null) {
            int statusCode = context.response().getStatusCode();
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            if (isServerError(statusCode)) {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            }
        }
        span.end();
    }

    @Override
    public void error(ProxyContext context, Throwable error) {
        span.recordException(error);
        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, error.getMessage());
        span.end();
    }
}
