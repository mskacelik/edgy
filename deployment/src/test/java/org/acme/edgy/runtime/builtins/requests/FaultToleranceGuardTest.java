package org.acme.edgy.runtime.builtins.requests;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.faulttolerance.api.TypedGuard;
import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.awaitility.Awaitility;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.BAD_GATEWAY;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

class FaultToleranceGuardTest {

    private static final long TIMEOUT_DURATION_MS = 300;
    private static final long TIMEOUT_OVERHEAD_MS = 50;

    // TODO: use builders instead
    private static final TypedGuard<Future<ProxyResponse>> timeoutGuard =
            TypedGuard.create(new TypeLiteral<Future<ProxyResponse>>() {}).withTimeout()
                    .duration(TIMEOUT_DURATION_MS, ChronoUnit.MILLIS).done().build();

    private static final TypedGuard<Future<ProxyResponse>> retryGuard =
            TypedGuard.create(new TypeLiteral<Future<ProxyResponse>>() {}).withRetry()
                    .retryOn(RuntimeException.class).maxRetries(10)
                    .done().build();

    @ApplicationScoped
    static class RoutingProvider {

        @Produces
        RoutingConfiguration routingConfiguration() {
            return new RoutingConfiguration()
                    .addRoute(new Route("/timeout", Origin.of("http://localhost:8081/test/timeout"),
                            PathMode.FIXED)
                                    .addRequestTransformer(new FaultToleranceGuard(timeoutGuard)))
                    .addRoute(new Route("/retry-1", Origin.of("http://localhost:8081/test/retry-1"),
                            PathMode.FIXED)
                                    .addRequestTransformer(new FaultToleranceGuard(retryGuard)))
                    .addRoute(new Route("/retry-2", Origin.of("http://localhost:8081/test/retry-2"),
                            PathMode.FIXED)
                                    .addRequestTransformer(new FaultToleranceGuard(retryGuard)));
        }
    }

    @Path("/test")
    static class TestApi {
        private AtomicInteger firstCounter = new AtomicInteger();
        private AtomicInteger secondCounter = new AtomicInteger();

        @GET
        @Path("/timeout")
        public RestResponse<Void> timeoutEndpoint() {
            try {
                Thread.sleep(2 * TIMEOUT_DURATION_MS);
            } catch (Exception e) {
                // ignore
            }
            return RestResponse.ok();
        }

        @GET
        @Path("/retry-1")
        public RestResponse<String> firstUnstableEndpointThatFixesItself() {
            if (firstCounter.incrementAndGet() < 2) {
                throw new RuntimeException("Simulated failure");
            }
            return RestResponse.ok("Success");
        }

        @GET
        @Path("/retry-2")
        public RestResponse<Void> secondUnstableEndpointThatFails() {
            if (secondCounter.incrementAndGet() < 3) {
                return RestResponse.serverError();
            }
            return RestResponse.ok();
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest =
            new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class));

    @Test
    void test_timeout() {
        // Awaitility.await().timeout(Duration.ofMillis(TIMEOUT_DURATION_MS + TIMEOUT_OVERHEAD_MS))
        // .untilAsserted(() -> RestAssured.given().when().get("/timeout").then()
        // .statusCode(BAD_GATEWAY));

        // 408 is probably better, 502 is probably due to Future.failedFuture
        RestAssured.given().when().get("/timeout").then().statusCode(BAD_GATEWAY);
    }

    @Test
    void test_retryBeingSuccessful() {
        RestAssured.given().when().get("/retry-1").then().statusCode(OK);
    }


    @Test
    void test_retryBedingFailure() {
        RestAssured.given().when().get("/retry-2").then().statusCode(INTERNAL_SERVER_ERROR);
    }
}
