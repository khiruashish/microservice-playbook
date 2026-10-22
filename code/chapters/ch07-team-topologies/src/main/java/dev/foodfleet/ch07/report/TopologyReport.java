package dev.foodfleet.ch07.report;

import dev.foodfleet.ch07.analysis.CognitiveLoadAnalyzer;
import dev.foodfleet.ch07.analysis.CognitiveLoadAnalyzer.TeamLoad;
import dev.foodfleet.ch07.analysis.ConwayAnalyzer;
import dev.foodfleet.ch07.analysis.ConwayAnalyzer.Requirement;
import dev.foodfleet.ch07.analysis.FlowAnalyzer;
import dev.foodfleet.ch07.analysis.FlowAnalyzer.FeatureFlow;
import dev.foodfleet.ch07.topology.Topology;
import dev.foodfleet.ch07.topology.Topology.Feature;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Renders the three analyses for one topology as plain text. Pure: no I/O. */
public final class TopologyReport {

    private final FlowAnalyzer flow = new FlowAnalyzer();
    private final CognitiveLoadAnalyzer load = new CognitiveLoadAnalyzer();
    private final ConwayAnalyzer conway = new ConwayAnalyzer();

    public String render(Topology topology, List<Feature> features) {
        StringBuilder out = new StringBuilder();
        out.append("######## ").append(topology.name()).append(" (as of ").append(topology.asOf()).append(") ########\n\n");

        FlowAnalyzer.Result f = flow.analyze(topology, features);
        out.append("== Flow: teams needed per feature ==\n");
        for (FeatureFlow ff : f.flows()) {
            out.append(String.format(Locale.ROOT, "%-24s %d  %s%n", ff.feature(), ff.teams().size(), String.join(", ", ff.teams())));
        }
        out.append(String.format(Locale.ROOT, "Average %.2f teams per feature, %d handoffs, %d of %d features need one team%n%n",
                f.averageTeams(), f.handoffs(), f.singleTeamFeatures(), f.flows().size()));

        out.append("== Cognitive load (budget ").append(CognitiveLoadAnalyzer.BUDGET).append(") ==\n");
        for (TeamLoad t : load.analyze(topology)) {
            String domains = t.domains().stream()
                    .map(d -> d.name() + " (" + d.complexity().name().toLowerCase(Locale.ROOT) + ")")
                    .collect(Collectors.joining(", "));
            out.append(String.format(Locale.ROOT, "%-20s %-22s %2d people  load %d%s  %s%n",
                    t.team(), t.type(), t.members(), t.load(), t.overloaded() ? " !" : "  ", domains.isEmpty() ? "-" : domains));
            t.findings().forEach(x -> out.append("    ! ").append(x).append('\n'));
        }

        ConwayAnalyzer.Result c = conway.analyze(topology, features);
        out.append(String.format(Locale.ROOT, "%n== Conway: socio-technical congruence %d/%d = %.0f%% ==%n",
                c.met(), c.required(), c.congruence() * 100));
        for (Requirement r : c.missing()) {
            out.append("  missing ").append(r.pair()).append(": ").append(String.join("; ", r.reasons())).append('\n');
        }
        c.unexplained().forEach(p -> out.append("  declared without a reason: ").append(p).append('\n'));
        c.modeWarnings().forEach(w -> out.append("  ! ").append(w).append('\n'));
        return out.toString();
    }
}
