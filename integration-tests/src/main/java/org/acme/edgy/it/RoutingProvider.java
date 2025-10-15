package org.acme.edgy.it;

import jakarta.enterprise.inject.Produces;

import java.time.Duration;
import java.util.function.Function;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;

import org.acme.edgy.runtime.api.Origin;
import org.acme.edgy.runtime.api.PathMode;
import org.acme.edgy.runtime.api.Route;
import org.acme.edgy.runtime.api.RoutingConfiguration;
import org.acme.edgy.runtime.builtins.requests.RequestRetryAdder;
import org.acme.edgy.runtime.builtins.requests.RequestTimeLimiterAdder;

class RoutingProvider {

    @Produces
    RoutingConfiguration routing() {
        return new RoutingConfigurationBuilder(new RoutingConfiguration())
                .addRoutes(this::storkRoutes)
                .addRoutes(this::timeLimiterRoutes)
                .addRoutes(this::retryRoutes)
                .build();
    }

    private RoutingConfiguration storkRoutes(RoutingConfiguration routingConfiguration) {
        return routingConfiguration.addRoute(
                new Route("/stork", Origin.of("stork://my-service/stork/hello"), PathMode.FIXED));
    }

    private RoutingConfiguration timeLimiterRoutes(RoutingConfiguration routingConfiguration) {
        TimeLimiter timeLimiter = TimeLimiter.of(Duration.ofMillis(400L));

        return routingConfiguration.addRoute(new Route("/time-limiter",
                Origin.of("http://localhost:8081/api/resilience4j/time-limiter"), PathMode.FIXED)
                .addRequestTransformer(new RequestTimeLimiterAdder(timeLimiter)));
    }

    private RoutingConfiguration retryRoutes(RoutingConfiguration routingConfiguration) {
        RequestRetryAdder.RetryOptions options = new RequestRetryAdder.RetryOptions("id")
                .maxAttempts(4)
                .waitDuration(Duration.ofMillis(100L));
        return routingConfiguration.addRoute(new Route("/retry",
                Origin.of("http://localhost:8081/api/resilience4j/retry"), PathMode.FIXED)
                .addRequestTransformer(new RequestRetryAdder(options)));
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
