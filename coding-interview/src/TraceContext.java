import java.util.*;
import java.util.random.RandomGenerator;

/** Problem: propagate a W3C traceparent across service calls: parse, validate, create child spans, record a trace. */
public class TraceContext {

    /** traceparent: version-traceid-parentid-flags, e.g. 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01 */
    record SpanContext(String traceId, String spanId, boolean sampled) {
        private static final String ZERO_TRACE = "0".repeat(32), ZERO_SPAN = "0".repeat(16);

        static Optional<SpanContext> parse(String header) {
            if (header == null) return Optional.empty();
            String[] p = header.trim().toLowerCase(Locale.ROOT).split("-");
            if (p.length != 4 || !p[0].equals("00")) return Optional.empty();
            if (!p[1].matches("[0-9a-f]{32}") || p[1].equals(ZERO_TRACE)) return Optional.empty();
            if (!p[2].matches("[0-9a-f]{16}") || p[2].equals(ZERO_SPAN)) return Optional.empty();
            if (!p[3].matches("[0-9a-f]{2}")) return Optional.empty();
            return Optional.of(new SpanContext(p[1], p[2], (Integer.parseInt(p[3], 16) & 1) == 1));
        }

        String header() { return "00-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00"); }
    }

    record Span(String traceId, String spanId, String parentId, String service, String name) { }

    static final class Tracer {
        private final RandomGenerator random;
        final List<Span> recorded = new ArrayList<>();
        Tracer(RandomGenerator random) { this.random = random; }

        String hex(int bytes) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes; i++) sb.append(String.format("%02x", random.nextInt(256)));
            return sb.toString();
        }

        /** Server side: continue the incoming trace, or start a new one if the header is missing/invalid. */
        SpanContext startServerSpan(String service, String name, String incomingHeader) {
            Optional<SpanContext> parent = SpanContext.parse(incomingHeader);
            String traceId = parent.map(SpanContext::traceId).orElseGet(() -> hex(16));
            SpanContext ctx = new SpanContext(traceId, hex(8), parent.map(SpanContext::sampled).orElse(true));
            if (ctx.sampled()) recorded.add(new Span(traceId, ctx.spanId(), parent.map(SpanContext::spanId).orElse(null), service, name));
            return ctx;
        }
    }

    /** Three services: gateway -> orders -> payments. Each passes its OWN span id as the next parent. */
    public static void main(String[] args) {
        Tracer tracer = new Tracer(new Random(1));

        SpanContext gateway = tracer.startServerSpan("gateway", "GET /checkout", null);
        SpanContext orders = tracer.startServerSpan("orders", "POST /orders", gateway.header());
        SpanContext payments = tracer.startServerSpan("payments", "POST /charge", orders.header());
        SpanContext fromBadClient = tracer.startServerSpan("orders", "GET /orders", "00-zzzz-bad-01");

        System.out.println("gateway  -> orders   traceparent: " + gateway.header());
        System.out.println("orders   -> payments traceparent: " + orders.header());
        for (Span s : tracer.recorded) {
            System.out.printf("%-9s %-14s trace=%s.. span=%s parent=%s%n", s.service(), s.name(), s.traceId().substring(0, 8), s.spanId(), s.parentId());
        }

        check(gateway.traceId().equals(orders.traceId()) && orders.traceId().equals(payments.traceId()), "one trace id end to end");
        check(tracer.recorded.get(2).parentId().equals(orders.spanId()), "parent is the caller's span");
        check(!fromBadClient.traceId().equals(gateway.traceId()), "invalid header -> new trace");
        check(SpanContext.parse("00-" + "0".repeat(32) + "-00f067aa0ba902b7-01").isEmpty(), "all-zero trace id is invalid");
        check(SpanContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00").map(c -> !c.sampled()).orElse(false), "sampled flag");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
