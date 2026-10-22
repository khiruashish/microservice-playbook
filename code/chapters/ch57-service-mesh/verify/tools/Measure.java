import dev.foodfleet.ch57.mesh.Sidecar;
import dev.foodfleet.ch57.mesh.Sidecar.Outlier;
import dev.foodfleet.ch57.mesh.Sidecar.Retry;
import dev.foodfleet.ch57.mesh.Sidecar.Tls;
import dev.foodfleet.ch57.mesh.Sidecar.Upstream;
import dev.foodfleet.ch57.mesh.SpiffeId;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 57's experiments: Envoy 1.39.4 sidecars (native sidecar containers, static configuration from
 * {@link Sidecar}) in front of menu-service and its caller on k3s 1.37. No control plane: the configuration a mesh
 * would push is written by hand, so each effect can be switched on and off.
 * <ul>
 *   <li>{@code balance}: a keep-alive client during a scale-out from 1 to 3 pods, through kube-proxy and through the sidecar</li>
 *   <li>{@code identity}: who can reach menu-service: plaintext, TLS without a certificate, the wrong identity, the wrong CA</li>
 *   <li>{@code resilience}: one of three pods answers 503; retries and outlier detection, alone and together</li>
 *   <li>{@code overhead}: latency through zero, one and two proxies, plaintext and mTLS; the sidecar's memory and CPU</li>
 * </ul>
 * System properties: c57.kubectl, c57.certs (the directory gen-certs made).
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c57.kubectl", "kubectl").split(" ");
    static final Path CERTS = Path.of(System.getProperty("c57.certs"));
    static final String NS = "mesh";
    static final String TD = "foodfleet.local";
    static final SpiffeId MENU = new SpiffeId(TD, NS, "menu-service");
    static final SpiffeId GATEWAY = new SpiffeId(TD, NS, "gateway");
    static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("configs")) {
            configs(Path.of(args[1]));
            return;
        }
        setup();
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "balance" -> balance();
                case "identity" -> identity();
                case "resilience" -> resilience();
                case "overhead" -> overhead();
                default -> throw new IllegalArgumentException(mode);
            }
        }
        kubectl(null, "delete", "namespace", NS, "--wait=false");
    }

    // ===================================================================================================== helpers

    static String kubectl(String stdin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(KUBECTL));
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        out = out.lines().filter(l -> !l.contains("DEPRECATION")).reduce((a, b) -> a + "\n" + b).orElse("");
        if (code != 0 && !Arrays.asList(args).contains("--ignore-not-found")) {
            throw new IllegalStateException(String.join(" ", args) + ": " + out);
        }
        return out;
    }

    static String get(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(300)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static void setup() throws Exception {
        kubectl(null, "delete", "namespace", NS, "--ignore-not-found", "--wait=true", "--timeout=180s");
        kubectl(null, "create", "namespace", NS);
        Path c = CERTS;
        kubectl(null, "-n", NS, "create", "secret", "generic", "certs-menu", "--from-file=tls.crt=" + c.resolve("menu.crt"),
                "--from-file=tls.key=" + c.resolve("menu.key"), "--from-file=ca.crt=" + c.resolve("ca.crt"));
        kubectl(null, "-n", NS, "create", "secret", "generic", "certs-gateway", "--from-file=tls.crt=" + c.resolve("gateway.crt"),
                "--from-file=tls.key=" + c.resolve("gateway.key"), "--from-file=ca.crt=" + c.resolve("ca.crt"));
        kubectl(null, "-n", NS, "create", "secret", "generic", "probe-keystores", "--from-file=gateway.p12=" + c.resolve("gateway.p12"),
                "--from-file=reporting.p12=" + c.resolve("reporting.p12"), "--from-file=rogue.p12=" + c.resolve("rogue-gateway.p12"),
                "--from-file=ca.crt=" + c.resolve("ca.crt"));
    }

    static final Tls MENU_TLS = new Tls("/etc/mesh-certs/tls.crt", "/etc/mesh-certs/tls.key", "/etc/mesh-certs/ca.crt", List.of(GATEWAY));
    static final Tls GATEWAY_TLS = new Tls("/etc/mesh-certs/tls.crt", "/etc/mesh-certs/tls.key", "/etc/mesh-certs/ca.crt", List.of(MENU));

    /** A menu-service Deployment; {@code inbound} is its sidecar's configuration, or null for no sidecar. */
    static void menu(String name, int replicas, String inbound, String bind, boolean fail) throws Exception {
        String sidecar = inbound == null ? "" : sidecar(name, "certs-menu", true);
        String probe = bind.equals("127.0.0.1") ? "" : "          readinessProbe: {httpGet: {path: /healthz, port: 8080}, periodSeconds: 1}\n";
        kubectl(configMap(name, inbound) + """
                apiVersion: apps/v1
                kind: Deployment
                metadata: {name: %s}
                spec:
                  replicas: %d
                  selector: {matchLabels: {app: menu-service, variant: %s}}
                  template:
                    metadata: {labels: {app: menu-service, variant: %s}}
                    spec:
                      terminationGracePeriodSeconds: 2
                %s      containers:
                        - name: app
                          image: foodfleet/mesh:ch57
                          imagePullPolicy: Never
                          command: [java, -XX:MaxRAMPercentage=75, -cp, /app, MeshLab, serve]
                          env:
                            - {name: POD_NAME, valueFrom: {fieldRef: {fieldPath: metadata.name}}}
                            - {name: BIND, value: "%s"}
                            - {name: FAIL, value: "%s"}
                          resources: {requests: {cpu: 100m, memory: 128Mi}, limits: {memory: 128Mi}}
                %s""".formatted(name, replicas, name, name, sidecar, bind, fail, probe), "apply", "-n", NS, "-f", "-");
    }

    /** A caller: MeshLab in call mode, with an outbound sidecar if {@code outbound} isn't null. */
    static void caller(String name, String outbound) throws Exception {
        String sidecar = outbound == null ? "" : sidecar(name, "certs-gateway", false);
        kubectl(configMap(name, outbound) + """
                apiVersion: apps/v1
                kind: Deployment
                metadata: {name: %s}
                spec:
                  replicas: 1
                  selector: {matchLabels: {app: %s}}
                  template:
                    metadata: {labels: {app: %s}}
                    spec:
                      terminationGracePeriodSeconds: 2
                %s      containers:
                        - name: app
                          image: foodfleet/mesh:ch57
                          imagePullPolicy: Never
                          command: [java, -XX:MaxRAMPercentage=75, -cp, /app, MeshLab, call]
                          volumeMounts: [{name: keystores, mountPath: /certs}]
                          readinessProbe: {tcpSocket: {port: 9090}, periodSeconds: 1}
                          resources: {requests: {cpu: 200m, memory: 256Mi}, limits: {memory: 256Mi}}
                      volumes:
                        - {name: keystores, secret: {secretName: probe-keystores}}
                        - {name: envoy, configMap: {name: %s-envoy, optional: true}}
                        - {name: mesh-certs, secret: {secretName: certs-gateway}}
                """.formatted(name, name, name, sidecar, name), "apply", "-n", NS, "-f", "-");
    }

    static String configMap(String name, String envoyYaml) {
        if (envoyYaml == null) {
            return "";
        }
        String indented = envoyYaml.lines().map(l -> "    " + l).reduce((a, b) -> a + "\n" + b).orElse("");
        return "apiVersion: v1\nkind: ConfigMap\nmetadata: {name: " + name + "-envoy}\ndata:\n  envoy.yaml: |\n" + indented + "\n---\n";
    }

    /** Envoy as a native sidecar: an init container that keeps running, started before the app and stopped after it. */
    static String sidecar(String name, String certs, boolean withVolumes) {
        String container = """
                      initContainers:
                        - name: envoy
                          image: foodfleet/mesh:ch57
                          imagePullPolicy: Never
                          restartPolicy: Always
                          command: [envoy, -c, /etc/envoy/envoy.yaml, --concurrency, "1", -l, warning]
                          volumeMounts: [{name: envoy, mountPath: /etc/envoy}, {name: mesh-certs, mountPath: /etc/mesh-certs}]
                          readinessProbe: {httpGet: {path: /ready, port: 15000}, periodSeconds: 1}
                          resources: {requests: {cpu: 100m, memory: 64Mi}, limits: {memory: 128Mi}}
                """;
        String volumes = """
                      volumes:
                        - {name: envoy, configMap: {name: %s-envoy}}
                        - {name: mesh-certs, secret: {secretName: %s}}
                """.formatted(name, certs);
        return withVolumes ? container + volumes : container;
    }

    static void service(String name, String variant, int port, int targetPort, boolean headless) throws Exception {
        kubectl("""
                apiVersion: v1
                kind: Service
                metadata: {name: %s}
                spec:
                  %s
                  selector: {app: menu-service%s}
                  ports: [{port: %d, targetPort: %d}]
                """.formatted(name, headless ? "clusterIP: None" : "type: ClusterIP", variant == null ? "" : ", variant: " + variant, port, targetPort),
                "apply", "-n", NS, "-f", "-");
    }

    static String ready(String deployment) throws Exception {
        kubectl(null, "-n", NS, "rollout", "status", "deployment/" + deployment, "--timeout=180s");
        Thread.sleep(3000);
        return podIp(deployment);
    }

    /** A Running pod's IP: menu Deployments' pods carry variant=<name>, callers' app=<name>. */
    static String podIp(String deployment) throws Exception {
        String selector = deployment.startsWith("menu") ? "variant=" + deployment : "app=" + deployment;
        return kubectl(null, "-n", NS, "get", "pods", "-l", selector, "--field-selector=status.phase=Running", "-o", "jsonpath={.items[0].status.podIP}").trim();
    }

    static void clean() throws Exception {
        kubectl(null, "-n", NS, "delete", "deployment,service,configmap", "--all", "--wait=true", "--timeout=120s");
        Thread.sleep(3000);
    }

    static String run(String callerIp, String url, int rps, int seconds, boolean keepAlive) throws Exception {
        return get("http://" + callerIp + ":9090/run?url=" + java.net.URLEncoder.encode(url, StandardCharsets.UTF_8) + "&rps=" + rps + "&seconds=" + seconds
                + "&keepalive=" + keepAlive);
    }

    static String field(String result, String name) {
        Matcher m = Pattern.compile(name + " ([0-9.NaN]+)").matcher(result);
        return m.find() ? m.group(1) : "?";
    }

    static long stat(String ip, String name) throws Exception {
        String s = get("http://" + ip + ":15000/stats?filter=" + name);
        Matcher m = Pattern.compile(Pattern.quote(name) + ": (\\d+)").matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    /** Every kind of sidecar configuration the experiments use, with local certificate paths, for envoy --mode validate. */
    static void configs(Path dir) throws IOException {
        java.nio.file.Files.createDirectories(dir);
        String c = CERTS.toAbsolutePath() + "/";
        Tls menu = new Tls(c + "menu.crt", c + "menu.key", c + "ca.crt", List.of(GATEWAY));
        Tls gateway = new Tls(c + "gateway.crt", c + "gateway.key", c + "ca.crt", List.of(MENU));
        String dns = "menu-headless." + NS + ".svc.cluster.local";
        java.util.Map<String, String> all = new java.util.TreeMap<>();
        all.put("inbound-mtls", Sidecar.inbound(15006, 8080, menu));
        all.put("inbound-plain", Sidecar.inbound(15006, 8080, null));
        all.put("outbound-plain", Sidecar.outbound(15001, new Upstream("menu-service", dns, 8080, null, null, null)));
        all.put("outbound-mtls", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, null, null, gateway)));
        all.put("outbound-retry-outlier", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, new Retry(2, Duration.ofMillis(500)),
                new Outlier(5, Duration.ofSeconds(2), Duration.ofSeconds(30), 50), gateway)));
        for (var e : all.entrySet()) {
            java.nio.file.Files.writeString(dir.resolve(e.getKey() + ".yaml"), e.getValue());
        }
    }

    // ===================================================================================================== balance

    static void balance() throws Exception {
        System.out.println("a keep-alive client (java.net.http, pooled) sends 100 requests/s to menu-service for 60 s; at 10 s it's scaled from 1 to 3 pods.");
        System.out.println("requests per pod in each 10 s window, one column per pod that answered");
        clean();
        menu("menu", 1, Sidecar.inbound(15006, 8080, MENU_TLS), "0.0.0.0", false);
        service("menu-service", null, 80, 8080, false);
        service("menu-headless", null, 15006, 15006, true);
        caller("caller", Sidecar.outbound(15001, new Upstream("menu-service", "menu-headless." + NS + ".svc.cluster.local", 15006, null, null, GATEWAY_TLS)));
        ready("menu");
        String caller = ready("caller");
        for (String[] path : new String[][] {{"through kube-proxy (Service ClusterIP)", "http://menu-service." + NS + ".svc.cluster.local/menu"},
                {"through the caller's sidecar (Envoy, per-request round robin, mTLS)", "http://127.0.0.1:15001/menu"}}) {
            kubectl(null, "-n", NS, "scale", "deployment/menu", "--replicas=1");
            kubectl(null, "-n", NS, "rollout", "status", "deployment/menu", "--timeout=120s");
            Thread.sleep(8000);
            CompletableFuture<String> r = CompletableFuture.supplyAsync(() -> {
                try {
                    return run(caller, path[1], 100, 60, true);
                } catch (Exception e) {
                    return "error " + e;
                }
            });
            Thread.sleep(10_000);
            long t = System.nanoTime();
            kubectl(null, "-n", NS, "scale", "deployment/menu", "--replicas=3");
            kubectl(null, "-n", NS, "rollout", "status", "deployment/menu", "--timeout=120s");
            double readyAt = 10 + (System.nanoTime() - t) / 1e9;
            String out = r.get();
            System.out.printf("%s (new pods ready at %.0f s):%n", path[0], readyAt);
            out.lines().filter(l -> l.startsWith("window")).forEach(l -> {
                String[] p = l.split(" ");
                System.out.printf("   %2s-%2d s   %s%n", p[1], Integer.parseInt(p[1]) + 10, String.join(" / ", Arrays.copyOfRange(p, 2, p.length)));
            });
            System.out.printf("   failed %s, p50 %s ms, p99 %s ms%n", field(out, "failed"), field(out, "p50"), field(out, "p99"));
        }
        System.out.println();
    }

    // ==================================================================================================== identity

    static void identity() throws Exception {
        System.out.println("menu-service's sidecar requires mTLS from the mesh CA and admits only " + GATEWAY + ".");
        clean();
        menu("menu", 1, Sidecar.inbound(15006, 8080, MENU_TLS), "127.0.0.1", false);
        menu("menu-open", 1, Sidecar.inbound(15006, 8080, MENU_TLS), "0.0.0.0", false);
        service("menu-headless", "menu", 15006, 15006, true);
        caller("caller", Sidecar.outbound(15001, new Upstream("menu-service", "menu-headless." + NS + ".svc.cluster.local", 15006, null, null, GATEWAY_TLS)));
        kubectl(null, "-n", NS, "rollout", "status", "deployment/menu", "--timeout=180s");
        String open = ready("menu-open");
        String caller = ready("caller");
        String menu = kubectl(null, "-n", NS, "get", "pods", "-l", "variant=menu", "-o", "jsonpath={.items[0].status.podIP}").trim();
        Thread.sleep(5000);
        System.out.println("request                                                                result");
        String[][] probes = {
                {"plain HTTP to the sidecar port (15006)", "plain", menu, "15006", ""},
                {"TLS without a client certificate", "tls-nocert", menu, "15006", ""},
                {"TLS, mesh CA, identity sa/reporting (not allowed)", "tls-cert", menu, "15006", "/certs/reporting.p12"},
                {"TLS, identity sa/gateway, signed by another CA", "tls-cert", menu, "15006", "/certs/rogue.p12"},
                {"TLS, mesh CA, identity sa/gateway", "tls-cert", menu, "15006", "/certs/gateway.p12"},
                {"through the caller's sidecar", "plain", "127.0.0.1", "15001", ""},
                {"plain HTTP to the app port (8080), app bound to 127.0.0.1", "plain", menu, "8080", ""},
                {"plain HTTP to the app port (8080), app bound to 0.0.0.0", "plain", open, "8080", ""}};
        for (String[] p : probes) {
            String r = get("http://" + caller + ":9090/probe?mode=" + p[1] + "&host=" + p[2] + "&port=" + p[3] + "&keystore=" + p[4]);
            System.out.printf("%-70s %s%n", p[0], r.replaceAll("pod=menu-[a-z0-9-]+", "pod=menu-...").strip());
        }
        get("http://" + caller + ":9090/handshakes?host=" + open + "&plainPort=8080&tlsPort=15006&keystore=/certs/gateway.p12&n=100");   // warm-up
        String h = get("http://" + caller + ":9090/handshakes?host=" + open + "&plainPort=8080&tlsPort=15006&keystore=/certs/gateway.p12&n=200");
        System.out.println("a request on a new connection (200 each, from the caller pod): " + h + " ms");
        System.out.println();
    }

    // ================================================================================================== resilience

    static void resilience() throws Exception {
        System.out.println("menu-service: 2 good pods and 1 that answers every request with 503. 50 requests/s for 60 s.");
        clean();
        menu("menu-good", 2, Sidecar.inbound(15006, 8080, MENU_TLS), "0.0.0.0", false);
        menu("menu-bad", 1, Sidecar.inbound(15006, 8080, MENU_TLS), "0.0.0.0", true);
        service("menu-service", null, 80, 8080, false);
        service("menu-headless", null, 15006, 15006, true);
        String dns = "menu-headless." + NS + ".svc.cluster.local";
        Retry retry = new Retry(2, Duration.ofMillis(500));
        Outlier outlier = new Outlier(5, Duration.ofSeconds(2), Duration.ofSeconds(30), 50);
        String[][] variants = {
                {"kube-proxy, a new connection per request (no mesh)", null},
                {"sidecar, no retries, no outlier detection", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, null, null, GATEWAY_TLS))},
                {"sidecar, 2 retries on another pod", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, retry, null, GATEWAY_TLS))},
                {"sidecar, outlier detection (5 x 5xx: eject 30 s)", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, null, outlier, GATEWAY_TLS))},
                {"  ... with Envoy's default max_ejection_percent (10%)", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, null,
                        new Outlier(5, Duration.ofSeconds(2), Duration.ofSeconds(30), 10), GATEWAY_TLS))},
                {"sidecar, both", Sidecar.outbound(15001, new Upstream("menu-service", dns, 15006, retry, outlier, GATEWAY_TLS))}};
        ready("menu-good");
        ready("menu-bad");
        System.out.println("caller                                              failed     retries  ejections  p50 / p99 (ms)   failures by 10 s window");
        for (int i = 0; i < variants.length; i++) {
            String name = "caller-" + i;
            caller(name, variants[i][1]);
            String ip = ready(name);
            boolean mesh = variants[i][1] != null;
            String out = run(ip, mesh ? "http://127.0.0.1:15001/menu" : "http://menu-service." + NS + ".svc.cluster.local/menu", 50, 60, mesh);
            String retries = mesh ? String.valueOf(stat(ip, "cluster.menu-service.upstream_rq_retry")) : "-";
            long ej = mesh ? stat(ip, "cluster.menu-service.outlier_detection.ejections_enforced_total") : -1;
            String ejections = ej < 0 ? "-" : String.valueOf(ej);
            List<String> windows = new ArrayList<>();
            out.lines().filter(l -> l.startsWith("window")).forEach(l -> {
                String[] p = l.split(" ");
                int ok = Arrays.stream(p, 2, p.length).mapToInt(Integer::parseInt).sum();
                windows.add(String.valueOf(500 - ok));
            });
            System.out.printf("%-51s %4s/%-5s %-8s %-10s %5s / %-7s  %s%n", variants[i][0], field(out, "failed"), field(out, "total"), retries, ejections,
                    field(out, "p50"), field(out, "p99"), String.join(" ", windows));
            kubectl(null, "-n", NS, "delete", "deployment", name, "--wait=false");
        }
        System.out.println();
    }

    // ==================================================================================================== overhead

    static void overhead() throws Exception {
        System.out.println("one menu-service pod; a keep-alive client sends 500 requests/s for 60 s (the first 10 s discarded as warm-up)");
        clean();
        menu("menu-plain", 1, Sidecar.inbound(15006, 8080, null), "0.0.0.0", false);
        menu("menu-mtls", 1, Sidecar.inbound(15006, 8080, MENU_TLS), "0.0.0.0", false);
        service("plain-app", "menu-plain", 8080, 8080, true);
        service("plain-sidecar", "menu-plain", 15006, 15006, true);
        service("mtls-sidecar", "menu-mtls", 15006, 15006, true);
        String sfx = "." + NS + ".svc.cluster.local";
        caller("c-direct", null);
        caller("c-one", Sidecar.outbound(15001, new Upstream("menu-service", "plain-app" + sfx, 8080, null, null, null)));
        caller("c-two", Sidecar.outbound(15001, new Upstream("menu-service", "plain-sidecar" + sfx, 15006, null, null, null)));
        caller("c-mtls", Sidecar.outbound(15001, new Upstream("menu-service", "mtls-sidecar" + sfx, 15006, null, null, GATEWAY_TLS)));
        ready("menu-plain");
        ready("menu-mtls");
        String plainPod = kubectl(null, "-n", NS, "get", "pods", "-l", "variant=menu-plain", "-o", "jsonpath={.items[0].status.podIP}").trim();
        String[][] paths = {{"no proxy (caller to the pod's app port)", "c-direct", "http://" + plainPod + ":8080/menu"},
                {"1 proxy: caller's sidecar to the app port", "c-one", "http://127.0.0.1:15001/menu"},
                {"2 proxies: caller's sidecar to menu's sidecar, plaintext", "c-two", "http://127.0.0.1:15001/menu"},
                {"2 proxies, mTLS between them", "c-mtls", "http://127.0.0.1:15001/menu"}};
        System.out.println("path                                                       p50 (ms)  p99 (ms)  p99.9 (ms)  failed");
        for (String[] p : paths) {
            String ip = ready(p[1]);
            run(ip, p[2], 500, 10, true);                     // warm-up: JIT, connection pools
            String out = run(ip, p[2], 500, 50, true);
            System.out.printf("%-58s %-9s %-9s %-11s %s%n", p[0], field(out, "p50"), field(out, "p99"), field(out, "p999"), field(out, "failed"));
        }
        // the sidecars' own footprint, as metrics-server sees it, under the same load
        String ip = podIp("c-mtls");
        CompletableFuture<String> load = CompletableFuture.supplyAsync(() -> {
            try {
                return run(ip, "http://127.0.0.1:15001/menu", 500, 60, true);
            } catch (Exception e) {
                return "error " + e;
            }
        });
        Thread.sleep(45_000);
        String top = kubectl(null, "-n", NS, "top", "pod", "--containers", "-l", "variant=menu-mtls");
        String topCaller = kubectl(null, "-n", NS, "top", "pod", "--containers", "-l", "app=c-mtls");
        load.get();
        System.out.println("CPU and memory at 500 requests/s with mTLS (kubectl top, 15 s average):");
        (top.lines().skip(1).toList()).forEach(l -> System.out.println("   menu   " + l.replaceAll("menu-mtls-[a-z0-9-]+", "").trim()));
        (topCaller.lines().skip(1).toList()).forEach(l -> System.out.println("   caller " + l.replaceAll("c-mtls-[a-z0-9-]+", "").trim()));
        System.out.println();
    }
}
