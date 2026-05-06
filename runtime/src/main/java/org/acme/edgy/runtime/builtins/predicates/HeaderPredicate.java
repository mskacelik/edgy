package org.acme.edgy.runtime.builtins.predicates;

import java.util.Objects;
import java.util.regex.Pattern;

import org.acme.edgy.runtime.api.RoutingPredicate;

import io.vertx.ext.web.RoutingContext;

public class HeaderPredicate implements RoutingPredicate {

    private final String name;
    private final Pattern pattern;

    public HeaderPredicate(String name, Pattern pattern) {
        this.name = Objects.requireNonNull(name);
        this.pattern = Objects.requireNonNull(pattern);
    }

    public HeaderPredicate(String name, String exactValue) {
        this(name, Pattern.compile(Pattern.quote(Objects.requireNonNull(exactValue))));
    }

    @Override
    public boolean test(RoutingContext rc) {
        String value = rc.request().getHeader(name);
        if (value == null) {
            return false;
        }
        return pattern.matcher(value).matches();
    }
}
