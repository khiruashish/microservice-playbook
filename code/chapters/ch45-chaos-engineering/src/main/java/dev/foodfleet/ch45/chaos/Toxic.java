package dev.foodfleet.ch45.chaos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Toxiproxy fault: what it does ({@code type} and {@code attributes}), which direction it affects
 * ({@code downstream}: responses to the client, {@code upstream}: requests to the server) and on what share of
 * connections ({@code toxicity}, 0..1). The factories cover the faults chapter 45 uses.
 */
public record Toxic(String name, String type, String stream, double toxicity, Map<String, Long> attributes) {

    public static Toxic latency(long millis, long jitterMillis) {
        return of("latency", "latency", "latency", millis, "jitter", jitterMillis);
    }

    /** Responses trickle at {@code kbPerSecond}: headers arrive, the body takes for ever. */
    public static Toxic bandwidth(long kbPerSecond) {
        return of("bandwidth", "bandwidth", "rate", kbPerSecond);
    }

    /**
     * Requests vanish and the connection stays open: the classic hung dependency (a partition, a dead host behind a
     * load balancer, a full accept queue). Applied to requests, so the server never sees them; on the response side
     * the server's own connection closes would still reach the client and turn the hang into fast errors.
     */
    public static Toxic blackHole() {
        return of("black-hole", "timeout", "timeout", 0L).upstream();
    }

    /** No data passes; the connection is closed after {@code millis}. */
    public static Toxic dropAfter(long millis) {
        return of("drop-after", "timeout", "timeout", millis);
    }

    /** TCP reset after {@code millis}. */
    public static Toxic resetPeer(long millis) {
        return of("reset-peer", "reset_peer", "timeout", millis);
    }

    /** The connection closes after {@code bytes}: a truncated response. */
    public static Toxic limitData(long bytes) {
        return of("limit-data", "limit_data", "bytes", bytes);
    }

    /** Attributes as name, value pairs, kept in order. */
    private static Toxic of(String name, String type, Object... attributes) {
        Map<String, Long> attrs = new LinkedHashMap<>();
        for (int i = 0; i < attributes.length; i += 2) {
            attrs.put((String) attributes[i], (Long) attributes[i + 1]);
        }
        return new Toxic(name, type, "downstream", 1.0, attrs);
    }

    /** Applies to only a share of connections: the blast radius within one proxy. */
    public Toxic withToxicity(double share) {
        return new Toxic(name, type, stream, share, attributes);
    }

    public Toxic upstream() {
        return new Toxic(name, type, "upstream", toxicity, attributes);
    }

    /** The JSON body of {@code POST /proxies/{proxy}/toxics}. */
    public String json() {
        String attrs = attributes.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
        return "{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"stream\":\"" + stream
                + "\",\"toxicity\":" + toxicity + ",\"attributes\":" + attrs + "}";
    }
}
