package org.acme.edgy.runtime.builtins.requests;

import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.acme.edgy.runtime.api.utils.ProxyResponseFactory;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.quarkus.arc.Arc;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestTimeLimiterAdder implements RequestTransformer {

    private final Function<ProxyContext, TimeLimiter> mapper;
    private final Vertx vertx;

    public RequestTimeLimiterAdder(Function<ProxyContext, TimeLimiter> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.vertx = Arc.container().instance(Vertx.class).get();
    }

    public RequestTimeLimiterAdder(TimeLimiter timeLimiter) {
        this(proxyContext -> timeLimiter);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        TimeLimiter timeLimiter = mapper.apply(proxyContext);
        return executeWithTimeout(proxyContext, timeLimiter);
    }

    private Future<ProxyResponse> executeWithTimeout(ProxyContext proxyContext, TimeLimiter timeLimiter) {
        ContextInternal ctx = ContextInternal.current();
        Promise<ProxyResponse> promise = ctx != null ? ctx.promise() : Promise.promise();

        long timeoutMillis = timeLimiter.getTimeLimiterConfig()
                .getTimeoutDuration()
                .toMillis();

        long timerId = vertx.setTimer(timeoutMillis, __ -> {
            TimeoutException ex = TimeLimiter.createdTimeoutExceptionWithName(timeLimiter.getName(), null);

            Future<ProxyResponse> timeoutFuture = ProxyResponseFactory.requestTimeoutInRequestTransformer(proxyContext,
                    ex.getMessage());

            timeoutFuture.onComplete(timeoutResult -> {
                if (timeoutResult.succeeded()) {
                    if (promise.tryComplete(timeoutResult.result())) {
                        timeLimiter.onError(ex);
                    }
                } else {
                    if (promise.tryFail(timeoutResult.cause())) {
                        timeLimiter.onError(timeoutResult.cause());
                    }
                }
            });
        });

        try {
            proxyContext.sendRequest().onComplete(ar -> {
                vertx.cancelTimer(timerId);

                if (ar.succeeded()) {
                    if (promise.tryComplete(ar.result())) {
                        timeLimiter.onSuccess();
                    }
                } else {
                    if (promise.tryFail(ar.cause())) {
                        timeLimiter.onError(ar.cause());
                    }
                }
            });
        } catch (Exception e) {
            vertx.cancelTimer(timerId);
            if (promise.tryFail(e)) {
                timeLimiter.onError(e);
            }
        }

        return promise.future();
    }
}