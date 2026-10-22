import dev.foodfleet.ch72.conformance.Conformance;
import dev.foodfleet.ch72.scaffold.Template;
import dev.foodfleet.ch72.scaffold.Upgrade;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chapter 72's experiments.
 * <ul>
 *   <li>{@code scaffold}: a new service from the golden path to a running pod: render, compile, image, deploy, answer</li>
 *   <li>{@code fleet}: twelve services (from template v1, from v2, and by hand, each with its own edits) against the
 *       platform's twelve conformance checks, and their manifests against the API server (server-side dry run)</li>
 *   <li>{@code upgrade}: the v1 services brought to v2 by a three-way merge, as Copier's update does</li>
 * </ul>
 * System properties: c72.templates, c72.work, c72.kubectl, c72.loadImage.
 */
public final class Lab {

    static final Path TEMPLATES = Path.of(System.getProperty("c72.templates", "../src/main/resources/templates"));
    static final Path WORK = Path.of(System.getProperty("c72.work", "build/work"));
    static final String KUBECTL = System.getProperty("c72.kubectl", "kubectl");
    static final String LOAD_IMAGE = System.getProperty("c72.loadImage", "");
    static final String JAVAC = Path.of(System.getProperty("java.home"), "bin", "javac").toString();

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "scaffold" -> scaffold();
                case "fleet" -> fleet();
                case "upgrade" -> upgrade();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------ scaffold to a running pod

    static void scaffold() throws Exception {
        Path dir = WORK.resolve("scaffold/rider-ratings");
        rm(dir);
        Template.Values v = new Template.Values("rider-ratings", "delivery", "Customers rate their riders.", "0.1.0");
        Map<String, Long> steps = new LinkedHashMap<>();
        long t = System.nanoTime();
        Map<String, String> files = Template.scaffold(TEMPLATES.resolve("v2"), v, dir);
        steps.put("render the template (" + files.size() + " files)", System.nanoTime() - t);
        t = System.nanoTime();
        sh(dir, JAVAC, "--release", "25", "-d", "build/classes", "src/main/java/dev/foodfleet/riderratings/Application.java");
        steps.put("compile", System.nanoTime() - t);
        t = System.nanoTime();
        sh(dir, "docker", "build", "-q", "-t", "foodfleet/rider-ratings:0.1.0", ".");
        steps.put("build the image", System.nanoTime() - t);
        t = System.nanoTime();
        if (!LOAD_IMAGE.isEmpty()) {
            sh(dir, "sh", "-c", "docker save foodfleet/rider-ratings:0.1.0 | " + LOAD_IMAGE);
        }
        steps.put("load it into the cluster", System.nanoTime() - t);
        kubectl("delete", "namespace", "c72", "--wait=true", "--timeout=120s");
        kubectl("create", "namespace", "c72");
        t = System.nanoTime();
        String applied = kubectl("-n", "c72", "apply", "-f", dir.resolve("deploy/k8s.yaml").toString());
        String rollout = kubectl("-n", "c72", "rollout", "status", "deploy/rider-ratings", "--timeout=180s");
        steps.put("deploy and wait for 2 ready copies", System.nanoTime() - t);
        t = System.nanoTime();
        String ip = kubectl("-n", "c72", "get", "svc", "rider-ratings", "-o", "jsonpath={.spec.clusterIP}").strip();
        String body = "";
        HttpClient http = HttpClient.newHttpClient();
        for (int i = 0; i < 100 && !body.contains("rider-ratings"); i++) {
            try {
                body = http.send(HttpRequest.newBuilder(URI.create("http://" + ip + "/hello")).build(), HttpResponse.BodyHandlers.ofString()).body();
            } catch (Exception e) {
                Thread.sleep(200);
            }
        }
        steps.put("first answer through the Service", System.nanoTime() - t);
        long total = steps.values().stream().mapToLong(Long::longValue).sum();
        System.out.println("rider-ratings from template v2, by the delivery team: " + applied.lines().count() + " objects applied; " + rollout.lines().reduce((a, b) -> b).orElse(""));
        steps.forEach((k, n) -> System.out.printf("  %-46s %8.1f s%n", k, n / 1e9));
        System.out.printf("  %-46s %8.1f s%n", "total", total / 1e9);
        System.out.println("  answer: " + body);
        Map<String, Boolean> score = Conformance.check(Conformance.read(dir));
        System.out.printf("  conformance: %d of %d checks%n", score.values().stream().filter(b -> b).count(), score.size());
        long appLines = files.entrySet().stream().filter(e -> e.getKey().endsWith(".java")).mapToLong(e -> e.getValue().lines().count()).sum();
        long platformLines = files.entrySet().stream().filter(e -> !e.getKey().endsWith(".java") && !e.getKey().endsWith(".md")).mapToLong(e -> e.getValue().lines().count()).sum();
        System.out.printf("  lines generated: %d of platform files (Dockerfile, deploy/, catalog, OpenAPI, CI), %d of application code%n", platformLines, appLines);
        kubectl("delete", "namespace", "c72", "--wait=true", "--timeout=120s");
    }

    // ------------------------------------------------------------------ a fleet that drifted

    record Svc(String name, String team, String origin) {
    }

    static final List<Svc> FLEET = List.of(
            new Svc("ratings-service", "delivery", "v1"), new Svc("coupon-service", "growth", "v1"), new Svc("invoice-service", "payments", "v1"),
            new Svc("kitchen-display", "menu", "v1"), new Svc("eta-predictor", "delivery", "v2"), new Svc("menu-images", "menu", "v2"),
            new Svc("loyalty-service", "growth", "v2"), new Svc("referral-service", "growth", "v2"), new Svc("support-console", "platform", "hand"),
            new Svc("fraud-scorer", "payments", "hand"), new Svc("zone-planner", "delivery", "hand"), new Svc("tax-calculator", "payments", "hand"));

    /** Builds the fleet: each service generated (or written by hand), then edited as its team did. */
    static void buildFleet() throws Exception {
        Path root = WORK.resolve("fleet");
        rm(root);
        for (Svc s : FLEET) {
            Path dir = root.resolve(s.name());
            Template.Values v = new Template.Values(s.name(), s.team(), "FoodFleet's " + s.name() + ".", "1.0.0");
            if (!s.origin().equals("hand")) {
                Template.scaffold(TEMPLATES.resolve(s.origin()), v, dir);
            }
            switch (s.name()) {
                // generated from v1, then changed by their teams
                case "ratings-service" -> {
                    edit(dir, "src/main/java/dev/foodfleet/ratingsservice/Application.java", "        server.start();",
                            "        server.createContext(\"/ratings\", ex -> reply(ex, 200, \"[]\"));\n        server.start();");
                    edit(dir, "deploy/k8s.yaml", "  replicas: 2", "  replicas: 3");
                }
                case "coupon-service" -> {
                    edit(dir, "Dockerfile", "\"-XX:MaxRAMPercentage=75\"", "\"-XX:MaxRAMPercentage=75\", \"-Xss512k\"");
                    edit(dir, "catalog-info.yaml", "  description: FoodFleet's coupon-service.", "  description: Coupons and their redemption rules.");
                }
                case "invoice-service" -> {
                    edit(dir, "deploy/k8s.yaml", "            requests: {cpu: \"1\", memory: 2Gi}\n            limits: {cpu: \"1\", memory: 2Gi}",
                            "            requests: {cpu: \"1\", memory: 1Gi}\n            limits: {cpu: \"1\", memory: 1Gi}");
                    edit(dir, ".github/workflows/ci.yaml", "      - run: docker build", "      - run: ./run-tests.sh\n      - run: docker build");
                }
                // generated from v2, then changed
                case "eta-predictor" -> edit(dir, "deploy/k8s.yaml", "            limits: {memory: 384Mi}", "            limits: {cpu: 500m, memory: 384Mi}");
                case "menu-images" -> edit(dir, "Dockerfile", ", \"-XX:+ExitOnOutOfMemoryError\"", "");
                case "loyalty-service" -> edit(dir, "src/main/java/dev/foodfleet/loyaltyservice/Application.java", "        server.start();",
                        "        server.createContext(\"/points\", ex -> reply(ex, 200, \"{\\\"points\\\":0}\"));\n        server.start();");
                // written by hand, each from a different older service
                case "support-console" -> {
                    Map<String, String> f = Template.render(TEMPLATES.resolve("v1"), v);
                    f.remove("catalog-info.yaml");
                    f.put("Dockerfile", "FROM c54/jre:25\nCOPY build/classes/ /app/classes/\nENTRYPOINT [\"java\", \"-cp\", \"/app/classes\", \"dev.foodfleet.supportconsole.Application\"]\n");
                    Template.write(f, dir);
                }
                case "fraud-scorer" -> {
                    Map<String, String> f = Template.render(TEMPLATES.resolve("v2"), v);
                    f.put("catalog-info.yaml", f.get("catalog-info.yaml").replace("  lifecycle: production\n", ""));
                    f.put("deploy/k8s.yaml", f.get("deploy/k8s.yaml").substring(0, f.get("deploy/k8s.yaml").indexOf("---\napiVersion: policy/v1")));
                    f.remove("deploy/vpa.yaml");
                    Template.write(f, dir);
                }
                case "zone-planner" -> {
                    Map<String, String> f = Template.render(TEMPLATES.resolve("v2"), v);
                    // a probe without a port: not a valid manifest
                    f.put("deploy/k8s.yaml", f.get("deploy/k8s.yaml").replace("httpGet: {path: /actuator/health/liveness, port: 8080}", "httpGet: {path: /actuator/health/liveness}"));
                    f.remove("openapi.json");
                    f.put(".github/workflows/ci.yaml", f.get(".github/workflows/ci.yaml").replaceAll("      - run: spectral[^\n]*\n", ""));
                    Template.write(f, dir);
                }
                case "tax-calculator" -> {
                    Map<String, String> f = Template.render(TEMPLATES.resolve("v1"), v);
                    f.put("deploy/k8s.yaml", f.get("deploy/k8s.yaml").replace("            limits: {cpu: \"1\", memory: 2Gi}", "            limits: {cpu: \"2\", memory: 4Gi}"));
                    Template.write(f, dir);
                }
                default -> {
                }
            }
        }
    }

    static void fleet() throws Exception {
        buildFleet();
        System.out.println("12 services: 4 generated from template v1, 4 from v2, 4 written by hand; each changed by its team since");
        Conformance.Check[] checks = Conformance.CHECKS;
        StringBuilder head = new StringBuilder(String.format("%-18s %-7s", "service", "origin"));
        for (int i = 0; i < checks.length; i++) {
            head.append(String.format(" %3s", "C" + (i + 1)));
        }
        System.out.println(head + "   passed   API server (dry run)");
        Map<String, int[]> byOrigin = new LinkedHashMap<>();
        int[] perCheck = new int[checks.length];
        kubectl("delete", "namespace", "c72-fleet", "--wait=true", "--timeout=120s");
        kubectl("create", "namespace", "c72-fleet");
        for (Svc s : FLEET) {
            Path dir = WORK.resolve("fleet").resolve(s.name());
            Map<String, Boolean> r = Conformance.check(Conformance.read(dir));
            StringBuilder row = new StringBuilder(String.format("%-18s %-7s", s.name(), s.origin()));
            int i = 0;
            int pass = 0;
            for (boolean b : r.values()) {
                row.append(String.format(" %3s", b ? "+" : "-"));
                if (b) {
                    pass++;
                    perCheck[i]++;
                }
                i++;
            }
            String dry = kubectl("-n", "c72-fleet", "apply", "--dry-run=server", "-f", dir.resolve("deploy/k8s.yaml").toString());
            boolean accepted = dry.contains("(server dry run)") && !dry.contains("is invalid") && !dry.toLowerCase().contains("error");
            System.out.println(row + String.format("   %2d/%d    %s", pass, checks.length, accepted ? "accepted" : "rejected: " + firstError(dry)));
            int[] g = byOrigin.computeIfAbsent(s.origin(), k -> new int[3]);
            g[0] += pass;
            g[1] += checks.length;
            g[2] += accepted ? 1 : 0;
        }
        kubectl("delete", "namespace", "c72-fleet", "--wait=true", "--timeout=120s");
        System.out.println();
        for (int i = 0; i < checks.length; i++) {
            System.out.printf("  C%-3d %-48s passed by %2d of %d%n", i + 1, checks[i].decision(), perCheck[i], FLEET.size());
        }
        byOrigin.forEach((o, g) -> System.out.printf("  %-5s services: %d of %d checks passed (%.0f%%); manifests accepted: %d of 4%n", o, g[0], g[1], 100.0 * g[0] / g[1], g[2]));
    }

    static String firstError(String out) {
        String line = out.lines().filter(l -> l.toLowerCase().contains("error") || l.contains("is invalid")).findFirst().orElse(out);
        int i = line.indexOf("spec.");
        return i >= 0 ? line.substring(i, Math.min(line.length(), i + 110)) : line.substring(0, Math.min(line.length(), 140));
    }

    // ------------------------------------------------------------------ upgrade v1 -> v2

    static void upgrade() throws Exception {
        buildFleet();
        System.out.println("the four services generated from v1 (with their teams' changes), updated to template v2 by a three-way merge per file");
        System.out.printf("%-18s %-62s %14s%n", "service", "files: updated / merged / conflict / added / unchanged", "checks passed");
        Map<String, Integer> totals = new TreeMap<>();
        for (Svc s : FLEET.stream().filter(x -> x.origin().equals("v1")).toList()) {
            Path dir = WORK.resolve("fleet").resolve(s.name());
            Template.Values v = new Template.Values(s.name(), s.team(), "FoodFleet's " + s.name() + ".", "1.0.0");
            long before = Conformance.check(Conformance.read(dir)).values().stream().filter(b -> b).count();
            Map<String, Upgrade.Outcome> out = Upgrade.apply(dir, Template.render(TEMPLATES.resolve("v1"), v), Template.render(TEMPLATES.resolve("v2"), v));
            Map<String, Boolean> after = Conformance.check(Conformance.read(dir));
            long afterPass = after.values().stream().filter(b -> b).count();
            Map<Upgrade.Outcome, List<String>> by = new TreeMap<>();
            out.forEach((f, o) -> by.computeIfAbsent(o, k -> new ArrayList<>()).add(f));
            out.values().forEach(o -> totals.merge(o.name(), 1, Integer::sum));
            String counts = String.format("%d / %d / %d / %d / %d", n(by, Upgrade.Outcome.UPDATED), n(by, Upgrade.Outcome.MERGED), n(by, Upgrade.Outcome.CONFLICT),
                    n(by, Upgrade.Outcome.ADDED), n(by, Upgrade.Outcome.UNCHANGED) + n(by, Upgrade.Outcome.KEPT_LOCAL));
            System.out.printf("%-18s %-62s %6d -> %d%n", s.name(), counts, before, afterPass);
            for (Upgrade.Outcome o : List.of(Upgrade.Outcome.MERGED, Upgrade.Outcome.CONFLICT)) {
                if (by.containsKey(o)) {
                    System.out.println("    " + o.name().toLowerCase() + ": " + String.join(", ", by.get(o)));
                }
            }
            if (by.containsKey(Upgrade.Outcome.CONFLICT)) {
                for (String f : by.get(Upgrade.Outcome.CONFLICT)) {
                    List<String> lines = Files.readAllLines(dir.resolve(f));
                    int start = lines.indexOf(lines.stream().filter(l -> l.startsWith("<<<<<<<")).findFirst().orElse(""));
                    int end = lines.indexOf(lines.stream().filter(l -> l.startsWith(">>>>>>>")).findFirst().orElse(""));
                    if (start >= 0 && end > start) {
                        lines.subList(start, end + 1).forEach(l -> System.out.println("      | " + l));
                    }
                }
            }
        }
        System.out.println("all files: " + totals);
        System.out.println("the four hand-written services have no template version to merge from: they can only be migrated by hand");
    }

    static int n(Map<Upgrade.Outcome, List<String>> by, Upgrade.Outcome o) {
        return by.getOrDefault(o, List.of()).size();
    }

    // ------------------------------------------------------------------ helpers

    static void edit(Path dir, String file, String from, String to) throws Exception {
        Path p = dir.resolve(file);
        String s = Files.readString(p);
        if (!s.contains(from)) {
            throw new IllegalStateException(file + ": no '" + from + "'");
        }
        Files.writeString(p, s.replace(from, to));
    }

    static String kubectl(String... args) throws Exception {
        List<String> c = new ArrayList<>(List.of(KUBECTL.split(" ")));
        c.addAll(List.of(args));
        return sh(Path.of("."), c.toArray(String[]::new));
    }

    static String sh(Path dir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0 && !cmd[0].contains("kubectl") && !String.join(" ", cmd).contains("kubectl")) {
            throw new IllegalStateException(String.join(" ", cmd) + " failed:\n" + out);
        }
        return out;
    }

    static void rm(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }
}
