import dev.foodfleet.ch71.catalog.Catalog;
import dev.foodfleet.ch71.deprecation.Deprecations;
import dev.foodfleet.ch71.lint.Baseline;
import dev.foodfleet.ch71.lint.Linter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Chapter 71's experiments.
 * <ul>
 *   <li>{@code lint}: six OpenAPI documents with 33 planted design problems, against Spectral's recommended rules and
 *       FoodFleet's house rules</li>
 *   <li>{@code ratchet}: a legacy document's existing findings recorded, so a change fails only on what it adds</li>
 *   <li>{@code deprecation}: Deprecation, Sunset and Link headers from the document, a client that warns, and who still
 *       calls a deprecated operation according to the access log and according to the catalog</li>
 *   <li>{@code catalog}: FoodFleet's Backstage catalog entities checked for what governance needs</li>
 * </ul>
 * System property c71.resources: the directory with openapi/ and catalog/; c71.violations: violations.tsv.
 */
public final class Lab {

    static final Path RES = Path.of(System.getProperty("c71.resources", "../src/main/resources"));

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "lint" -> lint();
                case "ratchet" -> ratchet();
                case "deprecation" -> Deprecations.experiment(RES.resolve("openapi/payment-service.json"), RES.resolve("catalog"));
                case "catalog" -> catalog();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    record Planted(String spec, String rule, String pointer, String what) {
    }

    static void lint() throws Exception {
        List<Planted> planted = Files.readAllLines(Path.of(System.getProperty("c71.violations", "violations.tsv"))).stream()
                .filter(l -> !l.startsWith("#") && !l.isBlank()).map(l -> l.split("\t")).map(f -> new Planted(f[0], f[1], f[2], f[3])).toList();
        Map<String, List<Linter.Finding>> found = new TreeMap<>();
        long t = System.nanoTime();
        int runs = 200;
        for (int i = 0; i < runs; i++) {
            for (Path spec : Files.list(RES.resolve("openapi")).sorted().toList()) {
                found.put(spec.getFileName().toString().replace(".json", ""), Linter.lint(Files.readString(spec)));
            }
        }
        double msPerDoc = (System.nanoTime() - t) / 1e6 / runs / found.size();
        System.out.println(planted.size() + " planted problems in " + found.size() + " documents (" + planted.stream().filter(p -> p.rule().equals("-")).count()
                + " that no rule describes); " + Linter.Rule.values().length + " rules: " + count(Linter.Source.SPECTRAL) + " from Spectral's recommended spectral:oas, "
                + count(Linter.Source.HOUSE) + " FoodFleet house rules");
        System.out.printf("%-20s %8s %15s %13s %12s %15s%n", "document", "planted", "caught: Spectral", "caught: house", "missed", "false findings");
        int[] tot = new int[5];
        for (String spec : found.keySet()) {
            List<Linter.Finding> f = found.get(spec);
            Set<String> hits = f.stream().map(x -> x.rule().id + " " + x.pointer()).collect(Collectors.toSet());
            List<Planted> mine = planted.stream().filter(p -> p.spec().equals(spec)).toList();
            int spectral = 0;
            int house = 0;
            int missed = 0;
            Set<String> expected = new HashSet<>();
            for (Planted p : mine) {
                String key = p.rule() + " " + p.pointer();
                expected.add(key);
                if (hits.contains(key)) {
                    if (rule(p.rule()).source == Linter.Source.SPECTRAL) {
                        spectral++;
                    } else {
                        house++;
                    }
                } else {
                    missed++;
                }
            }
            long falseFindings = hits.stream().filter(h -> !expected.contains(h)).count();
            System.out.printf("%-20s %8d %15d %13d %12d %15d%n", spec, mine.size(), spectral, house, missed, falseFindings);
            tot[0] += mine.size();
            tot[1] += spectral;
            tot[2] += house;
            tot[3] += missed;
            tot[4] += (int) falseFindings;
            hits.stream().filter(h -> !expected.contains(h)).forEach(h -> System.out.println("    unexpected: " + h));
        }
        System.out.printf("%-20s %8d %15d %13d %12d %15d%n", "total", tot[0], tot[1], tot[2], tot[3], tot[4]);
        System.out.println();
        System.out.println("what was missed (no rule describes it):");
        for (Planted p : planted) {
            String key = p.rule() + " " + p.pointer();
            if (found.get(p.spec()).stream().noneMatch(x -> (x.rule().id + " " + x.pointer()).equals(key))) {
                System.out.printf("  %-20s %s%n", p.spec(), p.what());
            }
        }
        System.out.println();
        Map<Linter.Severity, Long> bySeverity = found.values().stream().flatMap(List::stream).collect(Collectors.groupingBy(x -> x.rule().severity, TreeMap::new, Collectors.counting()));
        System.out.printf("findings by severity: %s; a build failing on errors would have stopped %d of %d documents; %.2f ms per document%n", bySeverity,
                found.values().stream().filter(l -> l.stream().anyMatch(x -> x.rule().severity == Linter.Severity.ERROR)).count(), found.size(), msPerDoc);
    }

    static Linter.Rule rule(String id) {
        for (Linter.Rule r : Linter.Rule.values()) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        throw new IllegalArgumentException(id);
    }

    static long count(Linter.Source s) {
        return java.util.Arrays.stream(Linter.Rule.values()).filter(r -> r.source == s).count();
    }

    static void ratchet() throws Exception {
        String legacy = Files.readString(RES.resolve("openapi/order-service.json"));
        List<Linter.Finding> before = Linter.lint(legacy);
        Baseline baseline = Baseline.of(before);
        // a change: a new operation with an action in the URL and a float amount, and one old problem fixed
        String changed = legacy.replace("\"/orders/{orderId}/cancel\": {", "\"/orders/{orderId}/applyCoupon\": {\n    \"post\": {\"operationId\": \"applyCoupon\", \"description\": \"Applies a coupon.\", \"tags\": [\"orders\"], "
                + "\"parameters\": [{\"name\": \"orderId\", \"in\": \"path\", \"required\": true, \"schema\": {\"type\": \"string\"}}], \"responses\": {\"200\": {\"description\": \"OK\"}}}\n  },\n  \"/orders/{orderId}/cancel\": {")
                .replace("\"total\": {\n            \"type\": \"number\"\n          }", "\"total\": {\n            \"type\": \"string\",\n            \"format\": \"decimal\"\n          },\n          \"discountAmount\": {\n            \"type\": \"number\"\n          }");
        List<Linter.Finding> after = Linter.lint(changed);
        Baseline.Result r = baseline.compare(after);
        System.out.println("order-service as it is: " + before.size() + " findings, recorded as the baseline");
        System.out.println("a change adds an operation (POST /orders/{orderId}/applyCoupon) and a property (discountAmount), and fixes total:");
        System.out.printf("  findings after the change: %d; new: %d; fixed: %d; still there (baselined, not reported): %d%n", after.size(), r.added().size(), r.fixed().size(), r.kept().size());
        r.added().forEach(f -> System.out.println("  new:   " + f.rule().id + " at " + f.pointer()));
        r.fixed().forEach(f -> System.out.println("  fixed: " + f.rule().id + " at " + f.pointer()));
    }

    static void catalog() throws Exception {
        Catalog.Report report = Catalog.check(RES.resolve("catalog"), RES.resolve("openapi"));
        System.out.println(report.entities() + " catalog entities (" + report.components() + " components, " + report.apis() + " APIs, " + report.groups() + " groups); checks per entity:");
        System.out.printf("%-58s %10s%n", "check", "failing");
        report.failures().forEach((check, who) -> System.out.printf("%-58s %10d   %s%n", check, who.size(), String.join(", ", who.stream().limit(4).toList()) + (who.size() > 4 ? ", ..." : "")));
        System.out.printf("components and APIs passing every check: %d of %d%n", report.passing(), report.entities() - report.groups());
    }
}
