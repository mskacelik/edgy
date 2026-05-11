package org.acme.edgy.runtime.builtins.predicates;

import java.util.Objects;
import java.util.regex.Pattern;

import org.acme.edgy.runtime.api.RoutingPredicate;

import io.vertx.ext.web.RoutingContext;

public class QueryParameterPredicate implements RoutingPredicate {

    private final String name;
    private final Pattern pattern;
    private final boolean existenceOnly;

    public QueryParameterPredicate(String name) {
        this.name = Objects.requireNonNull(name);
        this.pattern = null;
        this.existenceOnly = true;
    }

    public QueryParameterPredicate(String name, Pattern pattern) {
        this.name = Objects.requireNonNull(name);
        this.pattern = Objects.requireNonNull(pattern);
        this.existenceOnly = false;
    }

    public QueryParameterPredicate(String name, String exactValue) {
        this(name, Pattern.compile(Pattern.quote(Objects.requireNonNull(exactValue))));
    }

    @Override
    public boolean test(RoutingContext rc) {
        String value = rc.request().getParam(name);
        if (value == null) {
            return false;
        }
        if (existenceOnly) {
            return true;
        }
        return pattern.matcher(value).matches();
    }
}
