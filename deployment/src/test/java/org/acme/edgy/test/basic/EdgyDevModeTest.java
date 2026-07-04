package org.acme.edgy.test.basic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.Matchers.is;

import org.acme.edgy.runtime.api.utils.StatusCode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

class EdgyDevModeTest {

    static final int CORRECT_WIREMOCK_PORT = 9091;

    private static final String WIREMOCK_RESPONSE = "Hello from WireMock";
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(options().port(CORRECT_WIREMOCK_PORT));
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.get("/api/hello")
                .willReturn(aResponse().withBody(WIREMOCK_RESPONSE).withStatus(StatusCode.OK)));
    }

    @RegisterExtension
    private static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(DevModeRoutingProvider.class));

    @Test
    void testHotReloadRoutingConfiguration() {
        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.BAD_GATEWAY);
        wireMockServer.verify(exactly(0), getRequestedFor(urlEqualTo("/api/hello")));

        devModeTest.modifySourceFile(DevModeRoutingProvider.class,
                s -> s.replace(DevModeRoutingProvider.INCORRECT_WIREMOCK_PORT, String.valueOf(CORRECT_WIREMOCK_PORT)));

        RestAssured.given()
                .get("/hello")
                .then()
                .statusCode(StatusCode.OK)
                .body(is(WIREMOCK_RESPONSE));
        wireMockServer.verify(exactly(1), getRequestedFor(urlEqualTo("/api/hello")));
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }
}
