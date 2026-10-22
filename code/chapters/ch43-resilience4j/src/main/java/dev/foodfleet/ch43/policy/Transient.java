package dev.foodfleet.ch43.policy;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Is a failure worth retrying? Resilience4j's {@code retryExceptions(IOException.class)} matches the thrown
 * exception's own class only, and Spring's {@code RestClient} wraps I/O errors in {@code ResourceAccessException}
 * (a {@code RuntimeException}), so that configuration never retries a RestClient call. This predicate looks through
 * the cause chain, the way Spring Framework 7's {@code @Retryable(includes = ...)} does.
 */
public final class Transient {

    private Transient() {
    }

    public static boolean failure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof IOException || c instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
