import dev.foodfleet.ch55.promotion.PromotionPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chapter 55's experiments with Helm 4.1.4, kustomize 5.8.3 and a real cluster (k3s 1.37) pulling from a local OCI
 * registry.
 * <ul>
 *   <li>{@code render}: each environment rendered both ways, validated by the API server, checked by the promotion policy</li>
 *   <li>{@code ownership}: an autoscaler and a hand edit change a Deployment; what each way of re-applying does to them</li>
 *   <li>{@code config}: a configuration change; which ways of deploying roll the pods</li>
 *   <li>{@code rollback}: a release that crashes on start; Helm's rollback on failure against kubectl</li>
 *   <li>{@code tags}: a tag moved in the registry while pods run; tags against digests</li>
 * </ul>
 * System properties: c55.kubectl, c55.helm, c55.kubeconfig (for Helm), c55.rmi (removes an image from the node), c55.kustomize, c55.crane, c55.chart, c55.kustomize.dir, c55.registry,
 * c55.digest.v1, c55.digest.v2 (digests of menu-service 1.4.0 and 1.4.1 in the registry).
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c55.kubectl", "kubectl").split(" ");
    static final String HELM = System.getProperty("c55.helm", "helm");
    static final String KUSTOMIZE = System.getProperty("c55.kustomize", "kustomize");
    static final String CRANE = System.getProperty("c55.crane", "crane");
    static final Path CHART = Path.of(System.getProperty("c55.chart"));
    static final Path KUST = Path.of(System.getProperty("c55.kustomize.dir"));
    static final String REG = System.getProperty("c55.registry", "registry.foodfleet.local:5000");
    static final String V1 = System.getProperty("c55.digest.v1");
    static final String V2 = System.getProperty("c55.digest.v2");
    static final String REPO = REG + "/menu-service";
    static final Path WORK = Path.of(System.getProperty("c55.work", "/tmp/c55"));

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK);
        for (String mode : args) {
            switch (mode) {
                case "render" -> render();
                case "ownership" -> ownership();
                case "config" -> config();
                case "rollback" -> rollback();
                case "tags" -> tags();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ===================================================================================================== helpers

    record Out(int code, String text) {
    }

    static Out run(String stdin, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        String np = System.getenv().getOrDefault("NO_PROXY", "");
        pb.environment().put("NO_PROXY", np + ",registry.foodfleet.local");
        pb.environment().put("no_proxy", np + ",registry.foodfleet.local");
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        return new Out(code, out.lines().filter(l -> !l.contains("DEPRECATION")).reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    static Out kubectl(String stdin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(KUBECTL));
        cmd.addAll(Arrays.asList(args));
        return run(stdin, cmd.toArray(String[]::new));
    }

    static Out helm(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(HELM));
        if (System.getProperty("c55.kubeconfig") != null) {
            cmd.addAll(List.of("--kubeconfig", System.getProperty("c55.kubeconfig")));
        }
        cmd.addAll(Arrays.asList(args));
        return run(null, cmd.toArray(String[]::new));
    }

    static void namespace(String ns) throws Exception {
        kubectl(null, "delete", "namespace", ns, "--ignore-not-found", "--wait=true", "--timeout=120s");
        kubectl(null, "create", "namespace", ns);
    }

    /** An overlay copied to the work directory with the pipeline's digest written in. */
    static Path overlayWithDigest(String env, String digest) throws Exception {
        Path dst = WORK.resolve("kustomize-" + env);
        run(null, "rm", "-rf", dst.toString());
        run(null, "cp", "-r", KUST.toString(), dst.toString());
        Path k = dst.resolve("overlays/" + env + "/kustomization.yaml");
        String text = Files.readString(k);
        if (text.contains("digest:")) {
            text = PromotionPolicy.setDigest(text.replace("docker.io/foodfleet/menu-service", REPO), REPO, digest);
        }
        Files.writeString(k, text);
        Path base = dst.resolve("base/kustomization.yaml");
        Files.writeString(base, Files.readString(base).replace("newName: docker.io/foodfleet/menu-service", "newName: " + REPO).replace("newTag: ch54", "newTag: 1.4.0"));
        for (Path f : List.of(dst.resolve("overlays/dev/kustomization.yaml"), dst.resolve("overlays/staging/kustomization.yaml"),
                dst.resolve("overlays/prod/kustomization.yaml"))) {
            Files.writeString(f, Files.readString(f).replace("value: Never", "value: IfNotPresent"));
        }
        return dst.resolve("overlays/" + env);
    }

    static String kustomize(Path dir) throws Exception {
        Out o = run(null, KUSTOMIZE, "build", dir.toString());
        if (o.code() != 0) {
            throw new IllegalStateException(o.text());
        }
        return o.text();
    }

    static List<String> helmValues(String env, String digest) {
        List<String> v = new ArrayList<>(List.of("--set", "image.repository=" + REPO, "--set", "image.pullPolicy=IfNotPresent"));
        if (env.equals("dev")) {
            v.addAll(List.of("-f", CHART.resolve("values-dev.yaml").toString(), "--set", "image.tag=1.4.0", "--set", "image.pullPolicy=IfNotPresent"));
        } else {
            if (env.equals("prod")) {
                v.addAll(List.of("-f", CHART.resolve("values-prod.yaml").toString()));
            }
            v.addAll(List.of("--set", "image.digest=" + digest, "--set", "image.pullPolicy=IfNotPresent"));
        }
        return v;
    }

    static List<String[]> pods(String ns) throws Exception {
        String out = kubectl(null, "-n", ns, "get", "pods", "-l", "app=menu-service", "-o",
                "jsonpath={range .items[*]}{.metadata.name} {.status.podIP} {.status.containerStatuses[0].ready} {.status.containerStatuses[0].restartCount}{\"\\n\"}{end}").text();
        List<String[]> r = new ArrayList<>();
        for (String l : out.split("\n")) {
            String[] p = l.trim().split(" ");
            if (p.length == 4) {
                r.add(p);
            }
        }
        return r;
    }

    static int ready(String ns) throws Exception {
        return (int) pods(ns).stream().filter(p -> p[2].equals("true")).count();
    }

    static void waitReady(String ns, int n, int seconds) throws Exception {
        for (int i = 0; i < seconds * 2; i++) {
            if (ready(ns) >= n && pods(ns).size() == n) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(ns + ": " + n + " pods not ready in " + seconds + " s");
    }

    static String http(String ip, String path) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, 8080), 2000);
            s.setSoTimeout(3000);
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            return r.startsWith("HTTP/1.1 200") ? r.substring(r.indexOf("\r\n\r\n") + 4) : "error";
        } catch (IOException e) {
            return "error";
        }
    }

    // ====================================================================================================== render

    static void render() throws Exception {
        System.out.println("each environment rendered by Helm 4.1.4 (one chart, a values file per environment) and by kustomize 5.8.3 (base + overlay)");
        System.out.println("environment  tool        objects  lines  render (median of 5)  API server dry-run   promotion policy");
        for (String env : List.of("dev", "staging", "prod")) {
            namespace("menu-" + env);
            for (String tool : List.of("helm", "kustomize")) {
                String yaml = "";
                List<Long> ms = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    long t = System.nanoTime();
                    if (tool.equals("helm")) {
                        List<String> cmd = new ArrayList<>(List.of("template", "menu-service", CHART.toString(), "--namespace", "menu-" + env));
                        cmd.addAll(helmValues(env, V1));
                        Out o = helm(cmd.toArray(String[]::new));
                        if (o.code() != 0) {
                            throw new IllegalStateException(o.text());
                        }
                        yaml = o.text();
                    } else {
                        yaml = kustomize(overlayWithDigest(env, V1));
                    }
                    ms.add((System.nanoTime() - t) / 1_000_000);
                }
                Collections.sort(ms);
                long objects = yaml.lines().filter(l -> l.startsWith("kind:")).count();
                Out dry = kubectl(yaml, "apply", "--dry-run=server", "-n", "menu-" + env, "-f", "-");
                List<String> problems = PromotionPolicy.check(env, yaml);
                System.out.printf("%-12s %-10s %7d %6d %12d ms          %-18s %s%n", env, tool, objects, yaml.lines().count(), ms.get(2),
                        dry.code() == 0 ? "valid" : "INVALID", problems.isEmpty() ? "ok" : problems);
            }
        }
        // what each safeguard says about releases that aren't pinned
        System.out.println("unpinned or mismatched prod releases                        render              API server dry-run   promotion policy");
        Out raw = run(null, KUSTOMIZE, "build", KUST.resolve("overlays/prod").toString());
        report("kustomize prod overlay as checked in (digest placeholder)", raw, "menu-prod");
        List<String> tagged = new ArrayList<>(List.of("template", "menu-service", CHART.toString(), "-n", "menu-prod", "-f", CHART.resolve("values-prod.yaml").toString(),
                "--set", "image.repository=" + REPO, "--set", "image.digest=", "--set", "image.tag=1.4.0"));
        report("helm, prod values with --set image.tag=1.4.0", helm(tagged.toArray(String[]::new)), "menu-prod");
        report("helm, prod values as checked in (digest placeholder)", helm("template", "menu-service", CHART.toString(), "-n", "menu-prod", "-f",
                CHART.resolve("values-prod.yaml").toString()), "menu-prod");
        report("helm, default values (no tag, no digest)", helm("template", "menu-service", CHART.toString(), "-n", "menu-prod"), "menu-prod");
        String staging = kustomize(overlayWithDigest("staging", V1));
        String prodOther = kustomize(overlayWithDigest("prod", V2));
        System.out.println("policy, prod overlay naming 1.4.1 while staging tested 1.4.0: " + PromotionPolicy.samePromoted("staging", staging, "prod", prodOther));
        for (String env : List.of("dev", "staging", "prod")) {
            kubectl(null, "delete", "namespace", "menu-" + env, "--wait=false");
        }
        System.out.println();
    }

    static void report(String what, Out render, String ns) throws Exception {
        String r = render.code() == 0 ? "ok" : "FAILS: " + render.text().lines().filter(l -> l.contains("rror")).findFirst().orElse("").replaceAll("^.*\\): ", "");
        String dry = "-";
        String policy = "-";
        if (render.code() == 0) {
            Out d = kubectl(render.text(), "apply", "--dry-run=server", "-n", ns, "-f", "-");
            dry = d.code() == 0 ? "valid" : "INVALID";
            try {
                List<String> p = PromotionPolicy.check("prod", render.text());
                policy = p.isEmpty() ? "ok" : "REJECTED: " + p;
            } catch (IllegalArgumentException e) {
                policy = "REJECTED: " + e.getMessage();
            }
        }
        System.out.printf("%-59s %-19s %-20s %s%n", what, r, dry, policy);
    }

    // =================================================================================================== ownership

    static void ownership() throws Exception {
        System.out.println("menu-service deployed with replicas: 2, then changed by two other actors: an autoscaler-like scale to 5 and a hand edit");
        System.out.println("(kubectl set env FEATURE_X=on); then the same manifests are applied again");
        System.out.println("deployed and re-applied with                              re-apply                  replicas  FEATURE_X  owner of spec.replicas afterwards");
        String[][] methods = {
                {"kubectl apply (client-side)", "csa", "apply", "apply"},
                {"kubectl apply --server-side", "ssa", "apply --server-side", "apply --server-side"},
                {"  ... re-applied with --force-conflicts", "ssa-force", "apply --server-side", "apply --server-side --force-conflicts"},
                {"  ... manifest without spec.replicas", "ssa-noreplicas", "apply --server-side", "apply --server-side"},
                {"helm upgrade (Helm 4: server-side apply by default)", "helm", "", ""},
                {"  ... re-applied with --force-conflicts", "helm-force", "", "--force-conflicts"},
                {"helm upgrade --server-side=false (Helm 3's 3-way merge)", "helm-csa", "--server-side=false", "--server-side=false"}};
        List<String> errors = new ArrayList<>();
        for (String[] m : methods) {
            String ns = "own-" + m[1];
            namespace(ns);
            String yaml = kustomize(overlayWithDigest("staging", V1)).replace("namespace: menu-staging", "namespace: " + ns);
            if (m[1].equals("ssa-noreplicas")) {
                yaml = yaml.replaceAll("(?m)^  replicas: 2\n", "");
            }
            check(deployOwned(m[1], ns, yaml, m[2]));
            waitReady(ns, m[1].equals("ssa-noreplicas") ? 1 : 2, 120);
            if (m[1].equals("ssa-noreplicas")) {
                kubectl(null, "-n", ns, "scale", "deployment/menu-service", "--replicas=2");
                waitReady(ns, 2, 120);
            }
            kubectl(null, "-n", ns, "scale", "deployment/menu-service", "--replicas=5");
            kubectl(null, "-n", ns, "set", "env", "deployment/menu-service", "FEATURE_X=on");
            Thread.sleep(2000);
            Out again = deployOwned(m[1], ns, yaml, m[3]);
            Thread.sleep(2000);
            String replicas = kubectl(null, "-n", ns, "get", "deployment", "menu-service", "-o", "jsonpath={.spec.replicas}").text().trim();
            String env = kubectl(null, "-n", ns, "get", "deployment", "menu-service", "-o",
                    "jsonpath={.spec.template.spec.containers[0].env[?(@.name==\"FEATURE_X\")].value}").text().trim();
            String result = again.code() == 0 ? "ok" : again.text().toLowerCase().contains("conflict") ? "CONFLICT" : "failed";
            if (again.code() != 0) {
                errors.add(m[0].strip() + ": " + again.text().lines().filter(l -> l.toLowerCase().contains("conflict")).findFirst().orElse(again.text())
                        .replaceAll("^.*?(Apply failed)", "$1").replace("\\\"", "\"").replaceAll("\"$", "").strip());
            }
            if (m[1].startsWith("helm")) {
                Out st = helm("status", "menu-service", "-n", ns, "-o", "json");
                java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\"status\":\"([a-z-]+)\"").matcher(st.text());
                result += mm.find() ? " (release " + mm.group(1) + ")" : "";
            }
            String owners = replicaManagers(ns);
            System.out.printf("%-57s %-25s %-9s %-10s %s%n", m[0], result, replicas, env.isEmpty() ? "(removed)" : env, owners.isEmpty() ? "(none recorded)" : owners);
            kubectl(null, "delete", "namespace", ns, "--wait=false");
        }
        errors.forEach(e -> System.out.println("  " + e));
        System.out.println();
    }

    static Out deployOwned(String method, String ns, String yaml, String flags) throws Exception {
        if (method.startsWith("helm")) {
            List<String> cmd = new ArrayList<>(List.of("upgrade", "--install", "menu-service", CHART.toString(), "-n", ns, "--set", "replicaCount=2"));
            cmd.addAll(helmValues("staging", V1));
            if (!flags.isEmpty()) {
                cmd.add(flags);
            }
            return helm(cmd.toArray(String[]::new));
        }
        List<String> args = new ArrayList<>(Arrays.asList(flags.split(" ")));
        args.addAll(List.of("-f", "-"));
        return kubectl(yaml, args.toArray(String[]::new));
    }

    /** Field managers whose managedFields entry includes spec.replicas. */
    static String replicaManagers(String ns) throws Exception {
        String out = kubectl(null, "-n", ns, "get", "deployment", "menu-service", "--show-managed-fields", "-o",
                "jsonpath={range .metadata.managedFields[*]}{.manager}|{.operation}|{.subresource}|{.fieldsV1}{\"\\n\"}{end}").text();
        List<String> names = new ArrayList<>();
        for (String l : out.split("\n")) {
            String[] p = l.split("\\|", 4);
            if (p.length == 4 && !p[2].equals("status") && p[3].contains("f:replicas")) {
                names.add(p[0] + " (" + p[1] + (p[2].isEmpty() ? "" : ", /" + p[2]) + ")");
            }
        }
        return String.join(", ", names);
    }

    // ====================================================================================================== config

    static void config() throws Exception {
        System.out.println("menu-service (2 replicas) deployed; discount changed 10 -> 20 and re-deployed; the pods polled every 2 s for 150 s");
        System.out.println("deployed with                                           pods replaced  mounted file shows 20 on all pods  env var at the end");
        String[][] methods = {
                {"helm, chart with the checksum/config annotation", "helm"},
                {"helm, same chart without the annotation", "helm-nochecksum"},
                {"kustomize configMapGenerator (name has a content hash)", "kustomize"},
                {"kustomize, generator with disableNameSuffixHash", "kustomize-nohash"}};
        for (String[] m : methods) {
            String ns = "cfg-" + m[1];
            namespace(ns);
            Path chart = CHART;
            if (m[1].equals("helm-nochecksum")) {
                chart = WORK.resolve("chart-nochecksum");
                run(null, "rm", "-rf", chart.toString());
                run(null, "cp", "-r", CHART.toString(), chart.toString());
                Path d = chart.resolve("templates/deployment.yaml");
                Files.writeString(d, Files.readString(d).replaceAll("(?m)^\\s*checksum/config:.*\\n", ""));
            }
            deploy(m[1], ns, chart, "10");
            waitReady(ns, 2, 120);
            List<String> before = pods(ns).stream().map(p -> p[0]).toList();
            long t = System.nanoTime();
            deploy(m[1], ns, chart, "20");
            String fileAt = "not within 150 s";
            Map<String, Integer> seen = new TreeMap<>();
            while (System.nanoTime() - t < 150_000_000_000L) {
                Thread.sleep(2000);
                List<String[]> ps = pods(ns).stream().filter(p -> p[2].equals("true")).toList();
                seen.clear();
                for (String[] p : ps) {
                    seen.merge(http(p[1], "/config"), 1, Integer::sum);
                }
                if (ps.size() == 2 && seen.keySet().stream().allMatch(c -> c.endsWith("mounted=20"))) {
                    fileAt = String.format("after %.0f s", (System.nanoTime() - t) / 1e9);
                    break;
                }
            }
            waitReady(ns, 2, 120);
            long replaced = pods(ns).stream().filter(p -> !before.contains(p[0])).count();
            Map<String, Integer> envs = new TreeMap<>();
            for (String[] p : pods(ns)) {
                String c = http(p[1], "/config");
                envs.merge(c.replaceAll(" mounted=.*", ""), 1, Integer::sum);
            }
            System.out.printf("%-55s %d of 2         %-34s %s%n", m[0], replaced, fileAt, envs);
            kubectl(null, "delete", "namespace", ns, "--wait=false");
        }
        System.out.println();
    }

    static void deploy(String method, String ns, Path chart, String discount) throws Exception {
        if (method.startsWith("helm")) {
            List<String> cmd = new ArrayList<>(List.of("upgrade", "--install", "menu-service", chart.toString(), "-n", ns, "--set", "replicaCount=2",
                    "--set-string", "config.discount=" + discount, "--set", "pdb=null"));
            cmd.addAll(helmValues("staging", V1));
            Out o = helm(cmd.toArray(String[]::new));
            if (o.code() != 0) {
                throw new IllegalStateException(o.text());
            }
        } else {
            Path dir = overlayWithDigest("staging", V1);
            Path k = dir.resolve("kustomization.yaml");
            String text = Files.readString(k).replace("namespace: menu-staging", "namespace: " + ns) + """
                    configMapGenerator:
                      - name: menu-service-config
                        behavior: merge
                        literals: [discount=%s]
                    """.formatted(discount);
            if (method.equals("kustomize-nohash")) {
                text += "generatorOptions:\n  disableNameSuffixHash: true\n";
            }
            Files.writeString(k, text);
            Out o = kubectl(kustomize(dir), "apply", "--server-side", "-f", "-");
            if (o.code() != 0) {
                throw new IllegalStateException(o.text());
            }
        }
    }

    // ==================================================================================================== rollback

    static final List<String> CRASH = List.of("--set", "extraEnv[0].name=CRASH_ON_START", "--set-string", "extraEnv[0].value=true");

    static void rollback() throws Exception {
        System.out.println("menu-service 1.4.0, 3 replicas, 20 requests/s through the Service on new connections; release 1.4.1 crashes on start");
        System.out.println("deployed with                                      command returned                    ready pods afterwards  crash-looping  failed requests");
        for (String[] m : new String[][] {{"helm upgrade --rollback-on-failure --timeout 60s", "helm"},
                {"kubectl apply; rollout status --timeout 60s; undo", "kubectl"},
                {"kubectl apply only (nothing watches)", "apply"},
                {"  ... strategy maxUnavailable 1, maxSurge 0", "apply-unavailable"}}) {
            String ns = "rb-" + m[1];
            namespace(ns);
            List<String> good = new ArrayList<>(List.of("menu-service", CHART.toString(), "-n", ns, "--set", "replicaCount=3", "--set", "pdb=null"));
            good.addAll(helmValues("staging", V1));
            List<String> bad = new ArrayList<>(good);
            bad.addAll(List.of("--set", "image.digest=" + V2));
            bad.addAll(CRASH);
            if (m[1].equals("helm")) {
                List<String> cmd = new ArrayList<>(List.of("upgrade", "--install"));
                cmd.addAll(good);
                check(helm(cmd.toArray(String[]::new)));
            } else {
                kubectl(strategy(m[1], template(good)), "apply", "-n", ns, "-f", "-");
            }
            waitReady(ns, 3, 120);
            String cip = kubectl(null, "-n", ns, "get", "svc", "menu-service", "-o", "jsonpath={.spec.clusterIP}").text().trim();
            Load load = Load.start(cip);
            Thread.sleep(3000);
            long t = System.nanoTime();
            String returned;
            if (m[1].equals("helm")) {
                List<String> cmd = new ArrayList<>(List.of("upgrade", "--install"));
                cmd.addAll(bad);
                cmd.addAll(List.of("--rollback-on-failure", "--timeout", "60s"));
                Out o = helm(cmd.toArray(String[]::new));
                returned = String.format("exit %d after %.0f s", o.code(), (System.nanoTime() - t) / 1e9);
            } else {
                kubectl(strategy(m[1], template(bad)), "apply", "-n", ns, "-f", "-");
                if (m[1].equals("kubectl")) {
                    Out st = kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-service", "--timeout=60s");
                    kubectl(null, "-n", ns, "rollout", "undo", "deployment/menu-service");
                    kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-service", "--timeout=120s");
                    returned = String.format("status exit %d, undone at %.0f s", st.code(), (System.nanoTime() - t) / 1e9);
                } else {
                    returned = "exit 0 at once";
                    Thread.sleep(60_000);
                }
            }
            Thread.sleep(5000);
            int[] r = load.stop();
            Map<String, Integer> versions = new TreeMap<>();
            int notReady = 0;
            for (String[] p : pods(ns)) {
                if (p[2].equals("true")) {
                    versions.merge(http(p[1], "/version"), 1, Integer::sum);
                } else {
                    notReady++;
                }
            }
            System.out.printf("%-50s %-35s %-22s %-14d %d of %d%n", m[0], returned, versions, notReady, r[1], r[0] + r[1]);
            if (r[1] > 0) {
                System.out.println("    failures (seconds after the load started; the bad release went out at 3 s): " + String.join(", ", load.failures));
            }
            if (m[1].equals("helm")) {
                Out h = helm("history", "menu-service", "-n", ns, "-o", "json");
                java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\"revision\":(\\d+).*?\"status\":\"([a-z-]+)\"").matcher(h.text());
                StringBuilder sb = new StringBuilder();
                while (mm.find()) {
                    sb.append(mm.group(1)).append('=').append(mm.group(2)).append(' ');
                }
                System.out.println("    helm history: " + sb.toString().trim());
            }
            kubectl(null, "delete", "namespace", ns, "--wait=false");
        }
        System.out.println();
    }

    static String strategy(String method, String yaml) {
        return method.equals("apply-unavailable")
                ? yaml.replace("  replicas: 3", "  replicas: 3\n  strategy: {rollingUpdate: {maxUnavailable: 1, maxSurge: 0}}") : yaml;
    }

    static String template(List<String> args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("template"));
        cmd.addAll(args);
        Out o = helm(cmd.toArray(String[]::new));
        check(o);
        return o.text();
    }

    static void check(Out o) {
        if (o.code() != 0) {
            throw new IllegalStateException(o.text());
        }
    }

    /** 20 requests a second, each on a new connection, to the Service's cluster IP. */
    static final class Load {
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicBoolean running = new AtomicBoolean(true);
        final java.util.concurrent.ConcurrentLinkedQueue<String> failures = new java.util.concurrent.ConcurrentLinkedQueue<>();
        long t0;
        Thread thread;

        static Load start(String ip) {
            Load l = new Load();
            long t0 = System.nanoTime();
            l.t0 = t0;
            l.thread = Thread.ofPlatform().daemon().start(() -> {
                try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (long i = 0; l.running.get(); i++) {
                        long wait = t0 + i * 50_000_000L - System.nanoTime();
                        if (wait > 0) {
                            TimeUnit.NANOSECONDS.sleep(wait);
                        }
                        pool.submit(() -> {
                            try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress(ip, 80), 2000);
                                s.setSoTimeout(3000);
                                s.getOutputStream().write("GET /menu HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                                String r = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                                if (r.startsWith("HTTP/1.1 200")) {
                                    l.ok.incrementAndGet();
                                } else {
                                    l.fail("status " + r.lines().findFirst().orElse("(none)"));
                                }
                            } catch (IOException e) {
                                l.fail(e.getClass().getSimpleName());
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return l;
        }

        void fail(String why) {
            failed.incrementAndGet();
            failures.add(String.format("%.0f s: %s", (System.nanoTime() - t0) / 1e9, why));
        }

        int[] stop() throws InterruptedException {
            running.set(false);
            thread.join();
            return new int[] {ok.get(), failed.get()};
        }
    }

    // ======================================================================================================== tags

    static final String RMI = System.getProperty("c55.rmi", "crictl rmi");

    static void tags() throws Exception {
        System.out.println("menu-service, 2 replicas from the tag 'stable' (= 1.4.0); someone moves 'stable' to 1.4.1 in the registry; then scaled to 4.");
        System.out.println("\"fresh node\": the node's cached copy is removed before the scale-out, as on a node the cluster autoscaler just added");
        System.out.println("image reference         imagePullPolicy   new pods land on   versions across the 4 pods");
        String[][] cases = {{"tag", "IfNotPresent", "cached"}, {"tag", "IfNotPresent", "fresh"}, {"tag", "Always", "cached"}, {"digest", "IfNotPresent", "fresh"}};
        for (String[] c : cases) {
            check(run(null, CRANE, "tag", "--insecure", REPO + "@" + V1, "stable"));
            rmi(REPO + ":stable");
            String ns = "tags-" + c[0] + "-" + c[1].toLowerCase() + "-" + c[2];
            namespace(ns);
            List<String> args = new ArrayList<>(List.of("menu-service", CHART.toString(), "-n", ns, "--set", "replicaCount=2", "--set", "pdb=null",
                    "--set", "image.repository=" + REPO, "--set", "image.pullPolicy=" + c[1]));
            args.addAll(c[0].equals("tag") ? List.of("--set", "image.tag=stable") : List.of("--set", "image.digest=" + V1));
            kubectl(template(args), "apply", "-n", ns, "-f", "-");
            waitReady(ns, 2, 120);
            check(run(null, CRANE, "tag", "--insecure", REPO + "@" + V2, "stable"));     // the tag moves
            if (c[2].equals("fresh")) {
                rmi(REPO + ":stable");
                rmi(REPO + "@" + V1);
            }
            kubectl(null, "-n", ns, "scale", "deployment/menu-service", "--replicas=4");
            waitReady(ns, 4, 120);
            Map<String, Integer> versions = new TreeMap<>();
            for (String[] p : pods(ns)) {
                versions.merge(http(p[1], "/version"), 1, Integer::sum);
            }
            System.out.printf("%-23s %-17s %-18s %s%n", c[0].equals("tag") ? "menu-service:stable" : "menu-service@sha256:" + V1.substring(7, 15) + "...",
                    c[1], c[2] + " node", versions);
            kubectl(null, "delete", "namespace", ns, "--wait=true", "--timeout=120s");
        }
        check(run(null, CRANE, "tag", "--insecure", REPO + "@" + V1, "stable"));
        System.out.println();
    }

    static void rmi(String ref) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(RMI.split(" ")));
        cmd.add(ref);
        run(null, cmd.toArray(String[]::new));
    }

    static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
