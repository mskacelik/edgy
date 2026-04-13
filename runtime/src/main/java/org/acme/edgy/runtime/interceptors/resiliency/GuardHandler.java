package org.acme.edgy.runtime.interceptors.resiliency;

import static jakarta.ws.rs.core.HttpHeaders.RETRY_AFTER;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.acme.edgy.runtime.api.utils.ProxyErrorResponseBuilder;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import io.smallrye.faulttolerance.api.RateLimitException;
import io.smallrye.faulttolerance.api.TypedGuard;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;

public class GuardHandler implements ProxyInterceptor {

    private final BiConsumer<ProxyContext, ResiliencyBuilder> configurator;
    private final Expectation<ProxyResponse> expectation;

    private record GuardContainer(TypedGuard<Future<ProxyResponse>> guard, boolean buffering) {
    }

    private final AtomicReference<GuardContainer> guardRef = new AtomicReference<>();

    public GuardHandler(BiConsumer<ProxyContext, ResiliencyBuilder> configurator,
            Expectation<ProxyResponse> expectation) {
        this.configurator = Objects.requireNonNull(configurator);
        this.expectation = Objects.requireNonNull(expectation);
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        GuardContainer container = getOrInitialize(context);

        Future<Void> preparation = container.buffering()
                ? bufferBody(context.request())
                : Future.succeededFuture();

        return preparation.compose(v -> {
            try {
                return container.guard().call(() -> context.sendRequest().expecting(expectation))
                        .recover(throwable -> {
                            if (throwable instanceof RateLimitException rateLimitException) {
                                String retryAfterValue = String
                                        .valueOf((rateLimitException.getRetryAfterMillis() + 999) / 1000);
                                return ProxyErrorResponseBuilder.create(context)
                                        .tooManyRequests()
                                        .message(rateLimitException.getMessage())
                                        .header(RETRY_AFTER, retryAfterValue)
                                        .sendResponseInRequestTransformer();
                            } else if (throwable instanceof CircuitBreakerOpenException) {
                                return ProxyErrorResponseBuilder.create(context)
                                        .serviceUnavailable()
                                        .message(throwable.getMessage())
                                        .sendResponseInRequestTransformer();
                            }
                            return Future.failedFuture(throwable);
                        });
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        });
    }

    private Future<Void> bufferBody(ProxyRequest proxyRequest) {
        Body body = proxyRequest.getBody();
        if (body == null) {
            return Future.succeededFuture();
        }
        ReadStream<Buffer> stream = body.stream();
        Buffer collected = Buffer.buffer();
        Promise<Void> promise = Promise.promise();
        stream.handler(collected::appendBuffer);
        stream.endHandler(v -> {
            proxyRequest.setBody(Body.body(collected));
            promise.complete();
        });
        stream.exceptionHandler(promise::fail);
        stream.resume();
        return promise.future();
    }

    private GuardContainer getOrInitialize(ProxyContext proxyContext) {
        GuardContainer current = guardRef.get();
        if (current != null) {
            return current;
        }

        ResiliencyBuilder builder = new ResiliencyBuilder();
        configurator.accept(proxyContext, builder);
        GuardContainer newlyCreated = new GuardContainer(builder.build(), builder.hasRetry());

        if (guardRef.compareAndSet(null, newlyCreated)) {
            return newlyCreated;
        }

        return guardRef.get();
    }
}
