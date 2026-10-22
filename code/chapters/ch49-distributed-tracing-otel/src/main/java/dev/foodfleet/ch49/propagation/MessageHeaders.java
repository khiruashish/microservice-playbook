package dev.foodfleet.ch49.propagation;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries trace context and baggage through anything that has string headers but no instrumentation: an outbox row,
 * a file drop, a hand-written queue, a scheduled job's payload. The instrumented clients (HTTP, Kafka, NATS, JMS) do
 * exactly this for you; this is the same thing by hand, with whichever propagators are configured (W3C traceparent
 * and baggage by default).
 */
public final class MessageHeaders {

    private static final TextMapSetter<Map<String, String>> SETTER = (carrier, key, value) -> carrier.put(key, value);

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final TextMapPropagator propagator;

    public MessageHeaders(TextMapPropagator propagator) {
        this.propagator = propagator;
    }

    /** With whatever propagators the agent or SDK configured. */
    public static MessageHeaders global() {
        return new MessageHeaders(GlobalOpenTelemetry.getPropagators().getTextMapPropagator());
    }

    /** The headers that carry {@code context}, to store next to the message. */
    public Map<String, String> inject(Context context) {
        Map<String, String> headers = new LinkedHashMap<>();
        propagator.inject(context, headers, SETTER);
        return headers;
    }

    /** The context a message was sent in, to continue its trace (or link to it) when the message is handled. */
    public Context extract(Map<String, String> headers) {
        return propagator.extract(Context.root(), headers, GETTER);
    }
}
