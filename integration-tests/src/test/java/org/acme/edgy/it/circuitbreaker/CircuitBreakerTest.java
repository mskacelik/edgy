package org.acme.edgy.it.circuitbreaker;

import io.quarkus.deployment.annotations.Produce;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.vertx.core.Vertx;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;

@QuarkusTest
class CircuitBreakerTest {
    @Test
    void testTimeoutFailed() {
        long timeout = 450L;
        // returns 502 status code, due to failed future
        RestAssured.given().when().body(timeout).post("/timeout").then().statusCode(not(OK));
    }

    @Test
    void testTimeoutSuccess() {
        long timeout = 200L;
        RestAssured.given().body(timeout).when().post("/timeout").then().statusCode(OK);
    }

    @Test
    void retryFailed() {
        resetCounter();
        int numberOfRetriesUntilSuccess = 10;
        // returns 502 status code, due to failed future
        RestAssured.given().when().body(numberOfRetriesUntilSuccess).post("/retry").then()
                .statusCode(not(OK));
    }

    @Test
    void retrySuccess() {
        resetCounter();
        int numberOfRetriesUntilSuccess = 2;
        RestAssured.given().when().body(numberOfRetriesUntilSuccess).post("/retry").then()
                .statusCode(OK);
    }

    private void resetCounter() {
        RestAssured.given().when().get("/api/circuit-breaker/counter-reset").then().statusCode(OK);
    }
}
