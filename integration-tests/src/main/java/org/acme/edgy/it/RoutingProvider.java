package org.acme.edgy.it;

import static org.acme.edgy.runtime.api.utils.StatusCode.OK;

import java.time.temporal.ChronoUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.enterprise.inject.Produces;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.resiliency.SmallRyeFaultToleranceGuardHandler;
import org.acme.edgy.runtime.api.utils.StatusCode;

import io.smallrye.faulttolerance.api.RateLimitException;
import io.smallrye.faulttolerance.api.RateLimitType;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

class RoutingProvider {

    @Produces
    RoutingConfiguration routing() {
        return new RoutingConfigurationBuilder(RoutingConfiguration.builder())
                .addRoutes(this::storkRoutes)
                .addRoutes(this::resiliencyRoutes)
                .build();
    }

    private RoutingConfiguration.Builder storkRoutes(RoutingConfiguration.Builder builder) {
        return builder
                .addRoute(new Route("/test", Origin.of("stork-origin", "stork://my-service/test/hello")))
                .addRoute(new Route("/test-secured",
                        Origin.of("secured-stork-origin", "storks://my-secured-service/test/hello")));
    }

    private RoutingConfiguration.Builder resiliencyRoutes(RoutingConfiguration.Builder builder) {
        // Rate Limit Configuration
        int rateLimit = 10;
        long windowMillis = 1000L;
        // Bulkhead Configuration
        int bulkheadLimit = 5;
        int queueSize = 3;
        // Circuit Breaker Configuration
        double failureRatio = 0.5;
        int requestVolumeThreshold = 10;
        int delaySeconds = 1;
        int successThreshold = 3;

        BiFunction<ProxyContext, Throwable, Future<ProxyResponse>> fallback = (ctx,
                throwable) -> fallbackResponse(ctx, "Fallback response");

        return builder
                // ----------------------------- RATE LIMIT -----------------------------
                .addRoute(new Route("/rate-limit",
                        Origin.of("rate-limit-origin", "http://localhost:8081/api/resiliency/rate-limit"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withRateLimit(rl -> rl
                                        .limit(rateLimit)
                                        .type(RateLimitType.ROLLING)
                                        .window(windowMillis, ChronoUnit.MILLIS))
                                .build()))
                // ----------------------------- BULKHEAD -----------------------------
                .addRoute(new Route("/bulkhead",
                        Origin.of("bulkhead-origin", "http://localhost:8081/api/resiliency/bulkhead"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withBulkhead(bh -> bh
                                        .limit(bulkheadLimit)
                                        .queueSize(queueSize))
                                .build()))
                // ----------------------------- CIRCUIT BREAKER -----------------------------
                .addRoute(new Route("/circuit-breaker",
                        Origin.of("circuit-breaker-origin", "http://localhost:8081/api/resiliency/circuit-breaker"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withCircuitBreaker(cb -> cb
                                        .requestVolumeThreshold(requestVolumeThreshold)
                                        .failureRatio(failureRatio)
                                        .successThreshold(successThreshold)
                                        .delay(delaySeconds, ChronoUnit.SECONDS))
                                .withExpectation(StatusCode.SC_SUCCESS)
                                .build()))
                // ---------------- CB + RATE LIMIT (skipOn RateLimitException) ---------------------
                .addRoute(new Route("/circuit-breaker-with-rate-limit",
                        Origin.of("circuit-breaker-with-rate-limit-origin",
                                "http://localhost:8081/api/resiliency/rate-limit"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withRateLimit(rl -> rl
                                        .limit(1)
                                        .window(windowMillis, ChronoUnit.MILLIS)
                                        .type(RateLimitType.FIXED))
                                .withCircuitBreaker(cb -> cb
                                        .requestVolumeThreshold(4)
                                        .failureRatio(failureRatio)
                                        .skipOn(RateLimitException.class))
                                .withExpectation(StatusCode.SC_SUCCESS)
                                .build()))
                // ----------------------------- RETRY -----------------------------
                .addRoute(new Route("/retry",
                        Origin.of("retry-origin", "http://localhost:8081/api/resiliency/retry"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withRetry(rt -> rt
                                        .maxRetries(3)
                                        .delay(0, ChronoUnit.MILLIS)
                                        .jitter(0, ChronoUnit.MILLIS))
                                .withExpectation(StatusCode.SC_SUCCESS)
                                .build()))
                // ----------------------------- FALLBACK (only) -----------------------------
                .addRoute(new Route("/with-fallback",
                        Origin.of("with-fallback-origin", "http://localhost:8081/api/non-existing-endpoint"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withExpectation(StatusCode.SC_NON_ERROR)
                                .withFallback(fallback)
                                .build()))
                // ---------------------- RATE LIMIT + FALLBACK ----------------------------
                .addRoute(new Route("/rate-limit-with-fallback",
                        Origin.of("rate-limit-with-fallback-origin",
                                "http://localhost:8081/api/resiliency/rate-limit"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withRateLimit(rl -> rl
                                        .limit(1)
                                        .window(windowMillis, ChronoUnit.MILLIS)
                                        .type(RateLimitType.FIXED))
                                .withFallback((ctx, throwable) -> fallbackResponse(ctx, "Rate limit fallback"))
                                .build()))
                // -------------------- CIRCUIT BREAKER + FALLBACK -------------------------
                .addRoute(new Route("/circuit-breaker-with-fallback",
                        Origin.of("circuit-breaker-with-fallback-origin",
                                "http://localhost:8081/api/resiliency/circuit-breaker-fallback"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withCircuitBreaker(cb -> cb
                                        .requestVolumeThreshold(4)
                                        .failureRatio(0.5)
                                        .delay(10, ChronoUnit.SECONDS))
                                .withExpectation(StatusCode.SC_SUCCESS)
                                .withFallback((ctx, throwable) -> fallbackResponse(ctx, "Circuit breaker fallback"))
                                .build()))
                // ----------------------- RETRY + FALLBACK --------------------------------
                .addRoute(new Route("/retry-with-fallback",
                        Origin.of("retry-with-fallback-origin",
                                "http://localhost:8081/api/resiliency/retry-fallback"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withRetry(rt -> rt
                                        .maxRetries(2)
                                        .delay(0, ChronoUnit.MILLIS)
                                        .jitter(0, ChronoUnit.MILLIS))
                                .withExpectation(StatusCode.SC_SUCCESS)
                                .withFallback((ctx, throwable) -> fallbackResponse(ctx, "Retry fallback"))
                                .build()))
                // -------------------- EXPECTATION + FALLBACK (no guard) -----------------
                .addRoute(new Route("/expectation-with-fallback",
                        Origin.of("expectation-with-fallback-origin",
                                "http://localhost:8081/api/resiliency/circuit-breaker-fallback"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withExpectation(StatusCode.SC_SUCCESS)
                                .withFallback((ctx, throwable) -> fallbackResponse(ctx, "Expectation fallback"))
                                .build()))
                // ----------------------------- PAYLOAD LIMIT -----------------------------
                .addRoute(new Route("/payload-limit",
                        Origin.of("payload-limit-origin", "http://localhost:8081/api/resiliency/echo"))
                        .setGuardHandler(SmallRyeFaultToleranceGuardHandler.builder()
                                .withMaxPayloadSize(64)
                                .build()));
    }

    private Future<ProxyResponse> fallbackResponse(ProxyContext ctx, String body) {
        ProxyResponse response = ctx.request().release().response()
                .setBody(Body.body(Buffer.buffer(body)))
                .setStatusCode(OK);
        return Future.succeededFuture(response);
    }

    // for clear structure of tested routes
    static class RoutingConfigurationBuilder {
        private final RoutingConfiguration.Builder builder;

        RoutingConfigurationBuilder(RoutingConfiguration.Builder builder) {
            this.builder = builder;
        }

        RoutingConfigurationBuilder addRoutes(
                Function<RoutingConfiguration.Builder, RoutingConfiguration.Builder> routeFunction) {
            routeFunction.apply(this.builder);
            return this;
        }

        RoutingConfiguration build() {
            return builder.build();
        }
    }
}
