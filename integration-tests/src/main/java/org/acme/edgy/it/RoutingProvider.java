package org.acme.edgy.it;

import java.util.function.Function;
import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.builtins.requests.CircuitBreakerAdder;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.ProxyResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

class RoutingProvider {

    @Produces
    RoutingConfiguration routing(Vertx vertx) {
        return new RoutingConfigurationBuilder(new RoutingConfiguration())
                .addRoutes(this::storkRoutes)
                .addRoutes(
                        routingConfiguration -> circuitBreakerRoutes(routingConfiguration, vertx))
                .build();
    }

    private RoutingConfiguration storkRoutes(RoutingConfiguration routingConfiguration) {
        return routingConfiguration.addRoute(
                new Route("/test", Origin.of("stork://my-service/test/hello"), PathMode.FIXED));
    }

    private RoutingConfiguration circuitBreakerRoutes(RoutingConfiguration routingConfiguration,
            Vertx vertx) {
        long timeout = 400L;
        int maxRetries = 3;

        CircuitBreaker timeoutCircuitBreaker = CircuitBreaker.create("timeout-circuit-breaker",
                vertx, new CircuitBreakerOptions().setTimeout(timeout));

        CircuitBreaker retryCircuitBreaker = CircuitBreaker
                .create("retry-circuit-breaker", vertx,
                        new CircuitBreakerOptions().setMaxFailures(maxRetries)
                                .setMaxRetries(maxRetries))
                .<ProxyResponse>failurePolicy(ar -> {
                    if (ar.failed()) {
                        return true;
                    }

                    ProxyResponse response = ar.result();
                    return (response.getStatusCode() / 100) != 2;
                });

        return routingConfiguration.addRoute(new Route("/timeout",
                Origin.of("http://localhost:8081/api/circuit-breaker/timeout"), PathMode.FIXED)
                        .addRequestTransformer(new CircuitBreakerAdder(timeoutCircuitBreaker)))
                .addRoute(new Route("/retry",
                        Origin.of("http://localhost:8081/api/circuit-breaker/retry"),
                        PathMode.FIXED).addRequestTransformer(
                                new CircuitBreakerAdder(retryCircuitBreaker)));
    }

    // for clear structure of tested routes
    static class RoutingConfigurationBuilder {
        private RoutingConfiguration routingConfiguration;

        RoutingConfigurationBuilder(RoutingConfiguration routingConfiguration) {
            this.routingConfiguration = routingConfiguration;
        }

        RoutingConfigurationBuilder addRoutes(
                Function<RoutingConfiguration, RoutingConfiguration> routingConfiguration) {
            routingConfiguration.apply(this.routingConfiguration);
            return this;
        }

        RoutingConfiguration build() {
            return routingConfiguration;
        }

    }
}
