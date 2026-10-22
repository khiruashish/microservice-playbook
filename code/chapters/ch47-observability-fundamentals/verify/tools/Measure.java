import dev.foodfleet.ch47.analysis.BubbleUp;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Chapter 47's experiments.
 * <ul>
 *   <li>{@code find}: a gray failure (payment-service 1.5, UPI through bank-x, +600 ms) shipped as a canary, seen through
 *       metrics in Prometheus, a deploy event, logs, traces sampled four ways and wide events; then what each signal
 *       costs per request.</li>
 *   <li>{@code overhead}: latency, CPU, memory and startup with no instrumentation, the OpenTelemetry Java agent at
 *       several settings, and JDK Flight Recorder.</li>
 *   <li>{@code profile}: a CPU hot spot that traces show as time and a continuous profile shows as code.</li>
 *   <li>{@code cardinality}: Prometheus's memory, scrape and query times as one label's values grow.</li>
 * </ul>
 */
public final class Measure {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        switch (args[0]) {
            case "find" -> find();
            case "overhead" -> overhead();
            case "profile" -> profile();
            case "cardinality" -> cardinality();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    // =========================================================================================================== find

    static final int RATE = 100;
    static final Duration WARMUP = Duration.ofSeconds(20);
    static final Duration RUN = Duration.ofSeconds(80);
    static final long DEPLOY_AFTER_MS = 25_000;

    static String findCollector(int otlp, int prom, int promAll) {
        Path d = Lab.DIR;
        return """
                receivers:
                  otlp:
                    protocols:
                      http:
                        endpoint: 127.0.0.1:%d
                processors:
                  batch:
                    timeout: 1s
                  probabilistic_sampler/head1:
                    sampling_percentage: 1
                  probabilistic_sampler/head10:
                    sampling_percentage: 10
                  tail_sampling:
                    decision_wait: 5s
                    policies:
                      - {name: errors, type: status_code, status_code: {status_codes: [ERROR]}}
                      - {name: slow, type: latency, latency: {threshold_ms: 300}}
                      - {name: baseline, type: probabilistic, probabilistic: {sampling_percentage: 1}}
                exporters:
                  file/all: {path: %s/traces-all.json}
                  file/allproto: {path: %s/traces-all.pb, format: proto}
                  file/head1: {path: %s/traces-head1.json}
                  file/head10: {path: %s/traces-head10.json}
                  file/tail: {path: %s/traces-tail.json}
                  file/logs: {path: %s/logs.json}
                  file/logsproto: {path: %s/logs.pb, format: proto}
                  file/metricsproto: {path: %s/metrics.pb, format: proto}
                  prometheus:
                    endpoint: 127.0.0.1:%d
                    resource_constant_labels: {included: [service.version]}
                  prometheus/allresource:
                    endpoint: 127.0.0.1:%d
                    resource_to_telemetry_conversion: {enabled: true}
                service:
                  telemetry:
                    metrics: {level: none}
                  pipelines:
                    traces/all: {receivers: [otlp], processors: [batch], exporters: [file/all, file/allproto]}
                    traces/head1: {receivers: [otlp], processors: [probabilistic_sampler/head1, batch], exporters: [file/head1]}
                    traces/head10: {receivers: [otlp], processors: [probabilistic_sampler/head10, batch], exporters: [file/head10]}
                    traces/tail: {receivers: [otlp], processors: [tail_sampling, batch], exporters: [file/tail]}
                    logs: {receivers: [otlp], processors: [batch], exporters: [file/logs, file/logsproto]}
                    metrics: {receivers: [otlp], processors: [batch], exporters: [prometheus, prometheus/allresource, file/metricsproto]}
                """.formatted(otlp, d, d, d, d, d, d, d, d, prom, promAll);
    }

    static void find() throws Exception {
        Lab.reset();
        int otlp = Lab.freePort();
        int promExp = Lab.freePort();
        int promExpAll = Lab.freePort();
        Lab.PROM_PORT = Lab.freePort();
        Lab.Proc collector = Lab.collector(findCollector(otlp, promExp, promExpAll));
        Lab.waitForPort(otlp, Duration.ofSeconds(30));
        Lab.Proc prometheus = Lab.prometheus("prometheus", "127.0.0.1:" + promExp, "5s");
        Lab.waitPrometheusReady();

        int gw = Lab.freePort();
        int order = Lab.freePort();
        int payA = Lab.freePort();
        int payB = Lab.freePort();
        Path instances = Lab.DIR.resolve("payment-instances.txt");
        Files.writeString(instances, "payment-a http://127.0.0.1:" + payA + "\n");
        Lab.Observability agent = Lab.Observability.agent(null);
        List<Lab.Proc> services = new ArrayList<>();
        Map<String, String> paymentA = new HashMap<>(events("payment-a"));
        paymentA.put("payments.db.password", SECRET_PASSWORD);        // invented values, to see where they end up
        paymentA.put("payments.gateway.token", SECRET_TOKEN);
        services.add(Lab.service("payment-service", "1.4", "payment-a", payA, "", otlp, agent, paymentA));
        services.add(Lab.service("payment-service", "1.5", "payment-b", payB, "", otlp, agent, events("payment-b")));
        services.add(Lab.service("order-service", "1.4", "order-a", order, "file:" + instances, otlp, agent, events("order-a")));
        services.add(Lab.service("gateway", "1.4", "gateway-a", gw, "http://127.0.0.1:" + order, otlp, agent, Map.of()));
        for (int p : List.of(payA, payB, order, gw)) {
            Lab.waitHealthy(p, Duration.ofSeconds(90));
        }
        String url = "http://127.0.0.1:" + gw + "/orders";
        List<Lab.Result> warm = Lab.load(url, RATE, WARMUP);

        long t0 = System.currentTimeMillis();
        AtomicReference<Long> deployAt = new AtomicReference<>();
        Thread deployer = Thread.ofPlatform().start(() -> {
            try {
                Thread.sleep(DEPLOY_AFTER_MS);
                Files.writeString(instances, "payment-b http://127.0.0.1:" + payB + "\n", StandardOpenOption.APPEND);
                deployAt.set(System.currentTimeMillis());
                // the change event, as a deploy pipeline would send it
                Files.writeString(Lab.DIR.resolve("events-deploys.jsonl"), "{\"timestamp_ms\":" + deployAt.get()
                        + ",\"type\":\"deployment\",\"service\":\"payment-service\",\"version\":\"1.5\",\"pod\":\"payment-b\",\"strategy\":\"canary 50%\"}\n");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        List<Lab.Result> results = Lab.load(url, RATE, RUN);
        deployer.join();
        long end = System.currentTimeMillis();
        Thread.sleep(2000);
        for (Lab.Proc s : services) {
            s.stop();
        }
        Thread.sleep(8000);                                   // tail sampling's decision_wait, then a batch
        String promBody = Lab.getBody("http://127.0.0.1:" + promExp + "/metrics");
        String promAllBody = Lab.getBody("http://127.0.0.1:" + promExpAll + "/metrics");

        long deploy = deployAt.get();
        System.out.printf("payment-service 1.4 on payment-a; at %.0f s, 1.5 joins as a canary (payment-b, half the payments).%n",
                (deploy - t0) / 1000.0);
        System.out.printf("%d orders/s for %d s after a %d s warm-up; 1.5 adds 600 ms to UPI payments through bank-x.%n%n",
                RATE, RUN.toSeconds(), WARMUP.toSeconds());

        monitoring(results, t0, end, deploy);
        logs(deploy, end);
        traces(t0, end, deploy);
        wideEvents(deploy, end);
        cost(warm.size() + results.size(), WARMUP.plus(RUN).toMillis() + 10_000, promBody, promAllBody);
        prometheus.stop();
        collector.stop();
    }

    static final String SECRET_PASSWORD = "demo-password-5678";
    static final String SECRET_TOKEN = "demo-token-1234";

    static Map<String, String> events(String pod) {
        return Map.of("ff.events", Lab.DIR.resolve("events-" + pod + ".jsonl").toString());
    }

    /** What a dashboard shows: Prometheus queries over the agent's http.server.request.duration histograms. */
    static void monitoring(List<Lab.Result> results, long t0, long end, long deploy) throws Exception {
        long step = 10;
        long from = t0 / 1000 + step;
        long to = end / 1000;
        String hist = "http_server_request_duration_seconds_bucket";
        Map<Long, Double> orderP50 = series(Lab.promql("histogram_quantile(0.5, sum by (le) (rate(" + hist
                + "{job=\"order-service\",http_route=\"/orders\"}[10s])))", from, to, step + "s"), null);
        Map<Long, Double> orderP99 = series(Lab.promql("histogram_quantile(0.99, sum by (le) (rate(" + hist
                + "{job=\"order-service\",http_route=\"/orders\"}[10s])))", from, to, step + "s"), null);
        List<Object> byPod = Lab.promql("histogram_quantile(0.99, sum by (le, instance) (rate(" + hist
                + "{job=\"payment-service\",http_route=\"/payments\"}[10s])))", from, to, step + "s");
        Map<Long, Double> payA = series(byPod, "payment-a");
        Map<Long, Double> payB = series(byPod, "payment-b");
        Map<Long, Double> errors = series(Lab.promql("sum(rate(http_server_request_duration_seconds_count{job=\"gateway\","
                + "http_response_status_code=~\"5..\"}[10s])) or vector(0)", from, to, step + "s"), null);
        Map<Long, Double> rps = series(Lab.promql("sum(rate(http_server_request_duration_seconds_count{job=\"gateway\","
                + "http_route=\"/orders\"}[10s]))", from, to, step + "s"), null);

        System.out.println("-- metrics: what the dashboard showed (Prometheus, 10 s windows; p99 interpolated in the agent's buckets)");
        System.out.println("window (s after deploy)   orders/s   5xx/s   order p50   order p99   users p99   payment p99: payment-a   payment-b");
        for (long t = from; t <= to; t += step) {
            long winStart = (t - step) * 1000;
            long winEnd = t * 1000;
            List<Double> lat = new ArrayList<>();
            for (Lab.Result r : results) {
                if (r.sentMillis() >= winStart && r.sentMillis() < winEnd) {
                    lat.add(r.latencyMicros() / 1000.0);
                }
            }
            System.out.printf("%-24s %9s %7s %11s %11s %11s %22s %11s%n",
                    String.format("%+.0f .. %+.0f", (winStart - deploy) / 1000.0, (winEnd - deploy) / 1000.0),
                    fmt(rps.get(t), "%.0f", 1), fmt(errors.get(t), "%.1f", 1),
                    fmt(orderP50.get(t), "%.0f ms", 1000), fmt(orderP99.get(t), "%.0f ms", 1000),
                    String.format("%.0f ms", Lab.percentile(lat, 99)),
                    fmt(payA.get(t), "%.0f ms", 1000), fmt(payB.get(t), "%.0f ms", 1000));
        }
        Path deploys = Lab.DIR.resolve("events-deploys.jsonl");
        System.out.println("-- events: " + Files.readString(deploys).trim());
        System.out.println();
    }

    static Map<Long, Double> series(List<Object> result, String instance) {
        Map<Long, Double> out = new TreeMap<>();
        for (Object r : result) {
            if (instance != null && !instance.equals(Json.at(r, "metric", "instance"))) {
                continue;
            }
            for (Object v : Json.arr(Json.at(r, "values"))) {
                List<Object> pair = Json.arr(v);
                long ts = ((Number) pair.get(0)).longValue();
                String val = (String) pair.get(1);
                if (!val.equals("NaN")) {
                    out.put(ts, Double.parseDouble(val));
                }
            }
        }
        return out;
    }

    static String fmt(Double v, String f, double scale) {
        return v == null ? "-" : String.format(f, v * scale);
    }

    /** What the logs said while it happened. */
    static void logs(long deploy, long end) throws IOException {
        Map<String, Integer> bySeverity = new TreeMap<>();
        int mention = 0;
        int withTrace = 0;
        int total = 0;
        String sample = null;
        for (String line : lines(Lab.DIR.resolve("logs.json"))) {
            for (Object rl : Json.arr(Json.at(Json.parse(line), "resourceLogs"))) {
                for (Object sl : Json.arr(Json.at(rl, "scopeLogs"))) {
                    for (Object rec : Json.arr(Json.at(sl, "logRecords"))) {
                        long ms = Long.parseLong((String) Json.at(rec, "timeUnixNano")) / 1_000_000;
                        if (ms < deploy || ms > end) {
                            continue;
                        }
                        total++;
                        bySeverity.merge(String.valueOf(Json.at(rec, "severityText")), 1, Integer::sum);
                        String body = String.valueOf(Json.at(rec, "body", "stringValue"));
                        if (body.matches("(?i).*(slow|timeout|fraud|latency|error|fail|warn).*")) {
                            mention++;
                        }
                        if (Json.at(rec, "traceId") != null) {
                            withTrace++;
                        }
                        if (sample == null && body.startsWith("Authorised")) {
                            sample = body;
                        }
                    }
                }
            }
        }
        System.out.println("-- logs after the deploy (java.util.logging through the agent)");
        System.out.printf("records %d, by severity %s; mentioning slow/timeout/fraud/latency/error/fail/warn: %d; with a trace ID: %d%n",
                total, bySeverity, mention, withTrace);
        System.out.println("a payment's line: \"" + sample + "\"");
        System.out.println();
    }

    /** One span from the collector's OTLP/JSON files. */
    record Span(String traceId, String spanId, String parent, String name, long kind, long startNs, long endNs,
                String service, String version, String pod, Map<String, String> attrs) {
        double ms() {
            return (endNs - startNs) / 1e6;
        }
    }

    static List<Span> spans(Path file) throws IOException {
        List<Span> out = new ArrayList<>();
        for (String line : lines(file)) {
            for (Object rs : Json.arr(Json.at(Json.parse(line), "resourceSpans"))) {
                Map<String, String> res = attributes(Json.at(rs, "resource", "attributes"));
                for (Object ss : Json.arr(Json.at(rs, "scopeSpans"))) {
                    for (Object sp : Json.arr(Json.at(ss, "spans"))) {
                        out.add(new Span((String) Json.at(sp, "traceId"), (String) Json.at(sp, "spanId"),
                                (String) Json.at(sp, "parentSpanId"), (String) Json.at(sp, "name"),
                                ((Number) Json.at(sp, "kind")).longValue(),
                                Long.parseLong((String) Json.at(sp, "startTimeUnixNano")),
                                Long.parseLong((String) Json.at(sp, "endTimeUnixNano")),
                                res.get("service.name"), res.get("service.version"), res.get("service.instance.id"),
                                attributes(Json.at(sp, "attributes"))));
                    }
                }
            }
        }
        return out;
    }

    static Map<String, String> attributes(Object list) {
        Map<String, String> m = new HashMap<>();
        for (Object a : Json.arr(list)) {
            Object v = Json.at(a, "value");
            Object s = Json.at(v, "stringValue");
            if (s == null && Json.at(v, "arrayValue") != null) {   // a header's values, a command line's arguments
                List<Object> vals = Json.arr(Json.at(v, "arrayValue", "values"));
                s = vals.isEmpty() ? null : vals.stream().map(x -> String.valueOf(Json.at(x, "stringValue")))
                        .collect(Collectors.joining(" "));
            }
            if (s == null) {
                Object i = Json.at(v, "intValue");
                s = i == null ? null : String.valueOf(i);
            }
            if (s != null) {
                m.put((String) Json.at(a, "key"), String.valueOf(s));
            }
        }
        return m;
    }

    static final long SERVER = 2;

    static void traces(long t0, long end, long deploy) throws IOException {
        Map<String, List<Span>> files = new LinkedHashMap<>();
        files.put("every trace (100%)", spans(Lab.DIR.resolve("traces-all.json")));
        files.put("head sampling 10%", spans(Lab.DIR.resolve("traces-head10.json")));
        files.put("head sampling 1%", spans(Lab.DIR.resolve("traces-head1.json")));
        files.put("tail: errors + slow > 300 ms + 1%", spans(Lab.DIR.resolve("traces-tail.json")));
        Map<String, Path> paths = Map.of("every trace (100%)", Lab.DIR.resolve("traces-all.json"),
                "head sampling 10%", Lab.DIR.resolve("traces-head10.json"),
                "head sampling 1%", Lab.DIR.resolve("traces-head1.json"),
                "tail: errors + slow > 300 ms + 1%", Lab.DIR.resolve("traces-tail.json"));
        System.out.println("-- traces: the same traffic kept four ways (sampling in the collector; trace roots = gateway requests in the run)");
        System.out.println("kept by                              traces    slow (> 300 ms)    of all slow   slow span found in      stored");
        long allBytes = Lab.bytes(paths.get("every trace (100%)"));
        int allSlow = 0;
        for (Map.Entry<String, List<Span>> f : files.entrySet()) {
            Map<String, List<Span>> byTrace = f.getValue().stream().collect(Collectors.groupingBy(Span::traceId));
            int traces = 0;
            int slow = 0;
            Map<String, Integer> where = new TreeMap<>();
            for (List<Span> trace : byTrace.values()) {
                Span root = trace.stream().filter(s -> s.service().equals("gateway") && s.kind() == SERVER
                        && s.name().startsWith("POST")).findFirst().orElse(null);
                if (root == null || root.startNs() / 1_000_000 < t0 || root.startNs() / 1_000_000 > end) {
                    continue;
                }
                traces++;
                if (root.ms() > 300) {
                    slow++;
                    // the deepest server span that is itself slow: where the time went
                    Span deepest = trace.stream().filter(s -> s.kind() == SERVER && s.ms() > 300)
                            .min((a, b) -> Double.compare(a.ms(), b.ms())).orElse(root);
                    where.merge(deepest.service() + " " + deepest.version(), 1, Integer::sum);
                }
            }
            if (f.getKey().startsWith("every")) {
                allSlow = slow;
            }
            System.out.printf("%-34s %9d %18d %14s   %-20s %10s%n", f.getKey(), traces, slow,
                    String.format("%.0f%%", 100.0 * slow / Math.max(1, allSlow)), where.isEmpty() ? "-" : where.toString(),
                    String.format("%.1f%%", 100.0 * Lab.bytes(paths.get(f.getKey())) / allBytes));
        }
        System.out.println();
        System.out.println("-- what differs about the slow payments? (payment-service server spans after the deploy, BubbleUp)");
        for (Map.Entry<String, List<Span>> f : files.entrySet()) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (Span s : f.getValue()) {
                if (s.service().equals("payment-service") && s.kind() == SERVER && s.name().startsWith("POST")
                        && s.startNs() / 1_000_000 >= deploy && s.startNs() / 1_000_000 <= end) {
                    Map<String, String> row = new HashMap<>();
                    row.put("service.version", s.version());
                    row.put("pod", s.pod());
                    putIfPresent(row, "tenant", s.attrs().get("http.request.header.x-tenant"));
                    putIfPresent(row, "payment.method", s.attrs().get("http.request.header.x-payment-method"));
                    putIfPresent(row, "payment.bank", s.attrs().get("http.request.header.x-bank"));
                    row.put("duration_ms", String.valueOf(s.ms()));
                    rows.add(row);
                }
            }
            bubbleUp(f.getKey(), rows, "spans");
        }
        System.out.println();
    }

    static void putIfPresent(Map<String, String> m, String k, String v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    static final BubbleUp BUBBLE_UP = new BubbleUp(Set.of("duration_ms", "timestamp", "trace_id", "order.id",
            "outcome", "operation", "service"));

    static void bubbleUp(String label, List<Map<String, String>> rows, String unit) {
        Predicate<Map<String, String>> slow = r -> Double.parseDouble(r.get("duration_ms")) > 300;
        long selected = rows.stream().filter(slow).count();
        List<BubbleUp.Difference> top = BUBBLE_UP.rank(rows, slow);
        BubbleUp.Isolation iso = BUBBLE_UP.isolate(rows, slow, 4, 0.9);
        String diffs = top.stream().limit(3).map(d -> String.format("%s=%s %.0f%% vs %.0f%%", d.field(), d.value(),
                100 * d.inSelection(), 100 * d.inBaseline())).collect(Collectors.joining("; "));
        System.out.printf("%-34s %6d %s, %4d slow | top: %s%n", label, rows.size(), unit, selected, selected == 0 ? "-" : diffs);
        System.out.printf("%-34s filter %s matches %d: precision %.0f%%, recall %.0f%%%n", "", iso.filter(), iso.matched(),
                100 * iso.precision(), 100 * iso.recall());
    }

    /** The same question asked of the services' own wide events. */
    static void wideEvents(long deploy, long end) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String pod : List.of("payment-a", "payment-b")) {
            for (String line : lines(Lab.DIR.resolve("events-" + pod + ".jsonl"))) {
                Map<String, Object> e = Json.obj(Json.parse(line));
                long ms = java.time.Instant.parse((String) e.get("timestamp")).toEpochMilli();
                if (ms < deploy || ms > end) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                e.forEach((k, v) -> row.put(k, String.valueOf(v)));
                rows.add(row);
            }
        }
        System.out.println("-- wide events (one per request, written by payment-service itself, unsampled)");
        bubbleUp("wide events", rows, "events");
        System.out.println();
    }

    /** What each signal costs, per request and per hundred million requests. */
    static void cost(int requests, long durationMs, String promBody, String promAllBody) throws Exception {
        long spansProto = Lab.bytes(Lab.DIR.resolve("traces-all.pb"));
        long logsProto = Lab.bytes(Lab.DIR.resolve("logs.pb"));
        long metricsProto = Lab.bytes(Lab.DIR.resolve("metrics.pb"));
        long events = 0;
        long eventCount = 0;
        for (String pod : List.of("payment-a", "payment-b", "order-a")) {
            Path p = Lab.DIR.resolve("events-" + pod + ".jsonl");
            events += Lab.bytes(p);
            eventCount += lines(p).size();
        }
        long spanCount = spans(Lab.DIR.resolve("traces-all.json")).size();
        long logCount = 0;
        for (String line : lines(Lab.DIR.resolve("logs.json"))) {
            for (Object rl : Json.arr(Json.at(Json.parse(line), "resourceLogs"))) {
                for (Object sl : Json.arr(Json.at(rl, "scopeLogs"))) {
                    logCount += Json.arr(Json.at(sl, "logRecords")).size();
                }
            }
        }
        List<Object> series = Lab.promql("count({__name__=~\"http_server.*\"})", null, null, null);
        String seriesCount = series.isEmpty() ? "0" : (String) Json.arr(Json.at(series.get(0), "value")).get(1);
        double minutes = durationMs / 60_000.0;
        System.out.printf("-- cost: %d requests in %.1f minutes (OTLP protobuf, as the agent sends it; wide events as JSON lines)%n",
                requests, minutes);
        System.out.println("signal                                          records/request   bytes/request   GB per 100 million requests");
        row("traces, every span (3 services)", (double) spanCount / requests, (double) spansProto / requests);
        row("logs, one text line per service", (double) logCount / requests, (double) logsProto / requests);
        row("wide events, order + payment", (double) eventCount / requests, (double) events / requests);
        System.out.printf("%-47s %16s %15s   %s%n", "metrics (agent's default instruments)", "-",
                String.format("%.0f/min", metricsProto / minutes), "independent of traffic");
        Map<String, String> resource = Map.of();
        for (String line : lines(Lab.DIR.resolve("traces-all.json"))) {         // payment-a's resource
            for (Object rs : Json.arr(Json.at(Json.parse(line), "resourceSpans"))) {
                Map<String, String> r = attributes(Json.at(rs, "resource", "attributes"));
                if ("payment-a".equals(r.get("service.instance.id"))) {
                    resource = r;
                }
            }
            if (!resource.isEmpty()) {
                break;
            }
        }
        int resourceChars = resource.entrySet().stream().mapToInt(e -> e.getKey().length() + e.getValue().length()).sum();
        System.out.printf("payment-a's resource, sent with every batch: %d attributes, %d characters of keys and values (process.command_args alone: %d)%n",
                resource.size(), resourceChars, resource.getOrDefault("process.command_args", "").length());
        String spansText = Files.readString(Lab.DIR.resolve("traces-all.json"));
        System.out.printf("payment-a ran with -Dpayments.db.password=... and -Dpayments.gateway.token=...: in exported spans the password %s, "
                        + "the token %s; in Prometheus labels (all resource attributes) the token %s%n",
                spansText.contains(SECRET_PASSWORD) ? "appears" : "is masked",
                spansText.contains(SECRET_TOKEN) ? "appears" : "does not appear",
                promAllBody.contains(SECRET_TOKEN) ? "appears" : "does not appear");
        System.out.printf("Prometheus: %s http_server* series from 4 pods; a scrape of the collector: %,d bytes with service.version "
                        + "as the only resource label, %,d bytes with every resource attribute as a label%n",
                seriesCount, promBody.length(), promAllBody.length());
        System.out.println();
    }

    static void row(String name, double perRequest, double bytes) {
        System.out.printf("%-47s %16.1f %15.0f   %.1f%n", name, perRequest, bytes, bytes * 1e8 / 1e9);
    }

    static List<String> lines(Path p) throws IOException {
        if (!Files.exists(p)) {
            return List.of();
        }
        try (BufferedReader r = Files.newBufferedReader(p)) {
            return r.lines().filter(l -> !l.isBlank()).toList();
        }
    }

    // =========================================================================================================== overhead

    record Variant(String name, Lab.Observability obs) {
    }

    static void overhead() throws Exception {
        List<Variant> variants = List.of(
                new Variant("no instrumentation", Lab.Observability.none()),
                new Variant("agent, every trace exported", Lab.Observability.agent("always_on")),
                new Variant("agent, 10% of traces exported", Lab.Observability.agent("parentbased_traceidratio:0.1")),
                new Variant("agent, nothing exported", new Lab.Observability(true, null, true, null)),
                new Variant("JFR, default settings", new Lab.Observability(false, null, false, "default")),
                new Variant("JFR, profile settings", new Lab.Observability(false, null, false, "profile")));
        int rate = 150;
        System.out.printf("gateway -> order -> payment, %d orders/s: 15 s warm-up, then 30 s measured; each JVM -Xmx256m, SerialGC%n", rate);
        System.out.println("setup                               p50        p99   CPU per 1,000 orders (services / collector)   memory (3 JVMs)   startup");
        for (Variant v : variants) {
            Lab.reset();
            int otlp = Lab.freePort();
            Lab.Proc collector = Lab.collector("""
                    receivers:
                      otlp:
                        protocols:
                          http:
                            endpoint: 127.0.0.1:%d
                    processors:
                      batch: {}
                    exporters:
                      nop: {}
                    service:
                      telemetry:
                        metrics: {level: none}
                      pipelines:
                        traces: {receivers: [otlp], processors: [batch], exporters: [nop]}
                        metrics: {receivers: [otlp], processors: [batch], exporters: [nop]}
                        logs: {receivers: [otlp], processors: [batch], exporters: [nop]}
                    """.formatted(otlp));
            Lab.waitForPort(otlp, Duration.ofSeconds(30));
            int gw = Lab.freePort();
            int order = Lab.freePort();
            int pay = Lab.freePort();
            Path instances = Lab.DIR.resolve("payment-instances.txt");
            Files.writeString(instances, "payment-a http://127.0.0.1:" + pay + "\n");
            long started = System.nanoTime();
            List<Lab.Proc> procs = List.of(
                    Lab.service("payment-service", "1.4", "payment-a", pay, "", otlp, v.obs(), Map.of()),
                    Lab.service("order-service", "1.4", "order-a", order, "file:" + instances, otlp, v.obs(), Map.of()),
                    Lab.service("gateway", "1.4", "gateway-a", gw, "http://127.0.0.1:" + order, otlp, v.obs(), Map.of()));
            for (int p : List.of(pay, order, gw)) {
                Lab.waitHealthy(p, Duration.ofSeconds(90));
            }
            double startup = (System.nanoTime() - started) / 1e9;
            String url = "http://127.0.0.1:" + gw + "/orders";
            Lab.load(url, rate, Duration.ofSeconds(15));
            long cpu0 = procs.stream().mapToLong(Lab.Proc::cpuMillis).sum();
            long col0 = collector.cpuMillis();
            List<Lab.Result> r = Lab.load(url, rate, Duration.ofSeconds(30));
            long cpu = procs.stream().mapToLong(Lab.Proc::cpuMillis).sum() - cpu0;
            long col = collector.cpuMillis() - col0;
            long rss = procs.stream().mapToLong(Lab.Proc::rssMb).sum();
            List<Double> lat = r.stream().filter(x -> x.status() == 201).map(x -> x.latencyMicros() / 1000.0).toList();
            long failed = r.stream().filter(x -> x.status() != 201).count();
            System.out.printf("%-30s %8.1f ms %8.1f ms   %16.0f ms / %4.0f ms %24d MB %8.1f s%s%n", v.name(),
                    Lab.percentile(lat, 50), Lab.percentile(lat, 99), cpu * 1000.0 / r.size(), col * 1000.0 / r.size(),
                    rss, startup, failed > 0 ? "  (" + failed + " failed)" : "");
            for (Lab.Proc p : procs) {
                p.stop();
            }
            collector.stop();
        }
        System.out.println();
    }

    // =========================================================================================================== profile

    static void profile() throws Exception {
        Lab.reset();
        int otlp = Lab.freePort();
        Lab.Proc collector = Lab.collector("""
                receivers:
                  otlp:
                    protocols:
                      http:
                        endpoint: 127.0.0.1:%d
                processors:
                  batch: {}
                exporters:
                  file: {path: %s/traces.json}
                  nop: {}
                service:
                  telemetry:
                    metrics: {level: none}
                  pipelines:
                    traces: {receivers: [otlp], processors: [batch], exporters: [file]}
                    metrics: {receivers: [otlp], processors: [batch], exporters: [nop]}
                    logs: {receivers: [otlp], processors: [batch], exporters: [nop]}
                """.formatted(otlp, Lab.DIR));
        Lab.waitForPort(otlp, Duration.ofSeconds(30));
        int gw = Lab.freePort();
        int order = Lab.freePort();
        int pay = Lab.freePort();
        Path instances = Lab.DIR.resolve("payment-instances.txt");
        Files.writeString(instances, "payment-a http://127.0.0.1:" + pay + "\n");
        Lab.Observability agent = Lab.Observability.agent(null);
        Lab.Proc payment = Lab.service("payment-service", "1.6", "payment-a", pay, "", otlp,
                new Lab.Observability(true, null, false, "profile"), Map.of("ff.hotspot.iterations", "4000"));
        List<Lab.Proc> procs = List.of(payment,
                Lab.service("order-service", "1.4", "order-a", order, "file:" + instances, otlp, agent, Map.of()),
                Lab.service("gateway", "1.4", "gateway-a", gw, "http://127.0.0.1:" + order, otlp, agent, Map.of()));
        for (int p : List.of(pay, order, gw)) {
            Lab.waitHealthy(p, Duration.ofSeconds(90));
        }
        String url = "http://127.0.0.1:" + gw + "/orders";
        Lab.load(url, 60, Duration.ofSeconds(10));
        long t0 = System.currentTimeMillis();
        long cpu0 = payment.cpuMillis();
        List<Lab.Result> r = Lab.load(url, 60, Duration.ofSeconds(30));
        long cpu = payment.cpuMillis() - cpu0;
        Thread.sleep(1000);
        for (Lab.Proc p : procs) {
            p.stop();
        }
        Thread.sleep(3000);
        collector.stop();

        List<Span> spans = spans(Lab.DIR.resolve("traces.json"));
        Map<String, Span> byId = new HashMap<>();
        spans.forEach(s -> byId.put(s.spanId(), s));
        List<Double> payMs = new ArrayList<>();
        int children = 0;
        int payments = 0;
        for (Span s : spans) {
            if (s.startNs() / 1_000_000 < t0) {
                continue;
            }
            if (s.service().equals("payment-service") && s.kind() == SERVER && s.name().startsWith("POST")) {
                payMs.add(s.ms());
                payments++;
            }
            Span parent = s.parent() == null ? null : byId.get(s.parent());
            if (parent != null && parent.service().equals("payment-service") && parent.kind() == SERVER) {
                children++;
            }
        }
        System.out.println("payment-service 1.6 derives each payment's idempotency key with PBKDF2 (4,000 iterations); 60 orders/s for 30 s");
        System.out.printf("-- the trace: POST /payments p50 %.1f ms, p99 %.1f ms (2-8 ms of it is the database sleep); "
                + "child spans under it: %d of %d payments; payment-service CPU: %.1f ms per payment%n",
                Lab.percentile(payMs, 50), Lab.percentile(payMs, 99), children, payments, (double) cpu / r.size());

        Path jfr = Lab.DIR.resolve("payment-a.jfr");
        String json = run(List.of(Path.of(System.getProperty("java.home"), "bin", "jfr").toString(), "print", "--json",
                "--stack-depth", "64", "--events", "jdk.ExecutionSample", jfr.toString()));
        List<Object> events = Json.arr(Json.at(Json.parse(json), "recording", "events"));
        Map<String, Integer> inclusive = new HashMap<>();
        Map<String, Integer> self = new HashMap<>();
        int total = 0;
        for (Object e : events) {
            List<Object> frames = Json.arr(Json.at(e, "values", "stackTrace", "frames"));
            if (frames.isEmpty()) {
                continue;
            }
            total++;
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < frames.size(); i++) {
                String m = Json.at(frames.get(i), "method", "type", "name") + "." + Json.at(frames.get(i), "method", "name");
                m = m.replace('/', '.');
                if (i == 0) {
                    self.merge(m, 1, Integer::sum);
                }
                if (seen.add(m)) {
                    inclusive.merge(m, 1, Integer::sum);
                }
            }
        }
        System.out.printf("-- the profile: %d CPU samples from payment-service (JFR, profile settings: every 10 ms per running thread)%n", total);
        System.out.println("method (inclusive: on the stack)                                      samples");
        for (String m : List.of("Services.payment", "Services.idempotencyKey", "javax.crypto.SecretKeyFactory.generateSecret",
                "Services.respond")) {
            int n = inclusive.entrySet().stream().filter(x -> x.getKey().startsWith(m)).mapToInt(Map.Entry::getValue).max().orElse(0);
            System.out.printf("%-68s %6.0f%%%n", m, 100.0 * n / Math.max(1, total));
        }
        System.out.println("hottest methods (self: top of the stack)");
        int samples = total;
        self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4).forEach(x ->
                System.out.printf("  %-66s %6.0f%%%n", x.getKey(), 100.0 * x.getValue() / Math.max(1, samples)));
        System.out.println();
    }

    static String run(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IOException(String.join(" ", cmd) + " failed");
        }
        return out;
    }

    // =========================================================================================================== cardinality

    static void cardinality() throws Exception {
        System.out.println("one service's order metrics: a counter and a 10-bucket histogram per route (5) x status (4) x restaurant_id;");
        System.out.println("Prometheus 3 scrapes every 5 s for 60 s; memory read from its own /metrics, query = p99 by route over 1 m");
        System.out.println("restaurants   label sets      series   scrape body   scrape took   Prometheus RSS   heap in use   p99-by-route query");
        for (int restaurants : List.of(1, 10, 100, 1000)) {
            Lab.reset();
            int port = Lab.freePort();
            Lab.PROM_PORT = Lab.freePort();
            Lab.Proc emitter = Lab.start("emitter", List.of(Lab.JAVA, "-Xmx1g", "-cp", Lab.CP, "MetricsEmitter",
                    String.valueOf(port), String.valueOf(restaurants)));
            Lab.waitForPort(port, Duration.ofSeconds(30));
            Lab.Proc prom = Lab.prometheus("prometheus", "127.0.0.1:" + port, "5s");
            Lab.waitPrometheusReady();
            Thread.sleep(60_000);
            int body = Lab.getBody("http://127.0.0.1:" + port + "/metrics").length();
            String self = Lab.getBody("http://127.0.0.1:" + Lab.PROM_PORT + "/metrics");
            double head = selfMetric(self, "prometheus_tsdb_head_series");
            double rss = selfMetric(self, "process_resident_memory_bytes");
            double heap = selfMetric(self, "go_memstats_heap_inuse_bytes");
            List<Object> sd = Lab.promql("avg_over_time(scrape_duration_seconds[1m])", null, null, null);
            double scrape = sd.isEmpty() ? Double.NaN : Double.parseDouble((String) Json.arr(Json.at(sd.get(0), "value")).get(1));
            List<Double> qt = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                long s = System.nanoTime();
                Lab.promql("histogram_quantile(0.99, sum by (le, route) (rate(foodfleet_order_duration_seconds_bucket[1m])))", null, null, null);
                qt.add((System.nanoTime() - s) / 1e6);
            }
            System.out.printf("%11d %12d %11.0f %10.1f MB %11.0f ms %13.0f MB %10.0f MB %16.0f ms%n", restaurants,
                    20 * restaurants, head, body / 1e6, scrape * 1000, rss / 1e6, heap / 1e6, Lab.percentile(qt, 50));
            prom.stop();
            emitter.stop();
        }
        System.out.println();
    }

    static double selfMetric(String body, String name) {
        for (String l : body.split("\n")) {
            if (l.startsWith(name + " ") || l.startsWith(name + "{")) {
                return Double.parseDouble(l.substring(l.lastIndexOf(' ') + 1));
            }
        }
        return Double.NaN;
    }
}
