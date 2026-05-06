package org.acme.edgy.runtime.builtins.transformers;

public class BodySizeLimitExceededException extends RuntimeException {

    public BodySizeLimitExceededException(String message) {
        super(message);
    }
}
