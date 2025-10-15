package org.acme.edgy.runtime.builtins.requests;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.acme.edgy.runtime.api.RequestTransformer;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse.StatusCode;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.quarkus.arc.Arc;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class RequestRetryAdder implements RequestTransformer {

    private static final Logger logger = Logger.getLogger(RequestRetryAdder.class);

    private final Function<ProxyContext, RetryOptions> mapper;
    private final Vertx vertx;

    public RequestRetryAdder(Function<ProxyContext, RetryOptions> mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.vertx = Arc.container().instance(Vertx.class).get();
    }

    public RequestRetryAdder(RetryOptions options) {
        this(proxyContext -> options);
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        return executeWithRetry(proxyContext);
    }

    private Future<ProxyResponse> executeWithRetry(ProxyContext proxyContext) {
        ContextInternal ctx = ContextInternal.current();
        Promise<ProxyResponse> promise = ctx != null ? ctx.promise() : Promise.promise();
        RetryOptions options = mapper.apply(proxyContext);
        Retry retry = Retry.of(options.getId(), options.buildConfig());
        new AsyncRetryBlock(vertx, retry.asyncContext(), proxyContext::sendRequest, promise).run();

        return promise.future();
    }

    private static class AsyncRetryBlock implements Runnable, Handler<Long> {

        private final Vertx vertx;
        private final Retry.AsyncContext<ProxyResponse> retryContext;
        private final Supplier<Future<ProxyResponse>> supplier;
        private final Promise<ProxyResponse> promise;

        AsyncRetryBlock(Vertx vertx,
                Retry.AsyncContext<ProxyResponse> retryContext,
                Supplier<Future<ProxyResponse>> supplier,
                Promise<ProxyResponse> promise) {
            this.vertx = vertx;
            this.retryContext = retryContext;
            this.supplier = supplier;
            this.promise = promise;
        }

        @Override
        public void run() {
            logger.info("Running retry block");
            try {
                supplier.get().onComplete(result -> {
                    logger.info("Future completed in retry block");
                    if (result.failed()) {
                        Throwable cause = result.cause();
                        if (cause instanceof Exception) {
                            onError((Exception) cause);
                        } else {
                            promise.fail(cause);
                        }
                    } else {
                        onResult(result.result());
                    }
                });
            } catch (Exception e) {
                onError(e);
            }
            logger.info("non blocking supplier invoked");
        }

        @Override
        public void handle(Long __) {
            run();
        }

        private void onError(Exception e) {
            long delay = retryContext.onError(e);
            if (delay < 1) {
                promise.fail(e);
                return;
            }
            vertx.setTimer(delay, this);
        }

        private void onResult(ProxyResponse result) {
            long delay = retryContext.onResult(result);
            logger.infof("Result: %s", result.getStatusCode());
            if (delay < 1) {
                try {
                    retryContext.onComplete();
                    promise.complete(result);
                } catch (Exception e) {
                    logger.error("Error completing retry", e);
                    promise.fail(e);
                }
                return;
            }
            vertx.setTimer(delay, this);
            logger.infof("=== Retrying request, next attempt in %d ms ===", delay);
        }
    }

    public static class RetryOptions {
        private final String id;
        private int maxAttempts = RetryConfig.DEFAULT_MAX_ATTEMPTS;
        private Duration waitDuration = Duration.ofMillis(RetryConfig.DEFAULT_WAIT_DURATION);
        private Predicate<ProxyResponse> expectation = response -> response.getStatusCode() / 100 != 5;

        public RetryOptions(String id) {
            this.id = id;
        }

        public RetryOptions maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public RetryOptions waitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
            return this;
        }

        public RetryOptions expectation(Predicate<ProxyResponse> expectation) {
            this.expectation = expectation;
            return this;
        }

        private String getId() {
            return id;
        }

        private RetryConfig buildConfig() {
            return RetryConfig.<ProxyResponse>custom()
                    .maxAttempts(maxAttempts)
                    .waitDuration(waitDuration)
                    .retryOnResult(expectation.negate())
                    .build();
        }
    }
}