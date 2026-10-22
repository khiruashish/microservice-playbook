package dev.foodfleet.ch49.baggage;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Baggage is propagated to every outgoing call, inside FoodFleet and out of it, and is never recorded on spans unless
 * code copies it. FoodFleet's rules: only allow-listed keys with small values enter at the edge; nothing leaves for
 * a third party; the few keys worth querying are copied onto spans explicitly.
 */
public final class BaggagePolicy {

    /** Keys FoodFleet propagates: low-sensitivity routing and context, never identities or secrets. */
    public static final Set<String> ALLOWED = Set.of("tenant", "city.tier", "experiment", "priority");

    /** The W3C Baggage specification's limit is 8,192 bytes for the header; FoodFleet keeps far below it. */
    public static final int MAX_HEADER_BYTES = 512;

    private BaggagePolicy() {
    }

    /** At the edge: keep allow-listed entries, drop the rest, and stop adding once the header would be too big. */
    public static Baggage sanitize(Baggage incoming) {
        BaggageBuilder b = Baggage.builder();
        int[] size = {0};
        incoming.forEach((key, entry) -> {
            if (!ALLOWED.contains(key)) {
                return;
            }
            int entryBytes = (key + "=" + entry.getValue()).getBytes(StandardCharsets.UTF_8).length + 1;
            if (size[0] + entryBytes <= MAX_HEADER_BYTES) {
                b.put(key, entry.getValue());
                size[0] += entryBytes;
            }
        });
        return b.build();
    }

    /** Runs a call to a third party (a payment gateway, a maps API) with no baggage in the context. */
    public static <T> T withoutBaggage(Supplier<T> call) {
        try (Scope ignored = Context.current().with(Baggage.empty()).makeCurrent()) {
            return call.get();
        }
    }
}
