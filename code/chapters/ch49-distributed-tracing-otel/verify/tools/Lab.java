import java.io.BufferedReader;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The chapter 49 lab: a NATS server, an OpenTelemetry Collector writing spans to a file, and FoodFleet's checkout
 * services as JVMs under the OpenTelemetry Java agent. Binaries from -Dc49.bin (nats-server, otelcol-contrib,
 * opentelemetry-javaagent.jar); the services' classpath from -Dc49.cp; work files in -Dc49.dir (default /tmp/c49).
 */
final class Lab {

    private Lab() {
    }

    static final Path BIN = Path.of(System.getProperty("c49.bin", "build/bin")).toAbsolutePath();
    static final Path DIR = Path.of(System.getProperty("c49.dir", "/tmp/c49"));
    static final String CP = System.getProperty("c49.cp");
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();

    /** The running lab: processes, ports and output files. */
    static final class Run implements AutoCloseable {
        final List<Process> processes = new ArrayList<>();
        final Map<String, Integer> ports = new LinkedHashMap<>();
        int otlp;
        int nats;

        void add(Process p) {
            processes.add(p);
        }

        @Override
        public void close() throws InterruptedException {
            // services first (the agent flushes spans on shutdown), then the collector, then NATS
            for (int i = processes.size() - 1; i >= 0; i--) {
                Process p = processes.get(i);
                p.destroy();
                if (!p.waitFor(20, TimeUnit.SECONDS)) {
                    p.destroyForcibly().waitFor();
                }
                if (i == 2) {
                    Thread.sleep(2000);
                }
            }
        }
    }

    /** How one service is configured: agent settings and the service's own -Dff.* properties. */
    record Svc(String name, Map<String, String> otel, Map<String, String> ff) {
    }

    static Run start(String label, List<Svc> services) throws Exception {
        new ProcessBuilder("rm", "-rf", DIR.resolve(label).toString()).start().waitFor();
        Files.createDirectories(DIR.resolve(label));
        Run run = new Run();
        run.nats = freePort();
        run.otlp = freePort();
        run.add(proc(label, "nats", List.of(BIN.resolve("nats-server").toString(), "-a", "127.0.0.1", "-p", String.valueOf(run.nats))));
        Path cfg = DIR.resolve(label).resolve("collector.yaml");
        Files.writeString(cfg, """
                receivers: {otlp: {protocols: {http: {endpoint: 127.0.0.1:%d}}}}
                processors: {batch: {timeout: 1s}}
                exporters: {file: {path: %s}, nop: {}}
                service:
                  telemetry: {metrics: {level: none}}
                  pipelines:
                    traces: {receivers: [otlp], processors: [batch], exporters: [file]}
                    metrics: {receivers: [otlp], processors: [batch], exporters: [nop]}
                    logs: {receivers: [otlp], processors: [batch], exporters: [nop]}
                """.formatted(run.otlp, DIR.resolve(label).resolve("spans.json")));
        run.add(proc(label, "collector", List.of(BIN.resolve("otelcol-contrib").toString(), "--config=" + cfg)));
        waitPort(run.nats);
        waitPort(run.otlp);
        for (Svc s : services) {
            run.ports.put(s.name(), freePort());
        }
        for (Svc s : services) {
            List<String> cmd = new ArrayList<>(List.of(JAVA, "-Xmx256m", "-XX:+UseSerialGC"));
            if (!s.name().equals("external")) {
                cmd.add("-javaagent:" + BIN.resolve("opentelemetry-javaagent.jar"));
                cmd.add("-Dotel.service.name=" + s.name());
                cmd.add("-Dotel.exporter.otlp.endpoint=http://127.0.0.1:" + run.otlp);
                cmd.add("-Dotel.metrics.exporter=none");
                cmd.add("-Dotel.logs.exporter=none");
                cmd.add("-Dotel.bsp.schedule.delay=500");
                s.otel().forEach((k, v) -> cmd.add("-D" + k + "=" + v));
            }
            Map<String, String> ff = new HashMap<>(s.ff());
            ff.put("ff.service", s.name());
            ff.put("ff.port", String.valueOf(run.ports.get(s.name())));
            ff.put("ff.nats", "nats://127.0.0.1:" + run.nats);
            ff.put("ff.out", DIR.resolve(label).resolve(s.name() + ".out").toString());
            for (String target : List.of("order-service", "payment-service", "external")) {
                if (run.ports.containsKey(target)) {
                    String key = switch (target) {
                        case "order-service" -> "ff.downstream";
                        case "payment-service" -> "ff.payment";
                        default -> "ff.external";
                    };
                    ff.put(key, "http://127.0.0.1:" + run.ports.get(target));
                }
            }
            ff.forEach((k, v) -> cmd.add("-D" + k + "=" + v));
            cmd.addAll(List.of("-cp", CP, "Services"));
            run.add(proc(label, s.name(), cmd));
        }
        for (Svc s : services) {
            waitHealthy(run.ports.get(s.name()));
        }
        return run;
    }

    static Process proc(String label, String name, List<String> cmd) throws IOException {
        return new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(DIR.resolve(label).resolve(name + ".log").toFile()).start();
    }

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    static void waitPort(int port) throws InterruptedException {
        for (int i = 0; i < 600; i++) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("nothing on " + port);
    }

    static void waitHealthy(int port) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < end) {
            try {
                if (HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // starting
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("port " + port + " not healthy");
    }

    /** {@code count} orders at {@code perSecond}, each with the given request headers; returns how many got 201. */
    static int orders(Run run, int count, int perSecond, Map<String, String> headers) throws Exception {
        String url = "http://127.0.0.1:" + run.ports.get("gateway") + "/orders";
        int[] ok = {0};
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) {
                long due = start + i * 1_000_000_000L / perSecond;
                long wait = due - System.nanoTime();
                if (wait > 0) {
                    TimeUnit.NANOSECONDS.sleep(wait);
                }
                ex.submit(() -> {
                    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.noBody());
                    headers.forEach((k, v) -> b.header(k, v.equals("RANDOM_SAMPLED_TRACEPARENT") ? randomTraceparent() : v));
                    try {
                        if (HTTP.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 201) {
                            synchronized (ok) {
                                ok[0]++;
                            }
                        }
                    } catch (Exception e) {
                        // counted as not ok
                    }
                });
            }
        }
        Thread.sleep(3000);
        return ok[0];
    }

    /** What any client can send: a made-up trace ID and parent, with the sampled flag set. */
    static String randomTraceparent() {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        return String.format("00-%016x%016x-%016x-01", r.nextLong() | 1, r.nextLong() | 1, r.nextLong() | 1);
    }

    // ------------------------------------------------------------------------------------------------ spans

    record Span(String service, String scope, String name, long kind, String traceId, String spanId, String parent,
                long startNs, long endNs, Map<String, String> attrs) {
    }

    static List<Span> spans(String label) throws IOException {
        List<Span> out = new ArrayList<>();
        Path file = DIR.resolve(label).resolve("spans.json");
        if (!Files.exists(file)) {
            return out;
        }
        try (BufferedReader r = Files.newBufferedReader(file)) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.isBlank()) {
                    continue;
                }
                for (Object rs : Json.arr(Json.at(Json.parse(line), "resourceSpans"))) {
                    String service = null;
                    for (Object a : Json.arr(Json.at(rs, "resource", "attributes"))) {
                        if ("service.name".equals(Json.at(a, "key"))) {
                            service = (String) Json.at(a, "value", "stringValue");
                        }
                    }
                    for (Object ss : Json.arr(Json.at(rs, "scopeSpans"))) {
                        String scope = (String) Json.at(ss, "scope", "name");
                        for (Object sp : Json.arr(Json.at(ss, "spans"))) {
                            Map<String, String> attrs = new HashMap<>();
                            for (Object a : Json.arr(Json.at(sp, "attributes"))) {
                                Object v = Json.at(a, "value");
                                Object sv = Json.at(v, "stringValue");
                                if (sv == null && Json.at(v, "arrayValue") != null) {
                                    List<Object> vals = Json.arr(Json.at(v, "arrayValue", "values"));
                                    sv = vals.isEmpty() ? null : Json.at(vals.get(0), "stringValue");
                                }
                                if (sv == null) {
                                    sv = Json.at(v, "intValue");
                                }
                                if (sv != null) {
                                    attrs.put((String) Json.at(a, "key"), String.valueOf(sv));
                                }
                            }
                            Object parent = Json.at(sp, "parentSpanId");
                            out.add(new Span(service, scope, (String) Json.at(sp, "name"), ((Number) Json.at(sp, "kind")).longValue(),
                                    (String) Json.at(sp, "traceId"), (String) Json.at(sp, "spanId"),
                                    parent == null || ((String) parent).isEmpty() ? null : (String) parent,
                                    Long.parseLong((String) Json.at(sp, "startTimeUnixNano")),
                                    Long.parseLong((String) Json.at(sp, "endTimeUnixNano")), attrs));
                        }
                    }
                }
            }
        }
        return out;
    }

    static List<String> lines(String label, String service) throws IOException {
        Path p = DIR.resolve(label).resolve(service + ".out");
        return Files.exists(p) ? Files.readAllLines(p) : List.of();
    }

    static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
