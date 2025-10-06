package org.acme.edgy.runtime.builtins.requests;

import org.acme.edgy.runtime.api.RequestTransformer;
import io.smallrye.faulttolerance.api.TypedGuard;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;

public class FaultToleranceGuard implements RequestTransformer {
    // TODO: have in mind that it is probably better to use it as one of the last request
    // transformers, so it is as close to the ReverseProxy#sendProxyRequest as possible

    private final TypedGuard<Future<ProxyResponse>> guard;

    // TODO: use builder instead ?
    public FaultToleranceGuard(TypedGuard<Future<ProxyResponse>> guard) {
        this.guard = guard;
    }

    @Override
    public Future<ProxyResponse> apply(ProxyContext proxyContext) {
        try { // for testing purposes, returns itself
            return guard.call(() -> proxyContext.sendRequest().onComplete(ar -> {
                // System.err.println("null");
                throw new RuntimeException("Simulated failure");
            }));
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }
}
