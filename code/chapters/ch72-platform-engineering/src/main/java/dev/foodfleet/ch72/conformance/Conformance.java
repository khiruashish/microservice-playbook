package dev.foodfleet.ch72.conformance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The checks the platform runs over every service repository: each one the decision of an earlier chapter, read from
 * the files a service keeps (Dockerfile, deploy/, catalog-info.yaml, openapi.json, CI workflow). Plain text checks,
 * as a CI job or a scorecard (Backstage's Tech Insights, Cortex, OpsLevel) would run them.
 */
public final class Conformance {

    public record Check(String id, String decision, Predicate<Repo> test) {
    }

    /** The service's files, read once. */
    public record Repo(Path dir, Map<String, String> files) {

        /** A file's content; a file left with merge-conflict markers counts as unreadable, so its checks fail. */
        String file(String path) {
            String f = files.getOrDefault(path, "");
            return f.contains("\n<<<<<<< ") || f.startsWith("<<<<<<< ") ? "" : f;
        }

        boolean has(String path) {
            return files.containsKey(path);
        }
    }

    public static final Check[] CHECKS = {
            new Check("catalog-owner-lifecycle", "ADR-071: catalog entity with owner and lifecycle",
                    r -> r.file("catalog-info.yaml").contains("owner:") && r.file("catalog-info.yaml").contains("lifecycle:")),
            new Check("runbook", "ADR-071: a runbook linked from the catalog", r -> r.file("catalog-info.yaml").contains("foodfleet.dev/runbook")),
            new Check("openapi", "ADR-071: an OpenAPI document in the repository", r -> r.has("openapi.json")),
            new Check("ci-lints-api", "ADR-071: CI lints the API", r -> r.file(".github/workflows/ci.yaml").contains("spectral lint")),
            new Check("jvm-collector", "ADR-067: the collector is set explicitly", r -> Pattern.compile("-XX:\\+Use(G1|Z|Parallel)GC").matcher(r.file("Dockerfile")).find()),
            new Check("jvm-exit-on-oom", "ADR-053/067: ExitOnOutOfMemoryError", r -> r.file("Dockerfile").contains("ExitOnOutOfMemoryError")),
            new Check("non-root", "ADR-053: runs as a non-root user", r -> r.file("Dockerfile").contains("USER ") || r.file("deploy/k8s.yaml").contains("runAsNonRoot: true")),
            new Check("no-cpu-limit", "ADR-054/070: no CPU limit", r -> !Pattern.compile("limits:\\s*\\{[^}]*cpu").matcher(r.file("deploy/k8s.yaml")).find()),
            new Check("memory-limit-equals-request", "ADR-054: memory limit = request", Conformance::memoryLimitEqualsRequest),
            new Check("probes", "ADR-052: separate liveness and readiness", r -> r.file("deploy/k8s.yaml").contains("/actuator/health/readiness")
                    && r.file("deploy/k8s.yaml").contains("/actuator/health/liveness")),
            new Check("pdb", "ADR-054: a PodDisruptionBudget", r -> r.file("deploy/k8s.yaml").contains("kind: PodDisruptionBudget")),
            new Check("vpa-recommendations", "ADR-070: a VPA in recommendation mode", r -> r.file("deploy/vpa.yaml").contains("updateMode: \"Off\"")),
    };

    static final Pattern REQUEST_MEM = Pattern.compile("requests:\\s*\\{[^}]*memory:\\s*([0-9]+[A-Za-z]*)");
    static final Pattern LIMIT_MEM = Pattern.compile("limits:\\s*\\{[^}]*memory:\\s*([0-9]+[A-Za-z]*)");

    static boolean memoryLimitEqualsRequest(Repo r) {
        var req = REQUEST_MEM.matcher(r.file("deploy/k8s.yaml"));
        var lim = LIMIT_MEM.matcher(r.file("deploy/k8s.yaml"));
        return req.find() && lim.find() && req.group(1).equals(lim.group(1));
    }

    private Conformance() {
    }

    public static Repo read(Path dir) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var s = Files.walk(dir)) {
            for (Path f : s.filter(Files::isRegularFile).filter(f -> !f.toString().contains("/build/")).toList()) {
                files.put(dir.relativize(f).toString(), Files.readString(f));
            }
        }
        return new Repo(dir, files);
    }

    /** Check id to pass or fail, in order. */
    public static Map<String, Boolean> check(Repo repo) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Check c : CHECKS) {
            out.put(c.id(), c.test().test(repo));
        }
        return out;
    }
}
