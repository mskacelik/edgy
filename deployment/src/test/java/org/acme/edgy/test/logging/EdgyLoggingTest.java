package org.acme.edgy.test.logging;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.logging.Level;

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

import io.quarkus.test.QuarkusExtensionTest;

class EdgyLoggingTest {

    private static final String HELLO_ORIGIN_ID = "origin-1";
    private static final String HELLO_ROUTE_PATH = "/hello";
    private static final String UNREACHABLE_ORIGIN_ID = "unreachable-origin";
    private static final String UNREACHABLE_ROUTE_PATH = "/unreachable";

    @RegisterExtension
    private static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(RoutingProvider.class, TestApi.class))
            .overrideConfigKey("edgy.logging.enabled", "true")
            .setLogRecordPredicate(rec -> rec.getLoggerName().contains("LoggingProxyObserver"))
            .assertLogRecords(records -> {
                assertThat(records).as("Expected at least 2 log records from LoggingProxyObserver")
                        .hasSizeGreaterThanOrEqualTo(2);

                assertThat(records)
                        .withFailMessage("No INFO log with status=200 found")
                        .filteredOn(r -> String.format(r.getMessage(), r.getParameters()).contains("status=200"))
                        .first()
                        .satisfies(successRec -> {
                            String msg = String.format(successRec.getMessage(), successRec.getParameters());
                            SoftAssertions.assertSoftly(softly -> {
                                softly.assertThat(msg).as("Log should contain route path").contains(HELLO_ROUTE_PATH);
                                softly.assertThat(msg).as("Log should contain origin identifier")
                                        .contains(HELLO_ORIGIN_ID);
                                softly.assertThat(msg).as("Log should contain duration").contains("ms");
                            });
                        });

                assertThat(records)
                        .withFailMessage("No WARN log with status=502 found")
                        .filteredOn(r -> String.format(r.getMessage(), r.getParameters()).contains("status=502"))
                        .first()
                        .satisfies(errorRec -> {
                            String msg = String.format(errorRec.getMessage(), errorRec.getParameters());
                            SoftAssertions.assertSoftly(softly -> {
                                softly.assertThat(errorRec.getLevel().intValue())
                                        .as("5xx responses should be logged at WARN, but was: %s",
                                                errorRec.getLevel())
                                        .isGreaterThanOrEqualTo(Level.WARNING.intValue());
                                softly.assertThat(msg).as("Log should contain route path")
                                        .contains(UNREACHABLE_ROUTE_PATH);
                                softly.assertThat(msg).as("Log should contain origin identifier")
                                        .contains(UNREACHABLE_ORIGIN_ID);
                                softly.assertThat(msg).as("Log should contain duration").contains("ms");
                            });
                        });
            });

    @Test
    void test_loggingEmitsInfoForProxiedRequest() {
        given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is("Hello from origin!"));
    }

    @Test
    void test_loggingEmitsWarnForServerError() {
        given()
                .get("/unreachable")
                .then()
                .statusCode(StatusCode.BAD_GATEWAY);
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
