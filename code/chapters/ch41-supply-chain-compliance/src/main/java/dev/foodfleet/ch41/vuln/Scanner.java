package dev.foodfleet.ch41.vuln;

import dev.foodfleet.ch41.sbom.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A vulnerability scanner is three things: an inventory (the SBOM), a database (advisories), and version matching.
 * This one matches Maven components by "groupId:artifactId" and Maven version order, which is how the GitHub Advisory
 * Database describes them. (Scanners that match by CPE, NVD's product identifiers, find more and get more wrong.)
 */
public final class Scanner {

    public record Finding(Component component, Advisory advisory, String fixedIn) {
    }

    private final OsvDatabase db;

    public Scanner(OsvDatabase db) {
        this.db = db;
    }

    /** Findings for the components; {@code knownBy} keeps only advisories published by then (null: all). */
    public List<Finding> scan(List<Component> components, Instant knownBy) {
        Map<String, Finding> unique = new LinkedHashMap<>();
        for (Component c : components) {
            if (!"maven".equals(c.type())) {
                continue;
            }
            MavenVersion v = MavenVersion.parse(c.version());
            for (Advisory a : db.forPackage(c.mavenName())) {
                if (knownBy != null && a.published().isAfter(knownBy)) {
                    continue;
                }
                a.affects(c.mavenName(), v).ifPresent(m ->
                        unique.putIfAbsent(c.mavenName() + "@" + c.version() + " " + a.id(), new Finding(c, a, m.fixedIn())));
            }
        }
        List<Finding> out = new ArrayList<>(unique.values());
        out.sort(Comparator.comparing((Finding f) -> f.advisory().severity()).reversed()
                .thenComparing(f -> f.component().mavenName()));
        return out;
    }
}
