package dev.foodfleet.ch20.detect;

import dev.foodfleet.ch20.model.ModelParser;
import dev.foodfleet.ch20.model.SystemModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Runs every detector with FoodFleet's thresholds and prints a review-ready report. */
public final class AntiPatternReport {

    /** Thresholds are policy: these are FoodFleet's, written down in ADR-020. */
    public static List<Detector> foodFleetDetectors() {
        return List.of(
                new DeployCouplingDetector(0.7, 5),
                new CallGraphDetector(5, 6, 3, 0.999),
                new NanoServiceDetector(2, 3_000),
                new SharedDataDetector(2));
    }

    public static List<Finding> analyse(SystemModel model) {
        List<Finding> all = new ArrayList<>();
        for (Detector d : foodFleetDetectors()) {
            all.addAll(d.detect(model));
        }
        all.sort(Comparator.comparing(Finding::severity).thenComparing(Finding::pattern)
                .thenComparing(Finding::subject));
        return all;
    }

    public static SystemModel load(String resource) throws IOException {
        try (InputStream in = AntiPatternReport.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("no resource " + resource);
            }
            return ModelParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static String render(SystemModel model, List<Finding> findings) {
        StringBuilder out = new StringBuilder();
        Map<Finding.Severity, Long> bySeverity = new TreeMap<>();
        findings.forEach(f -> bySeverity.merge(f.severity(), 1L, Long::sum));
        out.append("== %s: %d services, %d findings %s ==%n".formatted(model.name(), model.services().size(),
                findings.size(), bySeverity));
        for (Finding f : findings) {
            out.append("%-6s %-30s %s%n".formatted(f.severity(), f.pattern().title, f.subject()));
            out.append("       evidence: %s%n".formatted(f.evidence()));
            out.append("       remedy:   %s%n".formatted(f.remedy()));
        }
        return out.toString();
    }

    public static void main(String[] args) throws IOException {
        for (String resource : List.of("/architecture/foodfleet-2023.model", "/architecture/foodfleet-2024.model")) {
            SystemModel model = load(resource);
            System.out.println(render(model, analyse(model)));
        }
    }
}
