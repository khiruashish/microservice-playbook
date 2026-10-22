package dev.foodfleet.ch50.mdc;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a request's correlation fields into the MDC for exactly as long as the request runs on this thread. The MDC is
 * a thread-local: a value left behind on a pooled thread is logged with the next request that thread serves.
 *
 * <pre>{@code
 * try (RequestMdc ignored = RequestMdc.open("request.id", id, "tenant", tenant)) { ... }
 * }</pre>
 */
public final class RequestMdc implements AutoCloseable {

    private final List<MDC.MDCCloseable> entries = new ArrayList<>();

    private RequestMdc() {
    }

    public static RequestMdc open(String... keyValues) {
        RequestMdc r = new RequestMdc();
        for (int i = 0; i < keyValues.length; i += 2) {
            r.entries.add(MDC.putCloseable(keyValues[i], keyValues[i + 1]));
        }
        return r;
    }

    @Override
    public void close() {
        for (MDC.MDCCloseable e : entries) {
            e.close();
        }
    }
}
