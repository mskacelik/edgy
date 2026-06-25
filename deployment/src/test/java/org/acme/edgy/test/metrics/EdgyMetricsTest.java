package org.acme.edgy.test.metrics;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;

class EdgyMetricsTest {

    private static final String HELLO_ORIGIN_ID = "origin-1";
    private static final String HELLO_ROUTE_PATH = "/hello";
    private static final String UNREACHABLE_ORIGIN_ID = "unreachable-origin";
    private static final String UNREACHABLE_ROUTE_PATH = "/unreachable";

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-micrometer-registry-prometheus-deployment",
                    System.getProperty("project.quarkus.version"))));

    @Inject
    MeterRegistry registry;

    @Test
    void test_metricsRecordedForSuccessfulProxy() {
        given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello from origin!"));

        var search = registry.find("edgy.proxy.requests")
                .tag("route", HELLO_ROUTE_PATH)
                .tag("origin", HELLO_ORIGIN_ID)
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .tag("method", "GET");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Timer timer = search.timer();
            assertThat(timer).as("Timer should be registered for proxied request").isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).as("Duration should be recorded").isGreaterThan(0);
        });
    }

    @Test
    void test_metricsRecordedForServerError() {
        given()
                .get("/unreachable")
                .then()
                .statusCode(StatusCode.BAD_GATEWAY);

        var search = registry.find("edgy.proxy.requests")
                .tag("route", UNREACHABLE_ROUTE_PATH)
                .tag("origin", UNREACHABLE_ORIGIN_ID)
                .tag("status", "502")
                .tag("outcome", "SERVER_ERROR");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Timer timer = search.timer();
            assertThat(timer).as("Timer should be registered for 5xx proxy response").isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        });
    }

    @Test
    void test_routeAndOriginGaugesRegistered() {
        Gauge routeGauge = registry.find("edgy.routes.count").gauge();
        Gauge originGauge = registry.find("edgy.origins.count").gauge();

        assertThat(routeGauge).as("Route count gauge should be registered").isNotNull();
        assertThat(originGauge).as("Origin count gauge should be registered").isNotNull();
        assertThat(routeGauge.value()).as("Should report 2 routes").isEqualTo(2.0);
        assertThat(originGauge.value()).as("Should report 2 unique origins").isEqualTo(2.0);
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route(HELLO_ROUTE_PATH,
                            Origin.of(HELLO_ORIGIN_ID, "http://localhost:8081/test/hello")))
                    .addRoute(new Route(UNREACHABLE_ROUTE_PATH,
                            Origin.of(UNREACHABLE_ORIGIN_ID, "http://localhost:1/nowhere"))).build();
        }
    }

    @Path("/test/hello")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello from origin!";
        }
    }
}
