package org.acme.edgy.runtime.api.resiliency;

/**
 * Thrown when a request is rejected because the rate limit has been exceeded.
 * <p>
 * This exception is used as a signal, not a diagnostic error, so it suppresses
 * stack trace capture to avoid the overhead of {@code fillInStackTrace()}.
 */
public class RateLimitRejectedException extends RuntimeException {

    private final long retryAfterMillis;

    public RateLimitRejectedException(long retryAfterMillis) {
        super(null, null, false, false);
        this.retryAfterMillis = retryAfterMillis;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
