package dev.foodfleet.ch47.correlation;

import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The W3C Trace Context {@code traceparent} header: {@code version-traceid-parentid-flags}, for example
 * {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}. The trace ID is what joins a request's
 * spans, logs, wide events and exemplars across services; the sampled flag carries the head sampling decision
 * downstream so that every service keeps or drops the same traces.
 */
public record TraceParent(String traceId, String spanId, boolean sampled) {

    private static final String INVALID_TRACE = "0".repeat(32);
    private static final String INVALID_SPAN = "0".repeat(16);

    public TraceParent {
        if (!isHex(traceId, 32) || traceId.equals(INVALID_TRACE)) {
            throw new IllegalArgumentException("trace-id must be 32 lowercase hex digits, not all zero: " + traceId);
        }
        if (!isHex(spanId, 16) || spanId.equals(INVALID_SPAN)) {
            throw new IllegalArgumentException("parent-id must be 16 lowercase hex digits, not all zero: " + spanId);
        }
    }

    /** A new root context, with random IDs. */
    public static TraceParent newRoot(boolean sampled) {
        return new TraceParent(randomHex(16), randomHex(8), sampled);
    }

    /**
     * Parses a header value. An invalid header is ignored (empty), as the specification requires: the receiver
     * starts a new trace rather than failing the request.
     */
    public static Optional<TraceParent> parse(String header) {
        if (header == null) {
            return Optional.empty();
        }
        String h = header.trim();
        if (h.length() < 55 || h.charAt(2) != '-' || h.charAt(35) != '-' || h.charAt(52) != '-') {
            return Optional.empty();
        }
        String version = h.substring(0, 2);
        if (!isHex(version, 2) || version.equals("ff") || (version.equals("00") && h.length() != 55)) {
            return Optional.empty();
        }
        String flags = h.substring(53, 55);
        if (!isHex(flags, 2)) {
            return Optional.empty();
        }
        try {
            boolean sampled = (Integer.parseInt(flags, 16) & 0x01) == 1;
            return Optional.of(new TraceParent(h.substring(3, 35), h.substring(36, 52), sampled));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The context for a call this service makes: same trace, new span, same decision. */
    public TraceParent child() {
        return new TraceParent(traceId, randomHex(8), sampled);
    }

    public String header() {
        return "00-" + traceId + "-" + spanId + (sampled ? "-01" : "-00");
    }

    private static boolean isHex(String s, int length) {
        if (s == null || s.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        do {
            ThreadLocalRandom.current().nextBytes(b);
        } while (allZero(b));
        return HexFormat.of().formatHex(b);
    }

    private static boolean allZero(byte[] b) {
        for (byte x : b) {
            if (x != 0) {
                return false;
            }
        }
        return true;
    }
}
