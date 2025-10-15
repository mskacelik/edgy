package org.acme.edgy.it.resilience4j;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.OK;
import static org.jboss.resteasy.reactive.RestResponse.StatusCode.REQUEST_TIMEOUT;

@QuarkusTest
class Resilience4jTest {
    @Test
    void testTimeoutFailed() {
        long timeout = 450L;
        RestAssured.given().when().body(timeout).post("/time-limiter").then().statusCode(REQUEST_TIMEOUT);
    }

    @Test
    void testTimeoutSuccess() {
        long timeout = 10L;
        RestAssured.given().body(timeout).when().post("/time-limiter").then().statusCode(OK);
    }

    @Test
    void testRetryFailed() {
        resetCounter();
        int numberOfRetriesUntilSuccess = 10;
        RestAssured.given().when().body(numberOfRetriesUntilSuccess).post("/retry").then().statusCode(not(OK));
    }

    @Test
    void testRetrySuccess() {
        resetCounter();
        int numberOfRetriesUntilSuccess = 2;
        RestAssured.given().when().body(numberOfRetriesUntilSuccess).post("/retry").then().statusCode(OK);
    }

    private void resetCounter() {
        RestAssured.given().when().get("/api/resilience4j/counter-reset").then().statusCode(OK);
    }
}
