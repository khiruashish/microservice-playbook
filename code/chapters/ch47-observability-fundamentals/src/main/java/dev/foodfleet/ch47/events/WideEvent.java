package dev.foodfleet.ch47.events;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One structured event per request per service, carrying every field that might explain it: who (tenant, user),
 * what (route, payment method, bank), where (service, version, pod, zone), how it went (status, duration, retries,
 * cache hits) and the trace it belongs to. Recorded for every request, unsampled, so any question can be asked of
 * it later, including ones nobody thought of when the dashboard was built.
 *
 * <pre>{@code
 * WideEvent e = WideEvent.start("payment-service", "POST /payments", clock)
 *         .with("tenant", tenant).with("payment.method", "UPI").with("payment.bank", bank);
 * ...
 * writer.write(e.finish("ok"));
 * }</pre>
 */
public final class WideEvent {

    private final Instant start;
    private final long startNanos;
    private final Map<String, Object> fields = new LinkedHashMap<>();

    private WideEvent(String service, String operation, Clock clock) {
        this.start = clock.instant();
        this.startNanos = System.nanoTime();
        fields.put("service", Objects.requireNonNull(service));
        fields.put("operation", Objects.requireNonNull(operation));
    }

    public static WideEvent start(String service, String operation) {
        return new WideEvent(service, operation, Clock.systemUTC());
    }

    public static WideEvent start(String service, String operation, Clock clock) {
        return new WideEvent(service, operation, clock);
    }

    /** Adds or replaces a field; null values are left out rather than written as "null". */
    public WideEvent with(String key, Object value) {
        if (value != null) {
            fields.put(key, value);
        }
        return this;
    }

    /** Adds {@code delta} to a numeric field (retries, cache misses, rows read). */
    public WideEvent count(String key, long delta) {
        fields.merge(key, delta, (a, b) -> ((Number) a).longValue() + ((Number) b).longValue());
        return this;
    }

    public Record finish(String outcome) {
        return finish(outcome, (System.nanoTime() - startNanos) / 1_000_000.0);
    }

    /** Finishes with an explicit duration (tests, replayed events). */
    public Record finish(String outcome, double durationMs) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("timestamp", start.toString());
        f.putAll(fields);
        f.put("outcome", outcome);
        f.put("duration_ms", Math.round(durationMs * 10) / 10.0);
        return new Record(Collections.unmodifiableMap(f));
    }

    /** A finished event: fields in insertion order, ready to write as one JSON line. */
    public record Record(Map<String, Object> fields) {

        public Object get(String key) {
            return fields.get(key);
        }

        public double durationMs() {
            return ((Number) fields.get("duration_ms")).doubleValue();
        }

        public String toJson() {
            StringBuilder b = new StringBuilder(256).append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                quote(b, e.getKey()).append(':');
                Object v = e.getValue();
                if (v instanceof Number || v instanceof Boolean) {
                    b.append(v);
                } else {
                    quote(b, String.valueOf(v));
                }
            }
            return b.append('}').toString();
        }

        private static StringBuilder quote(StringBuilder b, String s) {
            b.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '\t' -> b.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            b.append(String.format("\\u%04x", (int) c));
                        } else {
                            b.append(c);
                        }
                    }
                }
            }
            return b.append('"');
        }
    }
}
