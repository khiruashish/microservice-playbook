import dev.foodfleet.ch58.delivery.CanaryAnalysis;
import dev.foodfleet.ch58.delivery.CanaryAnalysis.Thresholds;
import dev.foodfleet.ch58.delivery.CanaryAnalysis.Verdict;
import dev.foodfleet.ch58.delivery.CanaryAnalysis.Window;
import dev.foodfleet.ch58.gitops.Inventory;
import dev.foodfleet.ch58.gitops.Reconciler;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Chapter 58's experiments on k3s 1.37 with Traefik 3.7 as the Gateway API implementation.
 * <ul>
 *   <li>{@code gitops}: {@link Reconciler} watching a Git repository every 10 s: commit to rollout, drift, deletes, pruning</li>
 *   <li>{@code canary}: an HTTPRoute's weights stepped 10/25/50/100 with {@link CanaryAnalysis} after each step; a good
 *       release, a bad one (5% errors), and the bad one as a plain rolling update</li>
 *   <li>{@code bluegreen}: a Service's selector switched from blue to green and back, under load</li>
 * </ul>
 * System properties: c58.kubectl, c58.kustomize, c58.resources (src/main/resources), c58.work.
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c58.kubectl", "kubectl").split(" ");
    static final String KUSTOMIZE = System.getProperty("c58.kustomize", "kustomize");
    static final Path RES = Path.of(System.getProperty("c58.resources"));
    static final Path WORK = Path.of(System.getProperty("c58.work", "/tmp/c58"));
    static final long T0 = System.nanoTime();

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "gitops" -> gitops();
                case "canary" -> canary();
                case "bluegreen" -> blueGreen();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ===================================================================================================== helpers

    record Out(int code, String text) {
    }

    static Out sh(String stdin, Path dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Out(p.waitFor(), out.lines().filter(l -> !l.contains("DEPRECATION")).collect(Collectors.joining("\n")));
    }

    static Out k(String stdin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(KUBECTL));
        cmd.addAll(Arrays.asList(args));
        return sh(stdin, null, cmd.toArray(String[]::new));
    }

    static String kubectl(String stdin, String... args) throws Exception {
        Out o = k(stdin, args);
        if (o.code() != 0 && !Arrays.asList(args).contains("--ignore-not-found")) {
            throw new IllegalStateException(String.join(" ", args) + ": " + o.text());
        }
        return o.text();
    }

    static Out git(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=FoodFleet CI", "-c", "user.email=ci@foodfleet.local", "-c", "init.defaultBranch=main"));
        cmd.addAll(Arrays.asList(args));
        Out o = sh(null, dir, cmd.toArray(String[]::new));
        if (o.code() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + ": " + o.text());
        }
        return o;
    }

    static double now() {
        return (System.nanoTime() - T0) / 1e9;
    }

    static double waitFor(BooleanSupplier condition, int seconds) throws InterruptedException {
        double start = now();
        while (now() - start < seconds) {
            if (condition.getAsBoolean()) {
                return now() - start;
            }
            Thread.sleep(250);
        }
        return Double.NaN;
    }

    static String jsonpath(String ns, String what, String path) {
        try {
            return k(null, "-n", ns, "get", what, "-o", "jsonpath=" + path, "--ignore-not-found").text().trim();
        } catch (Exception e) {
            return "";
        }
    }

    static void namespace(String ns) throws Exception {
        kubectl(null, "delete", "namespace", ns, "--ignore-not-found", "--wait=true", "--timeout=180s");
        kubectl(null, "create", "namespace", ns);
    }

    // ====================================================================================================== gitops

    /** The controller's side: a clone of the environment repository, kustomize, kubectl, an inventory ConfigMap. */
    static final class ClusterOps implements Reconciler.Ops {
        final Path clone;
        final String path;
        final String ns;

        ClusterOps(Path clone, String path, String ns) {
            this.clone = clone;
            this.path = path;
            this.ns = ns;
        }

        public String fetch() {
            try {
                git(clone, "fetch", "-q", "origin");
                git(clone, "reset", "-q", "--hard", "origin/main");
                return git(clone, "rev-parse", "--short", "HEAD").text().trim();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public String render() {
            try {
                Out o = sh(null, null, KUSTOMIZE, "build", clone.resolve(path).toString());
                if (o.code() != 0) {
                    throw new IllegalStateException(o.text());
                }
                return o.text();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean drifted(String yaml) {
            try {
                // exit 1: differences; the diff is computed by the API server, as a server-side apply would do it
                return k(yaml, "diff", "--server-side", "--field-manager=foodfleet-gitops", "--force-conflicts", "-f", "-").code() == 1;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public void apply(String yaml) {
            try {
                kubectl(yaml, "apply", "--server-side", "--field-manager=foodfleet-gitops", "--force-conflicts", "-f", "-");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public void delete(Inventory.Ref ref) {
            try {
                kubectl(null, "-n", ref.namespace(), "delete", ref.resource(), "--ignore-not-found", "--wait=false");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public Set<Inventory.Ref> loadInventory() {
            String refs = jsonpath(ns, "configmap/gitops-inventory", "{.data.refs}");
            Set<Inventory.Ref> s = new LinkedHashSet<>();
            for (String r : refs.split(",")) {
                if (!r.isBlank()) {
                    s.add(Inventory.Ref.decode(r));
                }
            }
            return s;
        }

        public void saveInventory(Set<Inventory.Ref> refs, String commit) {
            try {
                String data = refs.stream().map(Inventory.Ref::encoded).collect(Collectors.joining(","));
                kubectl("apiVersion: v1\nkind: ConfigMap\nmetadata: {name: gitops-inventory, namespace: " + ns + "}\ndata: {commit: \"" + commit
                        + "\", refs: \"" + data + "\"}\n", "apply", "--server-side", "--field-manager=foodfleet-gitops", "--force-conflicts", "-f", "-");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    record Event(double t, Reconciler.Result result) {
    }

    static void gitops() throws Exception {
        String ns = "gitops";
        int interval = 10;
        System.out.printf("a GitOps reconciler (fetch, kustomize build, server-side apply, prune) runs every %d s against a Git repository%n", interval);
        namespace(ns);
        Path w = WORK.resolve("gitops");
        sh(null, null, "rm", "-rf", w.toString());
        Files.createDirectories(w);
        Path bare = w.resolve("environments.git");
        Path author = w.resolve("author");
        Path controller = w.resolve("controller");
        git(w, "init", "-q", "--bare", bare.toString());
        git(w, "clone", "-q", bare.toString(), author.toString());
        sh(null, null, "cp", "-r", RES.resolve("gitops").toString(), author.resolve("prod").toString());
        git(author, "add", "-A");
        git(author, "commit", "-q", "-m", "menu-service 1.4.0");
        git(author, "push", "-q", "origin", "HEAD:main");
        git(w, "clone", "-q", bare.toString(), controller.toString());
        Reconciler reconciler = new Reconciler(new ClusterOps(controller, "prod/menu-prod", ns), ns, System::currentTimeMillis);
        ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
        AtomicBoolean running = new AtomicBoolean(true);
        double start = now();
        Thread loop = Thread.ofPlatform().daemon().start(() -> {
            while (running.get()) {
                long t = System.nanoTime();
                try {
                    Reconciler.Result r = reconciler.reconcile();
                    if (r.action() != Reconciler.Action.NONE) {
                        events.add(new Event(now(), r));
                    }
                } catch (RuntimeException e) {
                    if (running.get()) {
                        System.out.println("   reconcile failed: " + e.getMessage());
                    }
                }
                long sleep = interval * 1_000_000_000L - (System.nanoTime() - t);
                if (sleep > 0) {
                    try {
                        TimeUnit.NANOSECONDS.sleep(sleep);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });
        double firstReady = waitFor(() -> "2".equals(jsonpath(ns, "deployment/menu-service", "{.status.readyReplicas}")), 120);
        System.out.printf("first sync: menu-service ready %.1f s after the controller started%n%n", firstReady + (now() - start - (now() - start)));
        System.out.println("change                                                   applied after   rolled out after   what the controller did");
        // commits
        List<Double> applied = new ArrayList<>();
        List<Double> rolled = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String v = "1.4." + i;
            Path d = author.resolve("prod/menu-prod/deployment.yaml");
            Files.writeString(d, Files.readString(d).replaceAll("value: \"1\\.4\\.\\d+\"", "value: \"" + v + "\""));
            git(author, "commit", "-q", "-am", "menu-service " + v);
            double t = now();
            git(author, "push", "-q", "origin", "HEAD:main");
            double a = waitFor(() -> jsonpath(ns, "deployment/menu-service", "{.spec.template.spec.containers[0].env[0].value}").equals(v), 60);
            double r = waitFor(() -> rolledOut(ns), 120);
            applied.add(a);
            rolled.add(a + r);
            Thread.sleep(3000 + java.util.concurrent.ThreadLocalRandom.current().nextLong(7000));   // commits don't line up with the loop
        }
        Collections.sort(applied);
        Collections.sort(rolled);
        System.out.printf("%-56s %-15s %-18s %s%n", "5 commits: VERSION 1.4.1 ... 1.4.5", range(applied), range(rolled), "applied the commit");
        // drift in a field Git declares
        kubectl(null, "-n", ns, "scale", "deployment/menu-service", "--replicas=5");
        double t = now();
        double back = waitFor(() -> "2".equals(jsonpath(ns, "deployment/menu-service", "{.spec.replicas}")), 60);
        System.out.printf("%-56s %-15s %-18s %s%n", "kubectl scale --replicas=5 (Git says 2)", secs(back), "-", last(events, t));
        kubectl(null, "-n", ns, "set", "env", "deployment/menu-service", "VERSION=hotfix");
        t = now();
        back = waitFor(() -> jsonpath(ns, "deployment/menu-service", "{.spec.template.spec.containers[0].env[0].value}").equals("1.4.5"), 60);
        System.out.printf("%-56s %-15s %-18s %s%n", "kubectl set env VERSION=hotfix (Git says 1.4.5)", secs(back), "-", last(events, t));
        // drift in a field Git doesn't declare
        kubectl(null, "-n", ns, "set", "env", "deployment/menu-service", "DEBUG=true");
        final double t0debug = now();
        Thread.sleep(3L * interval * 1000);
        String debug = jsonpath(ns, "deployment/menu-service", "{.spec.template.spec.containers[0].env[?(@.name==\"DEBUG\")].value}");
        t = now();
        boolean synced = events.stream().anyMatch(e -> e.t() >= t0debug);
        System.out.printf("%-56s %-15s %-18s %s%n", "kubectl set env DEBUG=true (not in Git)", debug.isEmpty() ? "removed" : "kept, " + 3 * interval + " s later", "-",
                synced ? last(events, t0debug) : "nothing (no change detected)");
        // deleted by hand
        kubectl(null, "-n", ns, "delete", "deployment", "menu-service", "--wait=false");
        t = now();
        Thread.sleep(1000);
        double recreated = waitFor(() -> "2".equals(jsonpath(ns, "deployment/menu-service", "{.status.readyReplicas}")), 60);
        System.out.printf("%-56s %-15s %-18s %s%n", "kubectl delete deployment menu-service", "-", secs(recreated + 1), last(events, t));
        // deleted from Git
        git(author, "rm", "-q", "prod/menu-prod/flags.yaml");
        Path kz = author.resolve("prod/menu-prod/kustomization.yaml");
        Files.writeString(kz, Files.readString(kz).replace("  - flags.yaml\n", ""));
        git(author, "commit", "-q", "-am", "remove menu-flags");
        t = now();
        git(author, "push", "-q", "origin", "HEAD:main");
        double gone = waitFor(() -> jsonpath(ns, "configmap/menu-flags", "{.metadata.name}").isEmpty(), 60);
        System.out.printf("%-56s %-15s %-18s %s%n", "git rm flags.yaml (the menu-flags ConfigMap)", secs(gone), "-", last(events, t));
        running.set(false);
        loop.interrupt();
        loop.join();
        System.out.println();
        kubectl(null, "delete", "namespace", ns, "--wait=false");
    }

    static boolean rolledOut(String ns) {
        String s = jsonpath(ns, "deployment/menu-service", "{.status.updatedReplicas} {.status.readyReplicas} {.status.replicas} {.metadata.generation} {.status.observedGeneration}");
        String[] p = s.split(" ");
        return p.length == 5 && p[0].equals("2") && p[1].equals("2") && p[2].equals("2") && p[3].equals(p[4]);
    }

    /** What the controller recorded for the first sync after {@code after}: waits for it, since its effects show before it returns. */
    static String last(ConcurrentLinkedQueue<Event> events, double after) throws InterruptedException {
        waitFor(() -> events.stream().anyMatch(e -> e.t() >= after), 15);
        return events.stream().filter(e -> e.t() >= after).findFirst().map(e -> e.result().action().name().toLowerCase().replace('_', ' ')
                + (e.result().pruned().isEmpty() ? "" : ", pruned " + e.result().pruned())).orElse("nothing (no change detected)");
    }

    static String secs(double s) {
        return Double.isNaN(s) ? "not within 60 s" : String.format("%.1f s", s);
    }

    static String range(List<Double> sorted) {
        return String.format("%.1f-%.1f s", sorted.getFirst(), sorted.getLast());
    }

    // ====================================================================================================== traffic

    record Hit(double t, String version, int status, double ms) {
    }

    /** 100 requests/s, each on a new connection, with a Host header; records which version answered. */
    static final class Load {
        final ConcurrentLinkedQueue<Hit> hits = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean(true);
        Thread thread;

        static Load start(String ip, int port, String host, int rps) {
            return start(ip, port, host, rps, false);
        }

        /** keepAlive: a pooled java.net.http client instead of a new connection per request. */
        static Load start(String ip, int port, String host, int rps, boolean keepAlive) {
            Load l = new Load();
            java.net.http.HttpClient client = keepAlive ? java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build() : null;
            long t0 = System.nanoTime();
            l.thread = Thread.ofPlatform().daemon().start(() -> {
                try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (long i = 0; l.running.get(); i++) {
                        long wait = t0 + i * 1_000_000_000L / rps - System.nanoTime();
                        if (wait > 0) {
                            TimeUnit.NANOSECONDS.sleep(wait);
                        }
                        pool.submit(() -> l.hits.add(keepAlive ? pooled(client, ip, port) : hit(ip, port, host)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return l;
        }

        void stop() throws InterruptedException {
            running.set(false);
            thread.join();
        }

        List<Hit> between(double from, double to) {
            return hits.stream().filter(h -> h.t() >= from && h.t() < to).toList();
        }
    }

    static Hit hit(String ip, int port, String host) {
        double t = now();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 2000);
            s.setSoTimeout(5000);
            s.getOutputStream().write(("GET /menu HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            int status = Integer.parseInt(r.substring(9, 12));
            int v = r.indexOf("version=");
            return new Hit(t, v < 0 ? "-" : r.substring(v + 8).split("[ \r\n]")[0], status, (now() - t) * 1000);
        } catch (Exception e) {
            return new Hit(t, "-", 0, (now() - t) * 1000);
        }
    }

    static Hit pooled(java.net.http.HttpClient client, String ip, int port) {
        double t = now();
        try {
            var r = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://" + ip + ":" + port + "/menu"))
                    .timeout(java.time.Duration.ofSeconds(5)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            int v = r.body().indexOf("version=");
            return new Hit(t, v < 0 ? "-" : r.body().substring(v + 8).split(" ")[0], r.statusCode(), (now() - t) * 1000);
        } catch (Exception e) {
            return new Hit(t, "-", 0, (now() - t) * 1000);
        }
    }

    static void deployment(String ns, String name, String labels, int replicas, String version, double errorRate) throws Exception {
        kubectl("""
                apiVersion: apps/v1
                kind: Deployment
                metadata: {name: %s}
                spec:
                  replicas: %d
                  selector: {matchLabels: {%s}}
                  template:
                    metadata: {labels: {%s}}
                    spec:
                      terminationGracePeriodSeconds: 5
                      containers:
                        - name: app
                          image: foodfleet/menu-service:ch58
                          imagePullPolicy: Never
                          env: [{name: VERSION, value: "%s"}, {name: ERROR_RATE, value: "%s"}]
                          resources: {requests: {cpu: 50m, memory: 96Mi}, limits: {memory: 96Mi}}
                          readinessProbe: {httpGet: {path: /healthz, port: 8080}, periodSeconds: 1}
                """.formatted(name, replicas, labels, labels, version, errorRate), "apply", "-n", ns, "-f", "-");
    }

    static void service(String ns, String name, String selector) throws Exception {
        kubectl("apiVersion: v1\nkind: Service\nmetadata: {name: " + name + "}\nspec:\n  selector: {" + selector + "}\n  ports: [{port: 80, targetPort: 8080}]\n",
                "apply", "-n", ns, "-f", "-");
    }

    static String gatewayIp() throws Exception {
        String ip = jsonpath("kube-system", "service/traefik", "{.spec.clusterIP}");
        if (ip.isEmpty() || jsonpath("kube-system", "gateway/traefik-gateway", "{.metadata.name}").isEmpty()) {
            throw new IllegalStateException("Traefik with its Gateway API provider isn't running: see run.sh");
        }
        return ip;
    }

    // ====================================================================================================== canary

    static final Thresholds THRESHOLDS = new Thresholds(0.01, 0.005, 250, 200);
    static final int[] STEPS = {10, 25, 50, 100};
    static final int ANALYSIS_SECONDS = 30;

    static void canary() throws Exception {
        String ns = "delivery";
        String gw = gatewayIp();
        System.out.printf("menu-service 1.4.0 (3 pods) behind Traefik's Gateway; 100 requests/s. Canary steps %s%% of traffic, %d s of analysis each:%n",
                Arrays.toString(STEPS), ANALYSIS_SECONDS);
        System.out.printf("abort above %.0f%% errors, %.1f points over stable, or p99 over %.0f ms; at least %d canary requests to promote a step%n",
                THRESHOLDS.maxErrorRate() * 100, THRESHOLDS.maxErrorRateDelta() * 100, THRESHOLDS.maxP99Millis(), THRESHOLDS.minRequests());
        System.out.println("release                                      outcome                          took     served by 1.4.1   failed requests");
        for (Object[] c : new Object[][] {{"1.4.1, healthy, as a canary", 0.0, true}, {"1.4.1, 5% of requests fail, as a canary", 0.05, true},
                {"1.4.1, 5% fail, rolling update; undone after 2 min", 0.05, false}}) {
            namespace(ns);
            double errorRate = (double) c[1];
            deployment(ns, "menu-stable", "app: menu, track: stable", 3, "1.4.0", 0);
            deployment(ns, "menu-canary", "app: menu, track: canary", 1, "1.4.1", errorRate);
            service(ns, "menu-stable", "app: menu, track: stable");
            service(ns, "menu-canary", "app: menu, track: canary");
            route(ns, 100, 0);
            kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-stable", "--timeout=120s");
            kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-canary", "--timeout=120s");
            waitFor(() -> hit(gw, 80, "menu.foodfleet.local").status() == 200, 60);
            Load load = Load.start(gw, 80, "menu.foodfleet.local", 100);
            Thread.sleep(10_000);
            double start = now();
            String outcome;
            if ((boolean) c[2]) {
                outcome = "promoted to 100%";
                for (int step : STEPS) {
                    route(ns, 100 - step, step);
                    double from = now();
                    Verdict v;
                    Window last = null;
                    while (true) {
                        Thread.sleep(1000);
                        // only traffic since this step's weights took effect, at most one analysis window
                        List<Hit> w = load.between(Math.max(from + 2, now() - ANALYSIS_SECONDS), now());
                        last = window(w, "1.4.1");
                        v = CanaryAnalysis.judge(last, window(w, "1.4.0"), THRESHOLDS);
                        if (v == Verdict.ABORT || (v == Verdict.PROMOTE && now() - from >= ANALYSIS_SECONDS)) {
                            break;
                        }
                        if (now() - from > 120) {
                            v = Verdict.ABORT;
                            break;
                        }
                    }
                    if (v == Verdict.ABORT) {
                        route(ns, 100, 0);
                        outcome = String.format("aborted at %d%% after %.0f s", step, now() - from);
                        System.out.printf("    abort: canary %d requests, %d errors, p99 %.0f ms%n", last.requests(), last.errors(), last.p99Millis());
                        break;
                    }
                }
            } else {
                // no canary: the stable Deployment itself is updated to the bad release, then undone after two minutes
                kubectl(null, "-n", ns, "set", "env", "deployment/menu-stable", "VERSION=1.4.1", "ERROR_RATE=" + errorRate);
                Thread.sleep(120_000);
                kubectl(null, "-n", ns, "rollout", "undo", "deployment/menu-stable");
                kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-stable", "--timeout=120s");
                outcome = "rolled out, then undone";
            }
            double took = now() - start;
            Thread.sleep(5000);
            load.stop();
            List<Hit> all = load.between(start, now());
            long canary = all.stream().filter(h -> h.version().equals("1.4.1")).count();
            long failed = all.stream().filter(h -> h.status() != 200).count();
            System.out.printf("%-44s %-32s %4.0f s   %6d of %-6d %6d%n", c[0], outcome, took, canary, all.size(), failed);
            kubectl(null, "delete", "namespace", ns, "--wait=false");
        }
        System.out.println();
    }

    /** One version's requests, errors and p99. Errors carry the version too: the app names itself in a 500. */
    static Window window(List<Hit> hits, String version) {
        List<Hit> v = hits.stream().filter(h -> h.version().equals(version)).toList();
        long errors = v.stream().filter(h -> h.status() >= 500).count();
        List<Double> ms = v.stream().map(Hit::ms).sorted().toList();
        return new Window(v.size(), errors, ms.isEmpty() ? 0 : ms.get(Math.min(ms.size() - 1, (int) Math.ceil(0.99 * ms.size()) - 1)));
    }

    static void route(String ns, int stable, int canary) throws Exception {
        String r = Files.readString(RES.resolve("delivery/httproute-canary.yaml"))
                .replace("{name: menu-stable, port: 80, weight: 100}", "{name: menu-stable, port: 80, weight: " + stable + "}")
                .replace("{name: menu-canary, port: 80, weight: 0}", "{name: menu-canary, port: 80, weight: " + canary + "}");
        kubectl(r, "apply", "--server-side", "--field-manager=rollout-controller", "-n", ns, "-f", "-");
    }

    // =================================================================================================== bluegreen

    static void blueGreen() throws Exception {
        String ns = "bluegreen";
        String gw = gatewayIp();
        namespace(ns);
        deployment(ns, "menu-blue", "app: menu, slot: blue", 3, "1.4.0", 0);
        deployment(ns, "menu-green", "app: menu, slot: green", 3, "1.4.1", 0);
        service(ns, "menu", "app: menu, slot: blue");
        kubectl(Files.readString(RES.resolve("delivery/httproute-canary.yaml")).replace("name: menu\n", "name: menu-bg\n")
                .replace("        - {name: menu-stable, port: 80, weight: 100}\n        - {name: menu-canary, port: 80, weight: 0}", "        - {name: menu, port: 80}")
                .replace("menu.foodfleet.local", "bluegreen.foodfleet.local"), "apply", "-n", ns, "-f", "-");
        kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-blue", "--timeout=120s");
        kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-green", "--timeout=120s");
        String svc = jsonpath(ns, "service/menu", "{.spec.clusterIP}");
        waitFor(() -> hit(gw, 80, "bluegreen.foodfleet.local").status() == 200, 60);
        System.out.println("blue (1.4.0) and green (1.4.1), 3 pods each, both running; the Service's selector is switched under 100 requests/s per path");
        System.out.println("switch          path                        all answers from the new slot after   old-slot answers   failed");
        for (String[] sw : new String[][] {{"blue -> green", "green", "1.4.1"}, {"green -> blue", "blue", "1.4.0"}}) {
            Load direct = Load.start(svc, 80, "menu", 100);
            Load pooled = Load.start(svc, 80, "menu", 100, true);
            Load viaGateway = Load.start(gw, 80, "bluegreen.foodfleet.local", 100);
            Thread.sleep(5000);
            double t = now();
            kubectl("{\"spec\":{\"selector\":{\"app\":\"menu\",\"slot\":\"" + sw[1] + "\"}}}", "-n", ns, "patch", "service", "menu", "--type=merge", "--patch-file=/dev/stdin");
            Thread.sleep(15_000);
            direct.stop();
            pooled.stop();
            viaGateway.stop();
            for (Object[] p : new Object[][] {{"Service, new connections", direct}, {"Service, keep-alive pool", pooled}, {"Gateway (Traefik)", viaGateway}}) {
                List<Hit> after = ((Load) p[1]).between(t, now());
                double lastOld = after.stream().filter(h -> !h.version().equals(sw[2]) && h.status() == 200).mapToDouble(Hit::t).max().orElse(t);
                long mixed = after.stream().filter(h -> h.t() <= lastOld && !h.version().equals(sw[2])).count();
                long failed = ((Load) p[1]).hits.stream().filter(h -> h.status() != 200).count();
                boolean stillOld = lastOld > now() - 16;
                System.out.printf("%-15s %-27s %-37s %-15d %d%n", sw[0], p[0], stillOld && lastOld - t > 10 ? "never: still old after 15 s" : String.format("%.1f s", lastOld - t),
                        mixed, failed);
            }
        }
        System.out.println();
        kubectl(null, "delete", "namespace", ns, "--wait=false");
    }

}
