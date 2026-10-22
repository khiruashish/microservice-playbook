package dev.foodfleet.ch18.resilience;

import java.util.function.Consumer;

/**
 * An asynchronous call: start it, and it reports its outcome once, later. Every resilience pattern
 * below is a {@code Call} that wraps another {@code Call}, so they compose in any order, exactly
 * like Resilience4j decorators or Spring's {@code @Retryable} and {@code @ConcurrencyLimit} proxies.
 */
@FunctionalInterface
public interface Call {

    void start(Consumer<Outcome> done);
}
