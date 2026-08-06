package org.acme.edgy.runtime.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.acme.edgy.runtime.api.resiliency.GuardHandler;
import org.acme.edgy.runtime.api.utils.SegmentUtils;
import org.acme.edgy.runtime.api.utils.SegmentUtils.CompiledPath;
import org.acme.edgy.runtime.interceptors.resiliency.GuardInterceptor;

import io.vertx.httpproxy.ProxyInterceptor;

public class Route {

    private final String path;
    private final Origin origin;
    private final PathMode pathMode;

    private final CompiledPath transformedPath;
    private final boolean regexRoute;
    private final String resolvedPath;
    private final String resolvedOriginPath;

    private RoutingPredicate predicate = rc -> true;
    private final List<RequestTransformer> requestTransformers = new ArrayList<>();
    private final Deque<ResponseTransformer> responseTransformers = new ArrayDeque<>();
    private ProxyInterceptor guardInterceptor;

    public Route(String path, Origin origin) {
        this(path, origin, PathMode.BASIC);
    }

    public Route(String path, Origin origin, PathMode pathMode) {
        this.path = path;
        this.origin = origin;
        this.pathMode = pathMode;

        if (pathMode == PathMode.BASIC && SegmentUtils.needsRegexRouting(path)) {
            this.transformedPath = SegmentUtils.transform(path);
            this.regexRoute = true;
            this.resolvedPath = transformedPath.compiledPattern().pattern();
        } else if (pathMode == PathMode.REGEXP) {
            this.transformedPath = SegmentUtils.fromRegexp(path);
            this.regexRoute = true;
            this.resolvedPath = path;
        } else {
            this.transformedPath = null;
            this.regexRoute = false;
            this.resolvedPath = path;
        }

        this.resolvedOriginPath = computeResolvedOriginPath();
    }

    private String computeResolvedOriginPath() {
        String originPath = origin.path();

        if (hasWildcard() && !originPath.contains("{")) {
            if (!originPath.endsWith("/")) {
                originPath += "/";
            }
            return originPath + "{+" + SegmentUtils.SUFFIX + "}";
        }

        return SegmentUtils.toReservedExpansion(originPath);
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

    public String resolvedPath() {
        return resolvedPath;
    }

    public boolean needsRegexRouting() {
        return regexRoute;
    }

    public String resolvedOriginPath() {
        return resolvedOriginPath;
    }

    public boolean hasWildcard() {
        return path.endsWith("/*");
    }

    public Map<String, String> extractPathVariables(String requestUri) {
        return SegmentUtils.extractPathVariables(transformedPath, requestUri);
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

    /**
     * Sets a resilience guard for this route.
     *
     * @param handler the guard handler carrying expectation, fallback, and payload limit
     * @see GuardHandler
     */
    public Route setGuardHandler(GuardHandler handler) {
        this.guardInterceptor = new GuardInterceptor(handler);
        return this;
    }

    /**
     * Returns the configured guard interceptor, if any.
     */
    public Optional<ProxyInterceptor> guardInterceptor() {
        return Optional.ofNullable(guardInterceptor);
    }
}
