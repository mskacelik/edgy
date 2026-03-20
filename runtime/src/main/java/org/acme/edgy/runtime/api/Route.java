package org.acme.edgy.runtime.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class Route {

    private final String path;
    private final Origin origin;
    private final PathMode pathMode;
    private RoutingPredicate predicate = rc -> true;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final Deque<ResponseTransformer> responseTransformers = new ArrayDeque<>();

    public Route(String path, Origin origin, PathMode pathMode) {
        this.path = path;
        this.origin = origin;
        this.pathMode = pathMode;
    }

    public String path() {
        return path;
    }

    public Origin origin() {
        return origin;
    }

    public PathMode pathMode() {
        return pathMode;
    }

    public RoutingPredicate predicate() {
        return predicate;
    }

    public Route setPredicate(RoutingPredicate predicate) {
        this.predicate = Objects.requireNonNull(predicate);
        return this;
    }

    public List<RequestTransformer> requestTransformers() {
        return requestTransformers;
    }

    public Route addRequestTransformer(RequestTransformer requestTransformer) {
        requestTransformers.add(requestTransformer);
        return this;
    }

    public Deque<ResponseTransformer> responseTransformers() {
        return responseTransformers;
    }

    public Route addResponseTransformer(ResponseTransformer responseTransformer) {
        responseTransformers.addFirst(responseTransformer);
        return this;
    }
}
