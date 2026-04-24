package org.acme.edgy.runtime.interceptors.resiliency;

import jakarta.enterprise.util.TypeLiteral;

import io.smallrye.faulttolerance.api.TypedGuard;
import io.smallrye.faulttolerance.api.TypedGuard.Builder;
import io.smallrye.faulttolerance.api.TypedGuard.Builder.BulkheadBuilder;
import io.smallrye.faulttolerance.api.TypedGuard.Builder.CircuitBreakerBuilder;
import io.smallrye.faulttolerance.api.TypedGuard.Builder.FallbackBuilder;
import io.smallrye.faulttolerance.api.TypedGuard.Builder.RateLimitBuilder;
import io.smallrye.faulttolerance.api.TypedGuard.Builder.RetryBuilder;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyResponse;

public class ResiliencyBuilder {

    private final Builder<Future<ProxyResponse>> delegate;
    private boolean retryConfigured;

    ResiliencyBuilder() {
        this.delegate = TypedGuard.create(new TypeLiteral<Future<ProxyResponse>>() {
        });
    }

    public BulkheadBuilder<Future<ProxyResponse>> withBulkhead() {
        return delegate.withBulkhead();
    }

    public CircuitBreakerBuilder<Future<ProxyResponse>> withCircuitBreaker() {
        return delegate.withCircuitBreaker();
    }

    public FallbackBuilder<Future<ProxyResponse>> withFallback() {
        return delegate.withFallback();
    }

    public RateLimitBuilder<Future<ProxyResponse>> withRateLimit() {
        return delegate.withRateLimit();
    }

    public RetryBuilder<Future<ProxyResponse>> withRetry() {
        this.retryConfigured = true;
        return delegate.withRetry();
    }

    boolean hasRetry() {
        return retryConfigured;
    }

    TypedGuard<Future<ProxyResponse>> build() {
        return delegate.build();
    }
}
