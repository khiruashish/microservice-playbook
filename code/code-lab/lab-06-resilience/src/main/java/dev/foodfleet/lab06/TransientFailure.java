package dev.foodfleet.lab06;

import java.util.function.Predicate;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Which failures are worth retrying: timeouts, refused connections and 5xx. A 4xx is a wrong request and would fail
 * the same way again; a refusal from our own breaker or bulkhead isn't a failure of the dependency at all.
 */
public class TransientFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        return t instanceof ResourceAccessException || t instanceof HttpServerErrorException;
    }
}
