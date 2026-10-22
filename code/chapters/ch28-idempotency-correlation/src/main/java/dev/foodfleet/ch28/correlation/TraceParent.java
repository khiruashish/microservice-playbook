package dev.foodfleet.ch28.correlation;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The W3C Trace Context {@code traceparent} header: {@code version-traceid-parentid-flags}, e.g.
 * {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}. The trace id is the
 * correlation id of a whole request tree; the parent id names the span that made this call.
 * Spring Boot's Micrometer Tracing reads and writes it for you; this class shows what's in it.
 */
public record TraceParent(String traceId, String parentId, boolean sampled) {

    private static final Pattern FORMAT = Pattern.compile("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Parses a header value; null if it is not a valid version-00 traceparent. */
    public static TraceParent parse(String header) {
        if (header == null) {
            return null;
        }
        var m = FORMAT.matcher(header.trim());
        if (!m.matches() || m.group(1).equals("ff")
                || m.group(2).equals("0".repeat(32)) || m.group(3).equals("0".repeat(16))) {
            return null;
        }
        int flags = Integer.parseInt(m.group(4), 16);
        return new TraceParent(m.group(2), m.group(3), (flags & 1) == 1);
    }

    /** A new trace, for a request that arrived without one. */
    public static TraceParent newTrace(boolean sampled) {
        return new TraceParent(randomHex(16), randomHex(8), sampled);
    }

    /** The header to send downstream: same trace, this span as the new parent. */
    public TraceParent child() {
        return new TraceParent(traceId, randomHex(8), sampled);
    }

    public String header() {
        return "00-" + traceId + "-" + parentId + "-" + (sampled ? "01" : "00");
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
