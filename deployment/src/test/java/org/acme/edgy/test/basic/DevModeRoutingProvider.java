package org.acme.edgy.test.basic;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;

class DevModeRoutingProvider {

    static final String INCORRECT_WIREMOCK_PORT = "19999";

    @Produces
    @Singleton
    RoutingConfiguration routingConfiguration() {
        return RoutingConfiguration.builder()
                .addRoute(new Route("/hello",
                        Origin.of("origin-1", "http://localhost:" + INCORRECT_WIREMOCK_PORT + "/api/hello")))
                .build();
    }
}
