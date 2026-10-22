package dev.foodfleet.ch71.lint;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A ratchet for a legacy API: its current findings are recorded, and a change is judged only on the findings it adds.
 * Findings are identified by rule and JSON pointer, so a problem that moves counts as new.
 */
public final class Baseline {

    public record Result(List<Linter.Finding> added, List<Linter.Finding> fixed, List<Linter.Finding> kept) {
    }

    private final List<Linter.Finding> recorded;

    private Baseline(List<Linter.Finding> recorded) {
        this.recorded = List.copyOf(recorded);
    }

    public static Baseline of(List<Linter.Finding> findings) {
        return new Baseline(findings);
    }

    public Result compare(List<Linter.Finding> now) {
        Set<Linter.Finding> before = Set.copyOf(recorded);
        Set<Linter.Finding> after = Set.copyOf(now);
        return new Result(now.stream().filter(f -> !before.contains(f)).toList(), recorded.stream().filter(f -> !after.contains(f)).toList(),
                now.stream().filter(before::contains).collect(Collectors.toList()));
    }
}
