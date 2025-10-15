package org.acme.edgy.it.circuitbreaker;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/circuit-breaker")
class CircuitBreakerResourceApi {
    AtomicInteger counter = new AtomicInteger(0);

    @POST
    @Path("/timeout")
    public Uni<RestResponse<Void>> timeout(long timeout) throws InterruptedException {
        return Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofMillis(timeout))
                .replaceWith(RestResponse.ok());
    }

    @POST
    @Path("/retry")
    public RestResponse<Void> retry(int numberOfRetries) {
        if (counter.incrementAndGet() <= numberOfRetries) {
            return RestResponse.serverError();
        }
        return RestResponse.ok();
    }

    @GET
    @Path("/counter-reset")
    public RestResponse<Void> counterReset() {
        counter.set(0);
        return RestResponse.ok();
    }
}
