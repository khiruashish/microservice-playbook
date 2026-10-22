package dev.foodfleet.ch06.report;

import dev.foodfleet.ch06.decomposition.CoChangeAnalyzer;
import dev.foodfleet.ch06.decomposition.CoChangeAnalyzer.Coupling;
import dev.foodfleet.ch06.decomposition.DecompositionEvaluator;
import dev.foodfleet.ch06.decomposition.DecompositionEvaluator.Evaluation;
import dev.foodfleet.ch06.decomposition.DecompositionEvaluator.UseCaseMetrics;
import dev.foodfleet.ch06.decomposition.FoodFleetModel;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Renders the comparison as plain text tables. Pure: no I/O, so it is unit-testable. */
public final class DecompositionReport {

    private final DecompositionEvaluator evaluator = new DecompositionEvaluator();
    private final CoChangeAnalyzer analyzer = new CoChangeAnalyzer();

    /** Renders the report from the bundled {@code id|module,module} commit format. */
    public String render(List<String> commitLog) {
        return renderCommits(CoChangeAnalyzer.parseCommitLog(commitLog));
    }

    /** Renders the report from module-level commits (for example from {@code GitLogImporter}). */
    public String renderCommits(List<Set<String>> commits) {
        StringBuilder out = new StringBuilder();
        out.append("== Candidate decompositions (place-order = 3 dishes) ==\n");
        out.append(String.format(Locale.ROOT, "%-38s %8s %10s %7s %12s %10s %11s %12s%n",
                "Decomposition", "Services", "Sync hops", "Async", "Availability", "Cohesion", "Max teams/", "Max services/"));
        out.append(String.format(Locale.ROOT, "%-38s %8s %10s %7s %12s %10s %11s %12s%n",
                "", "", "(checkout)", "msgs", "(checkout)", "", "service", "team"));
        for (var candidate : FoodFleetModel.candidates()) {
            Evaluation e = evaluator.evaluate(FoodFleetModel.model(), candidate);
            UseCaseMetrics checkout = e.useCases().get(FoodFleetModel.PLACE_ORDER);
            out.append(String.format(Locale.ROOT, "%-38s %8d %10d %7d %11.2f%% %9.0f%% %11d %12d%n",
                    e.decomposition(), e.services(), checkout.syncHops(), checkout.asyncMessages(),
                    checkout.availability() * 100, e.cohesion() * 100, e.maxTeamsPerService(), e.maxServicesPerTeam()));
        }

        out.append("\n== Strongest change couplings (").append(commits.size()).append(" commits) ==\n");
        for (Coupling c : analyzer.couplings(commits, 3).stream().limit(8).toList()) {
            out.append(String.format(Locale.ROOT, "%-20s %-20s %3d together  %3.0f%%%n",
                    c.a(), c.b(), c.coChanges(), c.degree() * 100));
        }
        out.append("\n== Groups suggested by co-change (degree >= 60%, >= 3 commits) ==\n");
        for (Set<String> group : analyzer.suggestGroups(commits, 0.6, 3)) {
            out.append(" - ").append(String.join(", ", group)).append('\n');
        }
        return out.toString();
    }
}
