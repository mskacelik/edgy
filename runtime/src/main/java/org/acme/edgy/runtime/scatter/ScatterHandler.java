package org.acme.edgy.runtime.scatter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.acme.edgy.runtime.api.Leg;
import org.acme.edgy.runtime.api.LegResponse;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.ScatterObservation;
import org.acme.edgy.runtime.api.ScatterRoute;
import org.acme.edgy.runtime.api.utils.StatusCode;
import org.acme.edgy.runtime.interceptors.scatter.ScatterMethodBodyInterceptor;
import org.jboss.logging.Logger;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;

public class ScatterHandler implements Handler<RoutingContext> {

    private static final Logger logger = Logger.getLogger(ScatterHandler.class);

    private final ScatterRoute scatterRoute;
    private final List<LegDefinition> legDefinitions;
    private final List<ProxyObserver> observers;

    public record LegDefinition(
            Leg leg,
            HttpClient httpClient,
            List<ProxyInterceptor> interceptors,
            boolean requiresBody) {
    }

    public ScatterHandler(ScatterRoute scatterRoute, List<LegDefinition> legDefinitions,
            List<ProxyObserver> observers) {
        this.scatterRoute = scatterRoute;
        this.legDefinitions = legDefinitions;
        this.observers = observers;
    }

    @Override
    public void handle(RoutingContext rc) {
        if (!scatterRoute.predicate().test(rc)) {
            rc.next();
            return;
        }

        List<ScatterObservation> observations = observers.stream()
                .map(observer -> observer.observeScatter(rc, scatterRoute))
                .toList();

        HttpServerRequest request = rc.request();

        boolean anyLegNeedsBody = legDefinitions.stream()
                .anyMatch(LegDefinition::requiresBody);

        if (anyLegNeedsBody) {
            // body size is already bounded by quarkus.http.limits.max-body-size
            // at the Vert.x HTTP server layer
            Buffer bodyAccumulator = Buffer.buffer();
            request.handler(bodyAccumulator::appendBuffer);
            request.endHandler(v -> dispatchLegs(observations, request, bodyAccumulator, rc));
            request.resume();
        } else {
            // no leg needs the body - drain it without buffering and fire immediately
            request.resume();
            dispatchLegs(observations, request, Buffer.buffer(), rc);
        }
    }

    private void dispatchLegs(List<ScatterObservation> observations,
            HttpServerRequest request, Buffer bufferedBody, RoutingContext rc) {
        fireLegs(observations, request, bufferedBody)
                .compose(responses -> {
                    observations.forEach(obs -> obs.end(responses));
                    return scatterRoute.composer().apply(responses);
                })
                .onSuccess(composedBody -> rc.response()
                        .setStatusCode(StatusCode.OK)
                        .end(composedBody))
                .onFailure(error -> {
                    observations.forEach(obs -> obs.error(error));
                    logger.errorf(error, "Scatter/gather failed for route %s", scatterRoute.path());
                    if (!rc.response().ended()) {
                        rc.response().setStatusCode(StatusCode.BAD_GATEWAY).end();
                    }
                });
    }

    private Future<List<LegResponse>> fireLegs(List<ScatterObservation> observations,
            HttpServerRequest originalRequest, Buffer bufferedBody) {
        List<Future<LegResponse>> legFutures = new ArrayList<>();

        for (LegDefinition legDef : legDefinitions) {
            Leg leg = legDef.leg();

            Body body = bufferedBody != null && bufferedBody.length() > 0
                    ? Body.body(bufferedBody.copy())
                    : null;

            Supplier<Future<LegResponse>> execution = () -> {
                ScatterLegRequest legRequest = new ScatterLegRequest(originalRequest);
                ProxyRequest proxyRequest = ProxyRequest.reverseProxy(legRequest);

                ScatterLegContext context = new ScatterLegContext(
                        proxyRequest,
                        legDef.httpClient(),
                        leg.origin().originRequestProvider(),
                        leg.origin().identifier(),
                        legDef.interceptors());

                context.set(ScatterMethodBodyInterceptor.BUFFERED_BODY_KEY, body);
                return context.execute();
            };

            for (ScatterObservation obs : observations) {
                Supplier<Future<LegResponse>> prev = execution;
                execution = () -> obs.wrapLeg(leg, prev);
            }

            legFutures.add(execution.get());
        }

        return switch (scatterRoute.failureMode()) {
            case FAIL_FAST -> Future.all(legFutures).map(cf -> {
                List<LegResponse> results = new ArrayList<>();
                for (Future<LegResponse> legFuture : legFutures) {
                    results.add(legFuture.result());
                }
                return results;
            });
            case PARTIAL -> {
                List<Future<LegResponse>> resilientFutures = new ArrayList<>();
                for (int i = 0; i < legFutures.size(); i++) {
                    String originId = legDefinitions.get(i).leg().origin().identifier();
                    resilientFutures.add(legFutures.get(i)
                            .recover(throwable -> Future.succeededFuture(new LegResponse(
                                    originId, null, -1, MultiMap.caseInsensitiveMultiMap(), throwable))));
                }
                yield Future.join(resilientFutures).map(cf -> {
                    List<LegResponse> results = new ArrayList<>();
                    for (Future<LegResponse> f : resilientFutures) {
                        results.add(f.result());
                    }
                    return results;
                });
            }
        };
    }

}
