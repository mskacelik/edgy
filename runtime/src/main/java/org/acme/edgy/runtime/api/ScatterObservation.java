package org.acme.edgy.runtime.api;

import java.util.List;
import java.util.function.Supplier;

import io.vertx.core.Future;

/**
 * Handle for an in-flight scatter/gather observation.
 * Created by {@link ProxyObserver#observeScatter} and completed via
 * {@link #end} or {@link #error}.
 */
public interface ScatterObservation {

    ScatterObservation NOOP = new ScatterObservation() {
        @Override
        public Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution) {
            return execution.get();
        }

        @Override
        public void end(List<LegResponse> responses) {
            // no-op
        }

        @Override
        public void error(Throwable error) {
            // no-op
        }
    };

    /**
     * Wraps the execution of a scatter leg. The observer can set up context
     * (e.g. tracing spans) before calling the execution supplier, and clean up
     * when the returned future completes.
     */
    Future<LegResponse> wrapLeg(Leg leg, Supplier<Future<LegResponse>> execution);

    /**
     * Called when all legs completed and the composer produced a result.
     */
    void end(List<LegResponse> responses);

    /**
     * Called when the scatter/gather operation failed.
     */
    void error(Throwable error);
}
