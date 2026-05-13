package org.acme.edgy.runtime.interceptors;

import java.util.List;

import org.acme.edgy.runtime.api.ProxyObservation;
import org.acme.edgy.runtime.api.ProxyObserver;
import org.acme.edgy.runtime.api.Route;

import io.vertx.core.Future;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;

public class ObservingProxyInterceptor implements ProxyInterceptor {

    private static final String OBSERVATIONS_KEY = "edgy.observations";

    private final List<ProxyObserver> observers;
    private final Route route;

    public ObservingProxyInterceptor(List<ProxyObserver> observers, Route route) {
        this.observers = observers;
        this.route = route;
    }

    @Override
    public Future<ProxyResponse> handleProxyRequest(ProxyContext context) {
        List<ProxyObservation> observations = observers.stream()
                .map(observer -> observer.observe(context, route))
                .toList();
        context.set(OBSERVATIONS_KEY, observations);
        return context.sendRequest();
    }

    @Override
    public Future<Void> handleProxyResponse(ProxyContext context) {
        List<ProxyObservation> observations = (List<ProxyObservation>) context.get(OBSERVATIONS_KEY, List.class);
        if (observations == null) {
            return context.sendResponse();
        }
        return context.sendResponse()
                .onSuccess(v -> observations.forEach(observation -> observation.end(context)))
                .onFailure(error -> observations.forEach(observation -> observation.error(context, error)));
    }
}
