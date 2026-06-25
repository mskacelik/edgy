package org.acme.edgy.test.tracing;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.assertj.core.api.SoftAssertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;

class EdgyTracingDisabledTest {

    static final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class, SpanExporterProducer.class))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry-deployment",
                    System.getProperty("project.quarkus.version"))))
            .overrideConfigKey("edgy.tracing.enabled", "false");

    @Test
    void test_proxyWorksWithoutEdgyAttributes() {
        given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello from origin!"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(spanExporter.getFinishedSpanItems())
                    .withFailMessage("No root SERVER span found in: %s", spanExporter.getFinishedSpanItems())
                    .filteredOn(s -> s.getKind() == SpanKind.SERVER
                            && SpanId.getInvalid().equals(s.getParentSpanId()))
                    .first()
                    .satisfies(serverSpan -> {
                        var attrs = serverSpan.getAttributes();
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(attrs.get(AttributeKey.stringKey("edgy.origin.url"))).isNull();
                            softly.assertThat(attrs.get(AttributeKey.stringKey("edgy.origin.id"))).isNull();
                            softly.assertThat(attrs.get(AttributeKey.stringKey("edgy.route"))).isNull();
                        });
                    });
        });
    }

    static class RoutingProvider {

        @Produces
        @Singleton
        RoutingConfiguration routing() {
            return RoutingConfiguration.builder()
                    .addRoute(new Route("/hello", Origin.of("origin-1", "http://localhost:8081/test/hello"))).build();
        }
    }

    @Path("/test/hello")
    static class TestApi {

        @GET
        public String hello() {
            return "Hello from origin!";
        }
    }

    @ApplicationScoped
    static class SpanExporterProducer {

        @Produces
        @Singleton
        public InMemorySpanExporter spanExporter() {
            return spanExporter;
        }
    }
}
