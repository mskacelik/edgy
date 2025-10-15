package org.acme.edgy.runtime.builtins.requests;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.TimeoutException;
import io.vertx.circuitbreaker.impl.CircuitBreakerImpl;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Objects;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;

public class CircuitBreakerAdder implements RequestTransformer {

    private final Function<ProxyContext, CircuitBreaker> mapper;

    public CircuitBreakerAdder(Function<ProxyContext, CircuitBreaker> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public CircuitBreakerAdder(CircuitBreaker circuitBreaker) {
        this.mapper = proxyContext -> Objects.requireNonNull(circuitBreaker);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        return mapper.apply(proxyContext)
                .execute(proxyContext.sendRequest()::onComplete);
    }
}
