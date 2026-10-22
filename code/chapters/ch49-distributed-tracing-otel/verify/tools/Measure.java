import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Chapter 49's experiments, on the OpenTelemetry Java agent 2.32.0 (SDK 1.66.0), NATS 2.15.1 with jnats 2.26.4, and
 * the OpenTelemetry Collector 0.162.0.
 * <ul>
 *   <li>{@code context}: does a span started after a thread hand-off land in the request's trace? Ten ways.</li>
 *   <li>{@code messaging}: an order published on NATS, consumed by push and by pull, with and without extracting
 *       the context by hand.</li>
 *   <li>{@code sampling}: complete and broken traces when services disagree on samplers or propagators.</li>
 *   <li>{@code baggage}: what crosses each hop, and what reaches a third party, with and without a policy.</li>
 * </ul>
 */
public final class Measure {

    static final long SERVER = 2;
    static final long CLIENT = 3;
    static final long PRODUCER = 4;
    static final long CONSUMER = 5;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        switch (args[0]) {
            case "context" -> context();
            case "messaging" -> messaging();
            case "sampling" -> sampling();
            case "baggage" -> baggage();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    static List<Lab.Svc> checkout(Map<String, String> orderFf, Map<String, Map<String, String>> otel, Map<String, String> kitchenFf,
                                  Map<String, String> gatewayFf, Map<String, String> paymentFf) {
        return List.of(
                new Lab.Svc("external", Map.of(), Map.of()),
                new Lab.Svc("kitchen", otel.getOrDefault("kitchen", Map.of()), kitchenFf),
                new Lab.Svc("payment-service", otel.getOrDefault("payment-service", Map.of()), paymentFf),
                new Lab.Svc("order-service", otel.getOrDefault("order-service", Map.of()), orderFf),
                new Lab.Svc("gateway", otel.getOrDefault("gateway", Map.of()), gatewayFf));
    }

    // =========================================================================================================== context

    static void context() throws Exception {
        int orders = 200;
        try (Lab.Run run = Lab.start("context", checkout(Map.of("ff.probes", "true"), Map.of(), Map.of(), Map.of(), Map.of()))) {
            Lab.orders(run, 20, 10, Map.of());                       // warm-up
            Lab.orders(run, orders, 20, Map.of());
        }
        Map<String, int[]> byPath = new LinkedHashMap<>();
        for (String p : List.of("same thread", "ExecutorService (fixed pool)", "ScheduledExecutorService", "CompletableFuture.runAsync",
                "newVirtualThreadPerTaskExecutor()", "HttpClient.sendAsync().thenRun", "new Thread(...).start()",
                "Thread.ofVirtual().start()", "own queue + worker thread", "new Thread, Context.wrap()", "own queue, Context.wrap()")) {
            byPath.put(p, new int[2]);
        }
        List<String> lines = Lab.lines("context", "order-service");
        for (String l : lines.subList(Math.min(lines.size(), 220), lines.size())) {   // skip the warm-up's 20 x 11
            String[] f = l.split("\t");
            int[] c = byPath.computeIfAbsent(f[0], k -> new int[2]);
            c[f[1].equals("kept") ? 0 : 1]++;
        }
        System.out.printf("order-service, under the agent: for each of %d orders, a span started after handing work over eleven ways%n", orders);
        System.out.println("hand-off                                    in the request's trace    new trace (lost)");
        byPath.forEach((path, c) -> System.out.printf("%-42s %12d %20d%n", path, c[0], c[1]));
        System.out.println();
    }

    // =========================================================================================================== messaging

    static void messaging() throws Exception {
        int orders = 200;
        String[][] variants = {
                {"push: dispatcher callback", "push", "false"},
                {"pull: nextMessage() loop", "pull", "false"},
                {"pull: nextMessage(), context extracted by hand", "pull", "true"}};
        System.out.printf("order-service publishes orders.placed on NATS for each of %d orders; kitchen consumes it and starts a "
                + "'prepare order' span%n", orders);
        System.out.println("kitchen consumes by                               spans the agent made in kitchen          'prepare order' in the "
                + "order's trace   queue time p50");
        for (String[] v : variants) {
            String label = "messaging-" + v[1] + v[2];
            try (Lab.Run run = Lab.start(label, checkout(Map.of(), Map.of(),
                    Map.of("ff.consume", v[1], "ff.extract", v[2]), Map.of(), Map.of()))) {
                Lab.orders(run, 20, 10, Map.of());
                Lab.orders(run, orders, 20, Map.of());
            }
            List<Lab.Span> spans = Lab.spans(label);
            Map<String, Lab.Span> publishByTrace = new HashMap<>();
            Map<String, Lab.Span> byId = new HashMap<>();
            for (Lab.Span s : spans) {
                byId.put(s.spanId(), s);
                if (s.service().equals("order-service") && s.kind() == PRODUCER) {
                    publishByTrace.put(s.traceId(), s);
                }
            }
            Map<String, Integer> kitchenSpans = new TreeMap<>();
            int prepare = 0;
            int inTrace = 0;
            List<Double> queueMs = new ArrayList<>();
            for (Lab.Span s : spans) {
                if (!s.service().equals("kitchen") || s.name().equals("GET /health") || s.scope().contains("java-http-server")) {
                    continue;
                }
                kitchenSpans.merge(s.name() + " (" + kind(s.kind()) + ")", 1, Integer::sum);
                if (s.name().equals("prepare order")) {
                    prepare++;
                    Lab.Span publish = publishByTrace.get(s.traceId());
                    if (publish != null) {
                        inTrace++;
                        queueMs.add((s.startNs() - publish.startNs()) / 1e6);
                    }
                }
            }
            System.out.printf("%-49s %-40s %10d of %d %16s%n", v[0], kitchenSpans, inTrace, prepare,
                    queueMs.isEmpty() ? "-" : String.format("%.1f ms", percentile(queueMs, 50)));
            if (v[1].equals("push")) {
                Lab.Span process = spans.stream().filter(s -> s.service().equals("kitchen") && s.kind() == CONSUMER).findFirst().orElse(null);
                if (process != null) {
                    Lab.Span parent = byId.get(process.parent());
                    System.out.printf("   the consumer span's parent: %s%n", parent == null ? "none" : parent.service() + " '" + parent.name()
                            + "' (" + kind(parent.kind()) + ")");
                    System.out.println("   its attributes: " + new TreeMap<>(process.attrs()));
                }
            }
        }
        System.out.println();
    }

    static String kind(long k) {
        return switch ((int) k) {
            case 1 -> "internal";
            case 2 -> "server";
            case 3 -> "client";
            case 4 -> "producer";
            case 5 -> "consumer";
            default -> "?";
        };
    }

    // =========================================================================================================== sampling

    static void sampling() throws Exception {
        int orders = 1000;
        Map<String, String> ratio10 = Map.of("otel.traces.sampler", "parentbased_traceidratio", "otel.traces.sampler.arg", "0.1");
        Map<String, Map<String, Map<String, String>>> variants = new LinkedHashMap<>();
        variants.put("everyone parent-based; the gateway keeps 10%", Map.of("gateway", ratio10));
        variants.put("...payment-service: traceidratio 0.5, not parent-based", Map.of("gateway", ratio10,
                "payment-service", Map.of("otel.traces.sampler", "traceidratio", "otel.traces.sampler.arg", "0.5")));
        variants.put("...payment-service: traceidratio 0.05, not parent-based", Map.of("gateway", ratio10,
                "payment-service", Map.of("otel.traces.sampler", "traceidratio", "otel.traces.sampler.arg", "0.05")));
        variants.put("...payment-service: B3 propagation only", Map.of("gateway", ratio10,
                "payment-service", Map.of("otel.propagators", "b3multi")));
        variants.put("...payment-service: always_on, not parent-based", Map.of("gateway", ratio10,
                "payment-service", Map.of("otel.traces.sampler", "always_on")));
        variants.put("everyone parent-based, 10%; clients send traceparent ...-01", Map.of("gateway", ratio10));
        System.out.printf("%d orders: gateway -> order-service -> payment-service -> external partner; order-service -> NATS -> kitchen%n", orders);
        System.out.println("a complete trace has the spans of gateway, order-service, payment-service and kitchen; a fragment is a trace "
                + "with no gateway span");
        System.out.println("configuration                                                  complete   gateway traces missing a service     "
                + "fragments   spans stored");
        int i = 0;
        for (Map.Entry<String, Map<String, Map<String, String>>> v : variants.entrySet()) {
            String label = "sampling-" + i++;
            try (Lab.Run run = Lab.start(label, checkout(Map.of(), v.getValue(), Map.of(), Map.of(), Map.of()))) {
                Lab.orders(run, orders, 50, v.getKey().contains("clients send")
                        ? Map.of("traceparent", "RANDOM_SAMPLED_TRACEPARENT") : Map.of());
            }
            List<Lab.Span> spans = Lab.spans(label).stream().filter(s -> !s.name().startsWith("GET")).toList();
            Map<String, Set<String>> servicesByTrace = new HashMap<>();
            Set<String> rooted = new HashSet<>();
            for (Lab.Span s : spans) {
                servicesByTrace.computeIfAbsent(s.traceId(), k -> new HashSet<>()).add(s.service());
                if (s.service().equals("gateway") && s.kind() == SERVER) {
                    rooted.add(s.traceId());
                }
            }
            int complete = 0;
            int partial = 0;
            Map<String, Integer> missing = new TreeMap<>();
            for (String t : rooted) {
                Set<String> svc = servicesByTrace.get(t);
                if (svc.containsAll(List.of("gateway", "order-service", "payment-service", "kitchen"))) {
                    complete++;
                } else {
                    partial++;
                    for (String s : List.of("order-service", "payment-service", "kitchen")) {
                        if (!svc.contains(s)) {
                            missing.merge(s, 1, Integer::sum);
                        }
                    }
                }
            }
            long fragments = servicesByTrace.keySet().stream().filter(t -> !rooted.contains(t)).count();
            System.out.printf("%-62s %11d %-36s %9d %13d%n", v.getKey(), complete,
                    partial + (missing.isEmpty() ? "" : " " + missing), fragments, spans.size());
        }
        System.out.println();
    }

    // =========================================================================================================== baggage

    static void baggage() throws Exception {
        int orders = 50;
        String sent = "tenant=chain-7,city.tier=metro,user.email=priya%40example.com,session=eyJhbGciOiJIUzI1NiJ9.demo,"
                + "debug.note=" + "x".repeat(300);
        Map<String, String> capture = Map.of("otel.instrumentation.http.server.capture-request-headers", "baggage");
        Map<String, Map<String, String>> otel = Map.of("order-service", capture, "payment-service", capture, "gateway", capture);
        String[][] variants = {
                {"no policy", "false", "false"},
                {"sanitized at the gateway (allow-list, 512 bytes)", "true", "false"},
                {"sanitized, and stripped before the partner call", "true", "true"}};
        System.out.printf("%d orders; the client sends baggage: %s... (%d bytes)%n", orders, sent.substring(0, 60), sent.length());
        System.out.println("policy                                              at order-service              at payment-service            "
                + "at the external partner          tenant on payment's span");
        for (String[] v : variants) {
            String label = "baggage-" + v[1] + v[2];
            try (Lab.Run run = Lab.start(label, checkout(Map.of(), otel, Map.of(),
                    Map.of("ff.sanitize", v[1]), Map.of("ff.strip", v[2])))) {
                Lab.orders(run, orders, 10, Map.of("baggage", sent));
            }
            List<Lab.Span> spans = Lab.spans(label);
            String atOrder = header(spans, "order-service");
            String atPayment = header(spans, "payment-service");
            List<String> partner = Lab.lines(label, "external");
            String atPartner = partner.isEmpty() ? "-" : partner.get(partner.size() - 1).split("\t")[0];
            long withTraceparent = partner.stream().filter(l -> !l.endsWith("\t-")).count();
            long tenantOnSpan = spans.stream().filter(s -> s.name().equals("fraud check") && "chain-7".equals(s.attrs().get("foodfleet.tenant"))).count();
            long fraudSpans = spans.stream().filter(s -> s.name().equals("fraud check")).count();
            System.out.printf("%-51s %-29s %-29s %-32s %d of %d%n", v[0], describe(atOrder), describe(atPayment), describe(atPartner),
                    tenantOnSpan, fraudSpans);
            System.out.printf("   partner received traceparent on %d of %d calls; keys it saw: %s%n", withTraceparent, partner.size(),
                    keys(atPartner));
        }
        System.out.println();
    }

    static String header(List<Lab.Span> spans, String service) {
        for (Lab.Span s : spans) {
            if (s.service().equals(service) && s.kind() == SERVER && !s.name().startsWith("GET")) {
                return s.attrs().getOrDefault("http.request.header.baggage", "-");
            }
        }
        return "-";
    }

    static String describe(String header) {
        if (header == null || header.equals("-")) {
            return "none";
        }
        return keys(header).size() + " keys, " + header.length() + " bytes";
    }

    static List<String> keys(String header) {
        if (header == null || header.equals("-")) {
            return List.of();
        }
        List<String> k = new ArrayList<>();
        for (String e : header.split(",")) {
            k.add(e.split("=")[0].trim());
        }
        Collections.sort(k);
        return k;
    }

    static double percentile(List<Double> values, double p) {
        List<Double> s = values.stream().sorted().collect(Collectors.toList());
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }
}
