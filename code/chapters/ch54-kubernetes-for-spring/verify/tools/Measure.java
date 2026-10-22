import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 54's experiments on a real Kubernetes cluster (k3s 1.37 here) with menu-service ({@link KubeLab}).
 * <ul>
 *   <li>{@code cpu}: CPU limits against JVM startup and request latency, with the cgroup's throttling counters</li>
 *   <li>{@code config}: how long a ConfigMap change takes to reach an env var, configtree, a watched mount, a subPath</li>
 *   <li>{@code scaling}: scaling out under load, over new connections and over keep-alive connections</li>
 *   <li>{@code disruption}: draining the node with and without a PodDisruptionBudget</li>
 * </ul>
 * System properties: c54.kubectl (command), c54.image.
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c54.kubectl", "kubectl").split(" ");
    static final String IMAGE = System.getProperty("c54.image", "docker.io/foodfleet/menu-service:ch54");
    static final String NS = "ch54";
    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null)).connectTimeout(Duration.ofSeconds(2)).build();

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            switch (mode) {
                case "cpu" -> cpu();
                case "config" -> config();
                case "scaling" -> scaling();
                case "disruption" -> disruption();
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
        return out.lines().filter(l -> !l.contains("DEPRECATION")).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    static void reset() throws Exception {
        kubectl(null, "delete", "namespace", NS, "--ignore-not-found", "--wait=true", "--timeout=120s");
        kubectl(null, "create", "namespace", NS);
        kubectl("""
                apiVersion: v1
                kind: ConfigMap
                metadata: {name: menu-config, namespace: %s}
                data: {discount: "10"}
                """.formatted(NS), "apply", "-f", "-");
    }

    /** menu-service with the given resources block, replicas and readiness period. */
    static String deployment(int replicas, String resources) {
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata: {name: menu-service, namespace: %s}
                spec:
                  replicas: %d
                  selector: {matchLabels: {app: menu-service}}
                  template:
                    metadata: {labels: {app: menu-service}}
                    spec:
                      terminationGracePeriodSeconds: 10
                      containers:
                        - name: app
                          image: %s
                          imagePullPolicy: Never
                          ports: [{containerPort: 8080}]
                          resources: %s
                          env:
                            - {name: POD_NAME, valueFrom: {fieldRef: {fieldPath: metadata.name}}}
                            - {name: POD_NAMESPACE, valueFrom: {fieldRef: {fieldPath: metadata.namespace}}}
                            - {name: NODE_NAME, valueFrom: {fieldRef: {fieldPath: spec.nodeName}}}
                            - {name: CPU_LIMIT_MILLIS, valueFrom: {resourceFieldRef: {resource: limits.cpu, divisor: 1m}}}
                            - {name: MEMORY_LIMIT_BYTES, valueFrom: {resourceFieldRef: {resource: limits.memory}}}
                            - {name: DISCOUNT, valueFrom: {configMapKeyRef: {name: menu-config, key: discount}}}
                          readinessProbe: {httpGet: {path: /menu, port: 8080}, periodSeconds: 1}
                          volumeMounts:
                            - {name: config, mountPath: /etc/menu-config}
                            - {name: config, mountPath: /etc/subpath/discount, subPath: discount}
                      volumes:
                        - {name: config, configMap: {name: menu-config}}
                ---
                apiVersion: v1
                kind: Service
                metadata: {name: menu-service, namespace: %s}
                spec:
                  selector: {app: menu-service}
                  ports: [{port: 80, targetPort: 8080}]
                """.formatted(NS, replicas, IMAGE, resources, NS);
    }

    static List<String[]> pods() throws Exception {
        String out = kubectl(null, "-n", NS, "get", "pods", "-l", "app=menu-service", "-o",
                "jsonpath={range .items[*]}{.metadata.name} {.status.podIP} {.status.containerStatuses[0].ready} {.metadata.deletionTimestamp}{\"\\n\"}{end}");
        List<String[]> r = new ArrayList<>();
        for (String l : out.split("\n")) {
            String[] p = l.trim().split(" ");
            if (p.length >= 3 && !p[0].isEmpty()) {
                r.add(p);
            }
        }
        return r;
    }

    static int ready() throws Exception {
        return (int) pods().stream().filter(p -> p[2].equals("true")).count();
    }

    static void waitReady(int n, int seconds) throws Exception {
        for (int i = 0; i < seconds * 4; i++) {
            if (ready() >= n) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("not ready");
    }

    static String get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static long pct(List<Long> v, double p) {
        List<Long> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(Math.max(0, (int) Math.ceil(p / 100 * s.size()) - 1));
    }

    // ========================================================================================================= cpu

    static void cpu() throws Exception {
        System.out.println("menu-service, 1 replica on a 2-CPU node: time to Ready, then 20 requests/s that each need 5 ms of CPU, for 30 s");
        System.out.println("resources                                ready after   JVM sees   JVM \"started in\"   latency p50 / p99 / max        throttled periods / time");
        for (String[] r : new String[][] {
                {"limit 250m", "{requests: {cpu: 250m, memory: 384Mi}, limits: {cpu: 250m, memory: 384Mi}}"},
                {"limit 500m", "{requests: {cpu: 500m, memory: 384Mi}, limits: {cpu: 500m, memory: 384Mi}}"},
                {"limit 1000m", "{requests: {cpu: 1000m, memory: 384Mi}, limits: {cpu: 1000m, memory: 384Mi}}"},
                {"request 250m, no CPU limit", "{requests: {cpu: 250m, memory: 384Mi}, limits: {memory: 384Mi}}"}}) {
            List<Long> readyMs = new ArrayList<>();
            List<Long> started = new ArrayList<>();
            String jvm = "";
            for (int rep = 0; rep < 3; rep++) {
                reset();
                long t = System.nanoTime();
                kubectl(deployment(1, r[1]), "apply", "-f", "-");
                waitReady(1, 180);
                readyMs.add((System.nanoTime() - t) / 1_000_000);
                String logs = kubectl(null, "-n", NS, "logs", "deploy/menu-service");
                Matcher m = Pattern.compile("started in (\\d+) ms").matcher(logs);
                started.add(m.find() ? Long.parseLong(m.group(1)) : -1);
                Matcher j = Pattern.compile("JVM sees (\\d+) processors").matcher(logs);
                jvm = j.find() ? j.group(1) + " CPU" + (j.group(1).equals("1") ? "" : "s") : "?";
            }
            String ip = pods().getFirst()[1];
            for (int i = 0; i < 200; i++) {
                get("http://" + ip + ":8080/work?ms=5");             // warm up the JIT
            }
            Map<String, Long> before = cpuStat(get("http://" + ip + ":8080/cpustat"));
            List<Long> lat = new ArrayList<>();
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                long start = System.nanoTime();
                for (int i = 0; i < 600; i++) {
                    long due = start + i * 50_000_000L;
                    long wait = due - System.nanoTime();
                    if (wait > 0) {
                        TimeUnit.NANOSECONDS.sleep(wait);
                    }
                    pool.submit(() -> {
                        long s = System.nanoTime();
                        try {
                            get("http://" + ip + ":8080/work?ms=5");
                            synchronized (lat) {
                                lat.add((System.nanoTime() - s) / 1_000_000);
                            }
                        } catch (Exception e) {
                            // counted by its absence
                        }
                    });
                }
            }
            Map<String, Long> after = cpuStat(get("http://" + ip + ":8080/cpustat"));
            long periods = after.getOrDefault("nr_throttled", 0L) - before.getOrDefault("nr_throttled", 0L);
            long throttledMs = (after.getOrDefault("throttled_time", 0L) - before.getOrDefault("throttled_time", 0L)) / 1_000_000
                    + (after.getOrDefault("throttled_usec", 0L) - before.getOrDefault("throttled_usec", 0L)) / 1_000;
            Collections.sort(readyMs);
            Collections.sort(started);
            System.out.printf("%-40s %6.1f s       %-8s %8d ms          %3d / %4d / %4d ms        %5d / %6d ms%n", r[0], readyMs.get(1) / 1000.0, jvm,
                    started.get(1), pct(lat, 50), pct(lat, 99), Collections.max(lat), periods, throttledMs);
        }
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println("(ready after and \"started in\": median of 3 starts)");
        System.out.println();
    }

    static Map<String, Long> cpuStat(String s) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String l : s.split("\n")) {
            String[] p = l.trim().split(" ");
            if (p.length == 2) {
                m.put(p[0], Long.parseLong(p[1]));
            }
        }
        return m;
    }

    // ====================================================================================================== config

    static void config() throws Exception {
        reset();
        kubectl(deployment(1, "{requests: {cpu: 250m, memory: 256Mi}, limits: {memory: 256Mi}}"), "apply", "-f", "-");
        waitReady(1, 180);
        String ip = pods().getFirst()[1];
        System.out.println("the ConfigMap's discount changes; seconds until menu-service sees it, per way of consuming it (3 changes)");
        System.out.println("change          env var    configtree (read at start)   mounted directory, watched   subPath file");
        String[] values = {"15", "20", "25"};
        for (String v : values) {
            String before = get("http://" + ip + ":8080/config");
            long t = System.nanoTime();
            kubectl(null, "-n", NS, "patch", "configmap", "menu-config", "--type", "merge", "-p", "{\"data\":{\"discount\":\"" + v + "\"}}");
            Map<String, Double> seen = new TreeMap<>();
            while ((System.nanoTime() - t) < 150_000_000_000L && seen.size() < 4) {
                String now = get("http://" + ip + ":8080/config");
                for (String part : now.split(" ")) {
                    String[] kv = part.split("=");
                    if (kv[1].equals(v) && !seen.containsKey(kv[0])) {
                        seen.put(kv[0], (System.nanoTime() - t) / 1e9);
                    }
                }
                if (seen.containsKey("watched") && (System.nanoTime() - t) > 70_000_000_000L) {
                    break;           // the others won't change: give them a minute past the mount
                }
                Thread.sleep(250);
            }
            System.out.printf("%-4s -> %-4s    %-10s %-28s %-28s %s%n", before.replaceAll(".*watched=(\\S+).*", "$1"), v, show(seen.get("env")),
                    show(seen.get("once")), show(seen.get("watched")), show(seen.get("subpath")));
        }
        String logs = kubectl(null, "-n", NS, "logs", "deploy/menu-service");
        System.out.println("   menu-service logged " + logs.lines().filter(l -> l.contains("config changed")).count() + " config changes");
        kubectl(null, "-n", NS, "rollout", "restart", "deployment/menu-service");
        kubectl(null, "-n", NS, "rollout", "status", "deployment/menu-service", "--timeout=120s");
        waitReady(1, 60);
        System.out.println("   after a rollout restart: " + get("http://" + pods().stream().filter(p -> p[2].equals("true")).findFirst().orElseThrow()[1] + ":8080/config"));
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println();
    }

    static String show(Double s) {
        return s == null ? "never" : String.format("%.1f s", s);
    }

    // ===================================================================================================== scaling

    static void scaling() throws Exception {
        reset();
        kubectl(deployment(1, "{requests: {cpu: 200m, memory: 256Mi}, limits: {memory: 256Mi}}"), "apply", "-f", "-");
        waitReady(1, 180);
        String cip = kubectl(null, "-n", NS, "get", "svc", "menu-service", "-o", "jsonpath={.spec.clusterIP}").trim();
        System.out.println("1 replica under 50 requests/s through the Service (ClusterIP), half on new connections, half on a pooled keep-alive client;");
        System.out.println("scaled to 3 at 10 s; which pod answered, per 10-second window");
        Map<String, Map<Integer, Map<String, AtomicInteger>>> hits = new ConcurrentHashMap<>();
        AtomicInteger errors = new AtomicInteger();
        AtomicBoolean running = new AtomicBoolean(true);
        HttpClient pooled = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();
        long t0 = System.nanoTime();
        Map<String, Long> readyAt = new LinkedHashMap<>();
        Thread load = Thread.ofPlatform().start(() -> {
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (long i = 0; running.get(); i++) {
                    long due = t0 + i * 20_000_000L;
                    long wait = due - System.nanoTime();
                    if (wait > 0) {
                        TimeUnit.NANOSECONDS.sleep(wait);
                    }
                    boolean fresh = i % 2 == 0;
                    pool.submit(() -> {
                        int window = (int) ((System.nanoTime() - t0) / 10_000_000_000L);
                        try {
                            String pod = fresh ? freshGet(cip, "/whoami") : pooled.send(HttpRequest.newBuilder(URI.create("http://" + cip + "/whoami"))
                                    .timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofString()).body();
                            hits.computeIfAbsent(fresh ? "new connections" : "keep-alive", k -> new ConcurrentHashMap<>())
                                    .computeIfAbsent(window, k -> new ConcurrentHashMap<>()).computeIfAbsent(pod, k -> new AtomicInteger()).incrementAndGet();
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(10_000);
        kubectl(null, "-n", NS, "scale", "deployment/menu-service", "--replicas=3");
        while (System.nanoTime() - t0 < 60_000_000_000L) {
            for (String[] p : pods()) {
                if (p[2].equals("true")) {
                    readyAt.putIfAbsent(p[0], (System.nanoTime() - t0) / 1_000_000);
                }
            }
            Thread.sleep(250);
        }
        running.set(false);
        load.join();
        List<String> names = new ArrayList<>(readyAt.keySet());
        for (String stream : List.of("new connections", "keep-alive")) {
            System.out.println("   " + stream + " (requests per pod; pods in the order they became ready)");
            for (int w = 0; w < 6; w++) {
                Map<String, AtomicInteger> m = hits.getOrDefault(stream, Map.of()).getOrDefault(w, Map.of());
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < names.size(); k++) {
                    AtomicInteger c = m.get(names.get(k));
                    b.append(String.format("  pod %d: %4d", k + 1, c == null ? 0 : c.get()));
                }
                System.out.printf("      %2d-%2d s%s%n", w * 10, w * 10 + 10, b);
            }
        }
        System.out.printf("   pods 2 and 3 ready at %.1f s and %.1f s; errors: %d%n", readyAt.get(names.get(1)) / 1000.0,
                readyAt.get(names.get(2)) / 1000.0, errors.get());
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println();
    }

    static String freshGet(String host, String path) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, 80), 2000);
            s.setSoTimeout(3000);
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: menu-service\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            if (!r.startsWith("HTTP/1.1 200")) {
                throw new IOException(r.isEmpty() ? "empty" : r.substring(0, Math.min(20, r.length())));
            }
            return r.substring(r.indexOf("\r\n\r\n") + 4);
        }
    }

    // ================================================================================================== disruption

    static void disruption() throws Exception {
        String node = kubectl(null, "get", "nodes", "-o", "jsonpath={.items[0].metadata.name}").trim();
        System.out.println("3 replicas; `kubectl drain` of the only node (as in a node upgrade), 60 s timeout; 50 requests/s through the Service");
        for (boolean pdb : new boolean[] {false, true}) {
            reset();
            kubectl(deployment(3, "{requests: {cpu: 100m, memory: 256Mi}, limits: {memory: 256Mi}}"), "apply", "-f", "-");
            if (pdb) {
                kubectl("""
                        apiVersion: policy/v1
                        kind: PodDisruptionBudget
                        metadata: {name: menu-service, namespace: %s}
                        spec:
                          minAvailable: 2
                          selector: {matchLabels: {app: menu-service}}
                        """.formatted(NS), "apply", "-f", "-");
            }
            waitReady(3, 180);
            String cip = kubectl(null, "-n", NS, "get", "svc", "menu-service", "-o", "jsonpath={.spec.clusterIP}").trim();
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            AtomicBoolean running = new AtomicBoolean(true);
            long t0 = System.nanoTime();
            Thread load = Thread.ofPlatform().start(() -> {
                try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (long i = 0; running.get(); i++) {
                        long wait = t0 + i * 20_000_000L - System.nanoTime();
                        if (wait > 0) {
                            TimeUnit.NANOSECONDS.sleep(wait);
                        }
                        pool.submit(() -> {
                            try {
                                freshGet(cip, "/menu");
                                ok.incrementAndGet();
                            } catch (Exception e) {
                                failed.incrementAndGet();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            String drain;
            int readyAtEnd;
            long drainMs;
            try {
                Thread.sleep(5000);
                long t = System.nanoTime();
                drain = kubectl(null, "drain", node, "--ignore-daemonsets", "--delete-emptydir-data", "--pod-selector=app=menu-service",
                        "--timeout=60s");
                drainMs = (System.nanoTime() - t) / 1_000_000;
                readyAtEnd = ready();
            } finally {
                kubectl(null, "uncordon", node);
            }
            long recoverFrom = System.nanoTime();
            waitReady(3, 180);
            double recovered = (System.nanoTime() - recoverFrom) / 1e9;
            Thread.sleep(3000);
            running.set(false);
            load.join();
            long evicted = drain.lines().filter(l -> l.contains("evicted")).count();
            boolean blocked = drain.contains("Cannot evict pod as it would violate the pod's disruption budget");
            System.out.printf("-- %-40s drain %s after %.0f s; pods evicted %d; ready pods during the drain at the end: %d of 3%n",
                    pdb ? "PodDisruptionBudget minAvailable: 2" : "no PodDisruptionBudget",
                    drain.contains("error") || drain.contains("pending nodes to be drained") ? "gave up (timeout)" : "finished",
                    drainMs / 1000.0, evicted, readyAtEnd);
            System.out.printf("   requests: %,d ok, %,d failed; eviction refused by the budget: %s; all 3 ready again %.0f s after uncordon%n", ok.get(),
                    failed.get(), blocked ? "yes" : "no", recovered);
        }
        kubectl(null, "delete", "namespace", NS, "--wait=false");
        System.out.println();
    }
}
