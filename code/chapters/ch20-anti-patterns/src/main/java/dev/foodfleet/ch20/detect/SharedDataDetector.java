package dev.foodfleet.ch20.detect;

import dev.foodfleet.ch20.detect.Finding.AntiPattern;
import dev.foodfleet.ch20.detect.Finding.Severity;
import dev.foodfleet.ch20.model.SystemModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Coupling through things other than APIs:
 * <ul>
 *   <li><b>shared database</b>: other services writing (worst) or reading the owner's tables directly;</li>
 *   <li><b>shared domain library</b>: business classes in a jar every service depends on, so a change
 *       to "Order" means rebuilding and redeploying everyone;</li>
 *   <li><b>version skew</b>: one shared library at many versions across services: bugs fixed in some,
 *       not others, and an upgrade nobody can schedule.</li>
 * </ul>
 */
public final class SharedDataDetector implements Detector {

    private final int maxVersions;

    public SharedDataDetector(int maxVersions) {
        this.maxVersions = maxVersions;
    }

    @Override
    public List<Finding> detect(SystemModel model) {
        List<Finding> findings = new ArrayList<>();
        for (SystemModel.Table t : model.tables()) {
            if (!t.writers().isEmpty()) {
                findings.add(new Finding(AntiPattern.SHARED_DATABASE, Severity.HIGH, t.name(),
                        "owned by %s, also written by %s".formatted(t.owner(), String.join(",", new TreeSet<>(t.writers()))),
                        "only the owner writes; others call its API or send it commands (chapter 12)"));
            } else if (!t.readers().isEmpty()) {
                findings.add(new Finding(AntiPattern.SHARED_DATABASE, Severity.MEDIUM, t.name(),
                        "owned by %s, read directly by %s".formatted(t.owner(), String.join(",", new TreeSet<>(t.readers()))),
                        "publish events or expose a read API; readers keep their own copy (chapters 12, 19)"));
            }
        }
        for (SystemModel.Library lib : model.libraries()) {
            Map<String, String> versions = lib.versionByService();
            if (lib.kind().equals("domain") && versions.size() >= 2) {
                findings.add(new Finding(AntiPattern.SHARED_DOMAIN_LIBRARY, Severity.HIGH, lib.name(),
                        "business classes shared by %d services: %s".formatted(versions.size(),
                                String.join(",", new TreeSet<>(versions.keySet()))),
                        "each service owns its model; share contracts (OpenAPI, event schemas), not classes"));
            }
            TreeSet<String> distinct = new TreeSet<>(versions.values());
            TreeSet<String> majors = new TreeSet<>(distinct.stream().map(v -> v.split("\\.")[0]).toList());
            if (distinct.size() > maxVersions || majors.size() > 1) {
                findings.add(new Finding(AntiPattern.LIBRARY_VERSION_SKEW, Severity.MEDIUM, lib.name(),
                        "%d versions in use (%s) across %d services".formatted(distinct.size(),
                                String.join(", ", distinct), versions.size()),
                        "keep shared libraries small and stable; automate upgrades (Renovate/Dependabot); a BOM"));
            }
        }
        return findings;
    }
}
