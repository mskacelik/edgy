package org.acme.edgy.runtime.api;

import java.util.function.Predicate;

import io.vertx.ext.web.RoutingContext;

@FunctionalInterface
public interface RoutingPredicate extends Predicate<RoutingContext> {

    // TODO: should we have async predicates?

    default RoutingPredicate and(RoutingPredicate other) {
        return rc -> test(rc) && other.test(rc);
    }

    default RoutingPredicate or(RoutingPredicate other) {
        return rc -> test(rc) || other.test(rc);
    }

    @Override
    default RoutingPredicate negate() {
        return rc -> !test(rc);
    }
}
