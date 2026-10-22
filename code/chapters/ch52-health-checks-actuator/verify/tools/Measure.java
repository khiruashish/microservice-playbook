import java.io.IOException;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chapter 52's experiments on a real Kubernetes cluster (k3s 1.37 here): kubelet probes, Service endpoints and rolling
 * updates are Kubernetes' own; order-service is {@link OrderService} in a container; PostgreSQL runs outside the
 * cluster, like a managed database.
 * <ul>
 *   <li>{@code dependency}: the database goes away for 60 s; what each probe configuration does to traffic and pods</li>
 *   <li>{@code startup}: a service that takes 40 s to start, with and without a startup probe</li>
 *   <li>{@code shutdown}: a rolling restart under load, with and without graceful shutdown and a drain</li>
 *   <li>{@code actuator}: what a heap dump contains, and who can reach the management port before and after a
 *       NetworkPolicy</li>
 * </ul>
 * System properties: c52.kubectl (command, default "kubectl"), c52.image, c52.node (node IP, for the NodePort and the
 * database), c52.dbStop / c52.dbStart (shell commands), c52.java and c52.cp (for running order-service locally).
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c52.kubectl", "kubectl").split(" ");
    static final String IMAGE = System.getProperty("c52.image", "docker.io/foodfleet/order-service:ch52");
    static final String NODE = System.getProperty("c52.node", "127.0.0.1");
    static final String URL = "http://" + NODE + ":30080";
    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null)).connectTimeout(Duration.ofSeconds(2)).build();
    static final String NS = "ch52";

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            switch (mode) {
                case "dependency" -> dependency();
                case "startup" -> startup();
                case "shutdown" -> shutdown();
                case "actuator" -> actuator();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ===================================================================================================== cluster

    static String kubectl(String stdin, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(Arrays.asList(KUBECTL));
        cmd.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }

    static void sh(String command) throws IOException, InterruptedException {
        new ProcessBuilder("bash", "-c", command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
    }

    /** A fresh namespace for each configuration. */
    static void reset() throws Exception {
        kubectl(null, "delete", "namespace", NS, "--ignore-not-found", "--wait=true", "--timeout=120s");
        kubectl(null, "create", "namespace", NS);
    }

    /** Probe and shutdown settings for one configuration. */
    record Config(String label, String liveness, String readiness, boolean startupProbe, int startupSeconds, String env,
                  int livenessInitialDelay, int preStopSleep) {
    }

    static String manifest(Config c, int replicas) {
        String startup = c.startupProbe() ? """
                          startupProbe: {httpGet: {path: /actuator/health/liveness, port: 8081}, periodSeconds: 2, failureThreshold: 60}
                """ : "";
        String preStop = c.preStopSleep() > 0 ? """
                          lifecycle: {preStop: {sleep: {seconds: %d}}}
                """.formatted(c.preStopSleep()) : "";
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata: {name: order-service, namespace: %s}
                spec:
                  replicas: %d
                  strategy: {rollingUpdate: {maxUnavailable: 0, maxSurge: 1}}
                  selector: {matchLabels: {app: order-service}}
                  template:
                    metadata: {labels: {app: order-service}}
                    spec:
                      terminationGracePeriodSeconds: 30
                      containers:
                        - name: app
                          image: %s
                          imagePullPolicy: Never
                          env: [{name: DB_HOST, value: "%s"}, {name: STARTUP_SECONDS, value: "%d"}%s]
                          ports: [{containerPort: 8080, name: http}, {containerPort: 8081, name: management}]
                          resources: {requests: {cpu: 100m, memory: 160Mi}, limits: {memory: 256Mi}}
                          livenessProbe: {httpGet: {path: %s, port: 8081}, initialDelaySeconds: %d, periodSeconds: 5, timeoutSeconds: 1, failureThreshold: 3}
                          readinessProbe: {httpGet: {path: %s, port: 8081}, periodSeconds: 5, timeoutSeconds: 1, failureThreshold: 3}
                %s%s---
                apiVersion: v1
                kind: Service
                metadata: {name: order-service, namespace: %s}
                spec:
                  type: NodePort
                  selector: {app: order-service}
                  ports: [{name: http, port: 80, targetPort: 8080, nodePort: 30080}]
                """.formatted(NS, replicas, IMAGE, NODE, c.startupSeconds(), c.env(), c.liveness(), c.livenessInitialDelay(), c.readiness(),
                startup, preStop, NS);
    }

    /** Ready pods and total restarts, from the API server. */
    static int[] podState() throws Exception {
        String out = kubectl(null, "-n", NS, "get", "pods", "-l", "app=order-service", "-o",
                "jsonpath={range .items[*]}{.status.containerStatuses[0].ready} {.status.containerStatuses[0].restartCount}{\"\\n\"}{end}");
        int ready = 0;
        int restarts = 0;
        for (String l : out.split("\n")) {
            String[] p = l.trim().split(" ");
            if (p.length == 2 && !p[1].isEmpty() && Character.isDigit(p[1].charAt(0))) {
                if (p[0].equals("true")) {
                    ready++;
                }
                restarts += Integer.parseInt(p[1]);
            }
        }
        return new int[] {ready, restarts};
    }

    static void waitReady(int n, int seconds) throws Exception {
        for (int i = 0; i < seconds; i++) {
            if (podState()[0] >= n) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("pods not ready after " + seconds + " s");
    }

    // ======================================================================================================== load

    record Result(long atMillis, String endpoint, String outcome) {
    }

    /**
     * Sends {@code perSecond} requests a second to each of four streams until stopped, and records every outcome:
     * GET /menu and POST /orders, each over pooled keep-alive connections (like a service's HTTP client) and over a
     * new connection per request (like a fresh client, or a load balancer opening connections). The difference
     * matters: Kubernetes routes new connections to ready pods only; a connection already open stays with its pod.
     */
    static final class Load implements AutoCloseable {
        static final List<String> STREAMS = List.of("/menu keep-alive", "/menu new connection", "/orders keep-alive", "/orders new connection");
        final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean(true);
        final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        final long t0;
        final Thread driver;

        Load(int perSecond, long t0) {
            this.t0 = t0;
            driver = Thread.ofPlatform().start(() -> {
                long start = System.nanoTime();
                for (long i = 0; running.get(); i++) {
                    long due = start + i * 1_000_000_000L / perSecond;
                    long wait = due - System.nanoTime();
                    if (wait > 0) {
                        try {
                            TimeUnit.NANOSECONDS.sleep(wait);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    pool.submit(() -> pooled("GET", "/menu"));
                    pool.submit(() -> fresh("GET", "/menu"));
                    pool.submit(() -> pooled("POST", "/orders"));
                    pool.submit(() -> fresh("POST", "/orders"));
                }
            });
        }

        void pooled(String method, String path) {
            long at = System.currentTimeMillis() - t0;
            String outcome;
            try {
                HttpResponse<Void> r = HTTP.send(HttpRequest.newBuilder(URI.create(URL + path)).timeout(Duration.ofSeconds(3))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                outcome = r.statusCode() < 300 ? "ok" : String.valueOf(r.statusCode());
            } catch (HttpTimeoutException e) {
                outcome = "timeout";
            } catch (ConnectException e) {
                outcome = "refused";
            } catch (IOException e) {
                outcome = "reset";
            } catch (InterruptedException e) {
                return;
            }
            results.add(new Result(at, path + " keep-alive", outcome));
        }

        /** HTTP/1.1 with Connection: close on a socket of its own. */
        void fresh(String method, String path) {
            long at = System.currentTimeMillis() - t0;
            String outcome;
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(NODE, 30080), 2000);
                s.setSoTimeout(3000);
                s.getOutputStream().write((method + " " + path + " HTTP/1.1\r\nHost: order-service\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                String status = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                int code = status.startsWith("HTTP/1.1 ") ? Integer.parseInt(status.substring(9, 12)) : -1;
                outcome = code < 0 ? "reset" : code < 300 ? "ok" : String.valueOf(code);
            } catch (java.net.SocketTimeoutException e) {
                outcome = "timeout";
            } catch (ConnectException e) {
                outcome = "refused";
            } catch (IOException e) {
                outcome = "reset";
            }
            results.add(new Result(at, path + " new connection", outcome));
        }

        @Override
        public void close() throws InterruptedException {
            running.set(false);
            driver.join();
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        /** Outcomes per endpoint for requests sent in [from, to) ms. */
        Map<String, Map<String, Integer>> between(long from, long to) {
            Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
            STREAMS.forEach(k -> m.put(k, new LinkedHashMap<>()));
            for (Result r : results) {
                if (r.atMillis() >= from && r.atMillis() < to) {
                    m.get(r.endpoint()).merge(r.outcome(), 1, Integer::sum);
                }
            }
            return m;
        }

        /** The first second at or after {@code from} from which /orders succeeded ≥ 95% for 10 seconds running. */
        long recoveredAt(long from) {
            int[] ok = new int[1000];
            int[] all = new int[1000];
            for (Result r : results) {
                if (r.endpoint().startsWith("/orders")) {
                    int s = (int) (r.atMillis() / 1000);
                    all[s]++;
                    if (r.outcome().equals("ok")) {
                        ok[s]++;
                    }
                }
            }
            for (int s = (int) (from / 1000); s < 990; s++) {
                boolean good = true;
                for (int k = s; k < s + 10; k++) {
                    if (all[k] == 0 || ok[k] < 0.95 * all[k]) {
                        good = false;
                        break;
                    }
                }
                if (good) {
                    return s * 1000L;
                }
            }
            return -1;
        }
    }

    static String summary(Map<String, Integer> outcomes) {
        int total = outcomes.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return "-";
        }
        int ok = outcomes.getOrDefault("ok", 0);
        StringBuilder b = new StringBuilder(String.format("%5.1f%% ok", 100.0 * ok / total));
        outcomes.entrySet().stream().filter(e -> !e.getKey().equals("ok")).sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> b.append(", ").append(e.getValue()).append(' ').append(e.getKey()));
        return b.toString();
    }

    // ================================================================================================== dependency

    static void dependency() throws Exception {
        String stop = System.getProperty("c52.dbStop");
        String start = System.getProperty("c52.dbStart");
        String all = "/actuator/health";
        String live = "/actuator/health/liveness";
        String ready = "/actuator/health/readiness";
        List<Config> configs = List.of(
                new Config("liveness = /actuator/health (includes the database)", all, ready, true, 15, "", 0, 0),
                new Config("readiness includes the database", live, ready, true, 15, ", {name: READINESS_INCLUDES_DB, value: \"true\"}", 0, 0),
                new Config("neither probe includes the database (Boot's groups)", live, ready, true, 15, "", 0, 0));
        System.out.println("3 replicas; probes every 5 s, timeout 1 s, 3 failures; 15 s startup behind a startup probe;");
        System.out.println("20 requests/s to each stream: GET /menu (no database) and POST /orders (database), over keep-alive and new connections;");
        System.out.println("PostgreSQL stopped at 30 s for 60 s");
        for (Config c : configs) {
            reset();
            kubectl(manifest(c, 3), "apply", "-f", "-");
            waitReady(3, 180);
            warmUp();
            long t0 = System.currentTimeMillis();
            List<int[]> states = new ArrayList<>();
            long upAt;
            try (Load load = new Load(20, t0)) {
                sample(states, t0, 30);
                sh(stop);
                long downAt = System.currentTimeMillis() - t0;
                sample(states, t0, 90);
                sh(start);
                upAt = System.currentTimeMillis() - t0;
                sample(states, t0, 210);
                load.close();
                int minReady = states.stream().filter(s -> s[0] >= downAt && s[0] < upAt + 60_000).mapToInt(s -> s[1]).min().orElse(-1);
                int restarts = states.getLast()[2];
                long recovered = load.recoveredAt(upAt);
                System.out.println("-- " + c.label());
                Map<String, Map<String, Integer>> before = load.between(0, downAt);
                Map<String, Map<String, Integer>> during = load.between(downAt, upAt);
                Map<String, Map<String, Integer>> after = load.between(upAt, 210_000);
                System.out.printf("   %-24s %-34s %-44s %s%n", "", "before (30 s)", "database down (60 s)", "after (120 s)");
                for (String k : Load.STREAMS) {
                    System.out.printf("   %-24s %-34s %-44s %s%n", k, summary(before.get(k)), summary(during.get(k)), summary(after.get(k)));
                }
                System.out.printf("   ready pods at the lowest: %d of 3; container restarts: %d; /orders back to >=95%% %s%n", minReady,
                        restarts, recovered < 0 ? "never (within 120 s)" : recovered <= upAt ? "as soon as the database was"
                                : String.format("%.0f s after the database", (recovered - upAt) / 1000.0));
            }
        }
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println();
    }

    /** Ten seconds of traffic that isn't counted: JIT compilation, connection pools and the first database connections. */
    static void warmUp() throws Exception {
        try (Load warm = new Load(20, System.currentTimeMillis())) {
            Thread.sleep(10_000);
        }
    }

    /** Records {time, ready pods, restarts} every 2 s until {@code untilSeconds}. */
    static void sample(List<int[]> states, long t0, int untilSeconds) throws Exception {
        while (System.currentTimeMillis() - t0 < untilSeconds * 1000L) {
            int[] s = podState();
            states.add(new int[] {(int) (System.currentTimeMillis() - t0), s[0], s[1]});
            Thread.sleep(2000);
        }
    }

    // ===================================================================================================== startup

    static void startup() throws Exception {
        String live = "/actuator/health/liveness";
        String ready = "/actuator/health/readiness";
        List<Config> configs = List.of(
                new Config("liveness initialDelaySeconds 10, no startup probe", live, ready, false, 40, "", 10, 0),
                new Config("liveness initialDelaySeconds 60, no startup probe", live, ready, false, 40, "", 60, 0),
                new Config("startup probe (2 s x 60), liveness without delay", live, ready, true, 40, "", 0, 0));
        System.out.println("1 replica that takes 40 s to start; liveness every 5 s, 3 failures; watched for 180 s");
        for (Config c : configs) {
            reset();
            long t0 = System.currentTimeMillis();
            kubectl(manifest(c, 1), "apply", "-f", "-");
            long readyAt = -1;
            int[] s = {0, 0};
            while (System.currentTimeMillis() - t0 < 180_000) {
                s = podState();
                if (s[0] > 0 && readyAt < 0) {
                    readyAt = System.currentTimeMillis() - t0;
                }
                Thread.sleep(1000);
            }
            String reason = kubectl(null, "-n", NS, "get", "pods", "-l", "app=order-service", "-o",
                    "jsonpath={.items[0].status.containerStatuses[0].state.waiting.reason}").trim();
            System.out.printf("-- %-55s ready after: %-8s restarts in 180 s: %d%s%n", c.label(),
                    readyAt < 0 ? "never" : String.format("%.0f s", readyAt / 1000.0), s[1], reason.isEmpty() ? "" : " (now " + reason + ")");
        }
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println();
    }

    // ==================================================================================================== shutdown

    static void shutdown() throws Exception {
        String live = "/actuator/health/liveness";
        String ready = "/actuator/health/readiness";
        List<Config> configs = List.of(
                new Config("stop at once on SIGTERM", live, ready, true, 5, ", {name: GRACEFUL, value: \"false\"}", 0, 0),
                new Config("graceful: finish in-flight requests", live, ready, true, 5, ", {name: GRACEFUL, value: \"true\"}", 0, 0),
                new Config("graceful + refuse readiness, keep serving 5 s", live, ready, true, 5,
                        ", {name: GRACEFUL, value: \"true\"}, {name: DRAIN_SECONDS, value: \"5\"}", 0, 0),
                new Config("graceful + preStop sleep 5 s", live, ready, true, 5, ", {name: GRACEFUL, value: \"true\"}", 0, 5));
        System.out.println("3 replicas, rolling restart (maxSurge 1, maxUnavailable 0) under 50 requests/s to each of the four streams");
        for (Config c : configs) {
            reset();
            kubectl(manifest(c, 3), "apply", "-f", "-");
            waitReady(3, 180);
            warmUp();
            long t0 = System.currentTimeMillis();
            try (Load load = new Load(50, t0)) {
                Thread.sleep(10_000);
                long from = System.currentTimeMillis() - t0;
                kubectl(null, "-n", NS, "rollout", "restart", "deployment/order-service");
                kubectl(null, "-n", NS, "rollout", "status", "deployment/order-service", "--timeout=300s");
                long to = System.currentTimeMillis() - t0;
                Thread.sleep(15_000);
                load.close();
                Map<String, Map<String, Integer>> during = load.between(from, to + 15_000);
                int requests = during.values().stream().flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum();
                int failed = during.values().stream().flatMap(m -> m.entrySet().stream()).filter(e -> !e.getKey().equals("ok"))
                        .mapToInt(Map.Entry::getValue).sum();
                System.out.printf("-- %-50s rollout %3.0f s; %,d requests, %d failed%n", c.label(), (to - from) / 1000.0, requests, failed);
                for (String k : Load.STREAMS) {
                    System.out.printf("   %-24s %s%n", k, summary(during.get(k)));
                }
            }
        }
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println();
    }

    // ==================================================================================================== actuator

    static void actuator() throws Exception {
        heapDump();
        networkPolicy();
    }

    /** What /actuator/heapdump would hand out: the JVM's heap, written by the same HotSpot call. */
    static void heapDump() throws Exception {
        String secret = "pg-" + Long.toHexString(new java.security.SecureRandom().nextLong()) + "-s3cr3t";
        Path dir = Files.createTempDirectory("c52");
        ProcessBuilder pb = new ProcessBuilder(System.getProperty("c52.java", "java"), "-Xmx128m", "-cp", System.getProperty("c52.cp"), "OrderService")
                .redirectErrorStream(true).redirectOutput(dir.resolve("service.log").toFile());
        pb.environment().put("DB_PASSWORD", secret);
        pb.environment().put("DB_HOST", NODE);
        Process p = pb.start();
        try {
            for (int i = 0; i < 100 && !Files.readString(dir.resolve("service.log")).contains("started"); i++) {
                Thread.sleep(100);
            }
            String jcmd = Path.of(System.getProperty("c52.java", "java")).resolveSibling("jcmd").toString();
            Path dump = dir.resolve("heap.hprof");
            long t = System.nanoTime();
            Process j = new ProcessBuilder(jcmd, String.valueOf(p.pid()), "GC.heap_dump", dump.toString()).redirectErrorStream(true).start();
            j.getInputStream().readAllBytes();
            j.waitFor();
            long ms = (System.nanoTime() - t) / 1_000_000;
            byte[] bytes = Files.readAllBytes(dump);
            int hits = count(bytes, secret.getBytes(StandardCharsets.UTF_8)) + count(bytes, secret.getBytes(StandardCharsets.UTF_16BE))
                    + count(bytes, secret.getBytes(StandardCharsets.UTF_16LE));
            System.out.println("heap dump of order-service (the HotSpot call /actuator/heapdump makes), with DB_PASSWORD in its environment:");
            System.out.printf("   %,d bytes in %d ms; the password appears %d times in it%n", bytes.length, ms, hits);
            Files.delete(dump);
        } finally {
            p.destroy();
            p.waitFor();
        }
    }

    static int count(byte[] hay, byte[] needle) {
        int n = 0;
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int k = 0; k < needle.length; k++) {
                if (hay[i + k] != needle[k]) {
                    continue outer;
                }
            }
            n++;
        }
        return n;
    }

    /** Who can reach the management port: an app in another namespace and Prometheus in "monitoring", before and after. */
    static void networkPolicy() throws Exception {
        reset();
        kubectl(null, "delete", "namespace", "monitoring", "--ignore-not-found", "--wait=true", "--timeout=120s");
        kubectl(null, "create", "namespace", "monitoring");
        kubectl(null, "label", "namespace", "monitoring", "team=observability");
        Config c = new Config("", "/actuator/health/liveness", "/actuator/health/readiness", true, 3, "", 0, 0);
        kubectl(manifest(c, 1), "apply", "-f", "-");
        waitReady(1, 120);
        String ip = kubectl(null, "-n", NS, "get", "pods", "-l", "app=order-service", "-o", "jsonpath={.items[0].status.podIP}").trim();
        System.out.println("who reaches order-service's ports (pod " + ip + "):");
        System.out.println("                                    from namespace          :8080/menu   :8081/actuator/health");
        reach("no NetworkPolicy", ip);
        kubectl("""
                apiVersion: networking.k8s.io/v1
                kind: NetworkPolicy
                metadata: {name: order-service-ingress, namespace: %s}
                spec:
                  podSelector: {matchLabels: {app: order-service}}
                  policyTypes: [Ingress]
                  ingress:
                    - ports: [{port: 8080}]
                    - from: [{namespaceSelector: {matchLabels: {team: observability}}}]
                      ports: [{port: 8081}]
                """.formatted(NS), "apply", "-f", "-");
        Thread.sleep(5000);
        reach("NetworkPolicy: 8081 only from monitoring", ip);
        Thread.sleep(30_000);
        int[] s = podState();
        System.out.printf("   30 s after the policy: %d of 1 pods ready, %d restarts (kubelet probes come from the node, not a pod)%n", s[0], s[1]);
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        kubectl(null, "delete", "namespace", "monitoring", "--wait=false");
        System.out.println();
    }

    static void reach(String label, String ip) throws Exception {
        for (String ns : List.of("default", "monitoring")) {
            String name = "fetch-" + System.nanoTime() % 100000;
            kubectl(null, "-n", ns, "run", name, "--image=" + IMAGE, "--image-pull-policy=Never", "--restart=Never", "--command", "--",
                    "java", "-cp", "/app/tools", "Fetch", "http://" + ip + ":8080/menu", "http://" + ip + ":8081/actuator/health");
            String out = "";
            for (int i = 0; i < 60; i++) {
                String phase = kubectl(null, "-n", ns, "get", "pod", name, "-o", "jsonpath={.status.phase}").trim();
                if (phase.equals("Succeeded") || phase.equals("Failed")) {
                    out = kubectl(null, "-n", ns, "logs", name);
                    break;
                }
                Thread.sleep(1000);
            }
            kubectl(null, "-n", ns, "delete", "pod", name, "--wait=false");
            String app = out.lines().filter(l -> l.contains(":8080")).findFirst().map(l -> l.substring(l.lastIndexOf(' ') + 1)).orElse("?");
            String mgmt = out.lines().filter(l -> l.contains(":8081")).findFirst().map(l -> l.substring(l.lastIndexOf(' ') + 1)).orElse("?");
            System.out.printf("   %-40s %-18s %-12s %s%n", label, ns, app, mgmt);
        }
    }
}
