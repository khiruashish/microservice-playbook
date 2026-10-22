import java.io.IOException;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The chapter's lab: FoodFleet's checkout services as separate JVMs (with or without the OpenTelemetry Java agent),
 * an OpenTelemetry Collector, Prometheus, and an open-loop load generator. Binaries come from -Dc47.bin
 * (build/bin: opentelemetry-javaagent.jar, otelcol-contrib, prometheus); the classpath of the services from
 * -Dc47.cp; work files go to -Dc47.dir (default /tmp/c47).
 */
final class Lab {

    private Lab() {
    }

    static final Path BIN = Path.of(System.getProperty("c47.bin", "build/bin")).toAbsolutePath();
    static final Path DIR = Path.of(System.getProperty("c47.dir", "/tmp/c47"));
    static final String CP = System.getProperty("c47.cp", "build/main:build/tools");
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null))
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2))
            .executor(Executors.newVirtualThreadPerTaskExecutor()).build();

    static void reset() throws IOException, InterruptedException {
        new ProcessBuilder("rm", "-rf", DIR.toString()).start().waitFor();
        Files.createDirectories(DIR);
    }

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    static void waitForPort(int port, Duration max) throws InterruptedException {
        long end = System.nanoTime() + max.toNanos();
        while (System.nanoTime() < end) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("nothing listening on " + port);
    }

    static int get(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    static String getBody(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    // ------------------------------------------------------------------------------------------------ processes

    /** A child process with its pid, for CPU and memory readings from /proc. */
    record Proc(String name, Process process) {
        long pid() {
            return process.pid();
        }

        /** User + system CPU time so far, in milliseconds (Linux clock ticks are 10 ms). */
        long cpuMillis() {
            try {
                String stat = Files.readString(Path.of("/proc/" + pid() + "/stat"));
                String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
                return (Long.parseLong(f[11]) + Long.parseLong(f[12])) * 10;
            } catch (IOException e) {
                return -1;
            }
        }

        long rssMb() {
            try {
                for (String l : Files.readAllLines(Path.of("/proc/" + pid() + "/status"))) {
                    if (l.startsWith("VmRSS:")) {
                        return Long.parseLong(l.replaceAll("\\D", "")) / 1024;
                    }
                }
            } catch (IOException ignored) {
                // gone
            }
            return -1;
        }

        void stop() throws InterruptedException {
            process.destroy();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    static Proc start(String name, List<String> cmd) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(DIR.resolve(name + ".log").toFile()).start();
        return new Proc(name, p);
    }

    /** How a service JVM is observed. */
    record Observability(boolean agent, String sampler, boolean exportNothing, String jfr) {
        static Observability none() {
            return new Observability(false, null, false, null);
        }

        static Observability agent(String sampler) {
            return new Observability(true, sampler, false, null);
        }
    }

    /** Starts one FoodFleet service JVM. */
    static Proc service(String service, String version, String pod, int port, String downstream, int otlpPort,
                        Observability o, Map<String, String> extra) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(JAVA, "-Xmx256m", "-XX:+UseSerialGC"));
        if (o.agent()) {
            cmd.add("-javaagent:" + BIN.resolve("opentelemetry-javaagent.jar"));
            cmd.add("-Dotel.service.name=" + service);
            cmd.add("-Dotel.resource.attributes=service.version=" + version + ",service.instance.id=" + pod);
            cmd.add("-Dotel.exporter.otlp.endpoint=http://127.0.0.1:" + otlpPort);
            cmd.add("-Dotel.metric.export.interval=5000");
            cmd.add("-Dotel.instrumentation.http.server.capture-request-headers=x-tenant,x-payment-method,x-bank");
            if (o.sampler() != null) {
                cmd.add("-Dotel.traces.sampler=" + o.sampler().split(":")[0]);
                if (o.sampler().contains(":")) {
                    cmd.add("-Dotel.traces.sampler.arg=" + o.sampler().split(":")[1]);
                }
            }
            if (o.exportNothing()) {
                cmd.add("-Dotel.traces.exporter=none");
                cmd.add("-Dotel.metrics.exporter=none");
                cmd.add("-Dotel.logs.exporter=none");
            }
        }
        if (o.jfr() != null) {
            cmd.add("-XX:StartFlightRecording=settings=" + o.jfr() + ",filename=" + DIR.resolve(pod + ".jfr"));
        }
        cmd.add("-Dff.service=" + service);
        cmd.add("-Dff.version=" + version);
        cmd.add("-Dff.pod=" + pod);
        cmd.add("-Dff.port=" + port);
        cmd.add("-Dff.downstream=" + downstream);
        extra.forEach((k, v) -> cmd.add("-D" + k + "=" + v));
        cmd.add("-cp");
        cmd.add(CP);
        cmd.add("Services");
        return start(pod, cmd);
    }

    static Duration waitHealthy(int port, Duration max) throws InterruptedException {
        long start = System.nanoTime();
        long end = start + max.toNanos();
        while (System.nanoTime() < end) {
            if (get("http://127.0.0.1:" + port + "/health") == 200) {
                return Duration.ofNanos(System.nanoTime() - start);
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("port " + port + " not healthy");
    }

    // ------------------------------------------------------------------------------------------------ collector

    static Proc collector(String config) throws IOException, InterruptedException {
        Path file = DIR.resolve("collector.yaml");
        Files.writeString(file, config);
        Proc p = start("collector", List.of(BIN.resolve("otelcol-contrib").toString(), "--config=" + file));
        return p;
    }

    static Proc prometheus(String name, String scrapeTarget, String interval) throws IOException {
        Path cfg = DIR.resolve(name + ".yml");
        Files.writeString(cfg, """
                global:
                  scrape_interval: %s
                  scrape_timeout: %s
                scrape_configs:
                  - job_name: foodfleet
                    honor_labels: true
                    static_configs:
                      - targets: ['%s']
                """.formatted(interval, interval, scrapeTarget));
        return start(name, List.of(BIN.resolve("prometheus").toString(), "--config.file=" + cfg,
                "--storage.tsdb.path=" + DIR.resolve(name + "-data"), "--web.listen-address=127.0.0.1:" + PROM_PORT,
                "--log.level=warn"));
    }

    static int PROM_PORT;

    static void waitPrometheusReady() throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < end) {
            if (get("http://127.0.0.1:" + PROM_PORT + "/-/ready") == 200) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("prometheus not ready");
    }

    /** A PromQL instant or range query; returns Prometheus's "result" array. */
    static List<Object> promql(String query, Long start, Long end, String step) throws IOException, InterruptedException {
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = start == null ? "http://127.0.0.1:" + PROM_PORT + "/api/v1/query?query=" + q
                : "http://127.0.0.1:" + PROM_PORT + "/api/v1/query_range?query=" + q + "&start=" + start + "&end=" + end + "&step=" + step;
        Object r = Json.parse(getBody(url));
        if (!"success".equals(Json.at(r, "status"))) {
            throw new IllegalStateException(query + " -> " + r);
        }
        return Json.arr(Json.at(r, "data", "result"));
    }

    // ------------------------------------------------------------------------------------------------ load

    /** One user request's outcome. */
    record Result(long sentMillis, long latencyMicros, int status, String tenant, String method, String bank) {
    }

    static final String[] TENANTS = new String[40];
    static final double[] TENANT_WEIGHTS = new double[40];

    static {
        double total = 0;
        for (int k = 0; k < 40; k++) {
            TENANTS[k] = "chain-" + (k + 1);
            total += 1.0 / (k + 1);
            TENANT_WEIGHTS[k] = total;
        }
        for (int k = 0; k < 40; k++) {
            TENANT_WEIGHTS[k] /= total;
        }
    }

    /**
     * Open-loop load: {@code rate} requests per second to {@code url} for {@code duration}, each sent on time whether
     * or not earlier ones have finished (so a slow service can't slow the arrivals down). Latency is measured from the
     * moment each request was due. Tenants are Zipf-distributed; methods UPI 50%, CARD 35%, WALLET 15%; banks for UPI
     * and CARD bank-a, -b, -c, -d and -x at 20% each.
     */
    static List<Result> load(String url, int rate, Duration duration) throws InterruptedException {
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        long start = System.nanoTime();
        long total = rate * duration.toMillis() / 1000;
        long t0 = System.currentTimeMillis();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long n = 0; n < total; n++) {
                long due = start + n * 1_000_000_000L / rate;
                long wait = due - System.nanoTime();
                if (wait > 0) {
                    TimeUnit.NANOSECONDS.sleep(wait);
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double u = r.nextDouble();
                int k = 0;
                while (TENANT_WEIGHTS[k] < u) {
                    k++;
                }
                String tenant = TENANTS[k];
                double m = r.nextDouble();
                String method = m < 0.5 ? "UPI" : m < 0.85 ? "CARD" : "WALLET";
                String bank = method.equals("WALLET") ? "wallet-co" : "bank-" + "abcdx".charAt(r.nextInt(5));
                long sentMillis = t0 + (due - start) / 1_000_000;
                ex.submit(() -> {
                    int status;
                    try {
                        status = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                                .header("x-tenant", tenant).header("x-payment-method", method).header("x-bank", bank)
                                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding())
                                .statusCode();
                    } catch (IOException e) {
                        status = -1;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        status = -1;
                    }
                    results.add(new Result(sentMillis, (System.nanoTime() - due) / 1000, status, tenant, method, bank));
                });
            }
        }
        List<Result> out = new ArrayList<>(results);
        out.sort((a, b) -> Long.compare(a.sentMillis(), b.sentMillis()));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ stats

    static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> s = new ArrayList<>(values);
        Collections.sort(s);
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }

    static long bytes(Path p) {
        try {
            return Files.exists(p) ? Files.size(p) : 0;
        } catch (IOException e) {
            return 0;
        }
    }
}
