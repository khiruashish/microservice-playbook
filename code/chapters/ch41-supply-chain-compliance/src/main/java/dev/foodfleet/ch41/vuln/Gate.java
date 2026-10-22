package dev.foodfleet.ch41.vuln;

import dev.foodfleet.ch41.vuln.Advisory.Severity;
import dev.foodfleet.ch41.vuln.Scanner.Finding;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The CI gate: a build fails on a finding at or above {@code failAt} that has a fix to upgrade to, unless a waiver
 * covers it. A waiver names one advisory and one package, gives a reason (not reachable, mitigated, no fix yet) and
 * expires, so accepted risk is re-examined instead of forgotten. Findings without a fix are reported, not blocking:
 * blocking them stops every release until someone else ships a patch.
 */
public final class Gate {

    public record Waiver(String advisoryId, String packageName, String reason, LocalDate expires) {
    }

    public record Result(List<Finding> blocking, List<Finding> waived, List<Finding> reported) {
        public boolean passed() {
            return blocking.isEmpty();
        }
    }

    private final Severity failAt;
    private final List<Waiver> waivers;

    public Gate(Severity failAt, List<Waiver> waivers) {
        this.failAt = failAt;
        this.waivers = List.copyOf(waivers);
    }

    public Result evaluate(List<Finding> findings, LocalDate today) {
        List<Finding> blocking = new ArrayList<>();
        List<Finding> waived = new ArrayList<>();
        List<Finding> reported = new ArrayList<>();
        Set<Severity> serious = failAt == Severity.CRITICAL ? Set.of(Severity.CRITICAL)
                : failAt == Severity.HIGH ? Set.of(Severity.HIGH, Severity.CRITICAL)
                : Set.of(Severity.MODERATE, Severity.HIGH, Severity.CRITICAL);
        for (Finding f : findings) {
            if (!serious.contains(f.advisory().severity()) || f.fixedIn() == null) {
                reported.add(f);
            } else if (waivers.stream().anyMatch(w -> w.advisoryId().equals(f.advisory().id())
                    && w.packageName().equals(f.component().mavenName()) && !today.isAfter(w.expires()))) {
                waived.add(f);
            } else {
                blocking.add(f);
            }
        }
        return new Result(blocking, waived, reported);
    }
}
