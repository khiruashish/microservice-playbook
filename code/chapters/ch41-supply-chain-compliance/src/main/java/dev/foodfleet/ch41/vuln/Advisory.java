package dev.foodfleet.ch41.vuln;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One OSV advisory (the format of the GitHub Advisory Database, OSV.dev and others), reduced to what matching
 * needs: which packages and versions it affects, how bad it is, and when it became known.
 */
public record Advisory(String id, List<String> aliases, String summary, Severity severity, Instant published,
        Instant withdrawn, List<Affected> affected) {

    public enum Severity { LOW, MODERATE, HIGH, CRITICAL, UNKNOWN }

    /** One package's affected versions: ranges of introduced/fixed/last_affected events and explicit versions. */
    public record Affected(String packageName, List<Range> ranges, List<String> versions) {
    }

    /** [introduced, fixed) or [introduced, lastAffected]; "0" means from the first version. */
    public record Range(String introduced, String fixed, String lastAffected) {

        boolean contains(MavenVersion v) {
            if (!introduced.equals("0") && v.compareTo(MavenVersion.parse(introduced)) < 0) {
                return false;
            }
            if (fixed != null && v.compareTo(MavenVersion.parse(fixed)) >= 0) {
                return false;
            }
            return lastAffected == null || v.compareTo(MavenVersion.parse(lastAffected)) <= 0;
        }
    }

    /** Whether this advisory affects {@code version} of {@code packageName}, and the first fixed version if any. */
    public Optional<Match> affects(String packageName, MavenVersion version) {
        if (withdrawn != null) {
            return Optional.empty();
        }
        for (Affected a : affected) {
            if (!a.packageName().equals(packageName)) {
                continue;
            }
            for (String listed : a.versions()) {
                if (MavenVersion.parse(listed).equals(version)) {
                    return Optional.of(new Match(this, null));
                }
            }
            for (Range r : a.ranges()) {
                if (r.contains(version)) {
                    return Optional.of(new Match(this, r.fixed()));
                }
            }
        }
        return Optional.empty();
    }

    public record Match(Advisory advisory, String fixedIn) {
    }

    public String displayId() {
        return aliases.stream().filter(a -> a.startsWith("CVE-")).findFirst().orElse(id);
    }
}
