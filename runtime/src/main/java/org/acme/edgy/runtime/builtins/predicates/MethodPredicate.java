package org.acme.edgy.runtime.builtins.predicates;

import java.util.Objects;

import org.acme.edgy.runtime.api.RoutingPredicate;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

public class MethodPredicate implements RoutingPredicate {

    private final HttpMethod method;

    public MethodPredicate(HttpMethod method) {
        this.method = Objects.requireNonNull(method);
    }

    @Override
    public boolean test(RoutingContext rc) {
        return method.equals(rc.request().method());
    }
}
