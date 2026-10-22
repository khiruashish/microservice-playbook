package dev.foodfleet.ch07.analysis;

import dev.foodfleet.ch07.topology.Topology;
import dev.foodfleet.ch07.topology.Topology.Dependency;
import dev.foodfleet.ch07.topology.Topology.Feature;
import dev.foodfleet.ch07.topology.Topology.Interaction;
import dev.foodfleet.ch07.topology.Topology.Mode;
import dev.foodfleet.ch07.topology.Topology.Team;
import dev.foodfleet.ch07.topology.Topology.TeamType;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Conway's Law, checked. Wherever the work makes two teams depend on each other (a runtime
 * dependency between their components, or a feature that needs both), they need an agreed way to
 * interact. <b>Socio-technical congruence</b> is the share of those coordination needs that are met
 * by a declared interaction.
 *
 * <p>Also checks interaction modes against Team Topologies' guidance: collaboration is time-boxed,
 * platforms are consumed as a service, and facilitating is what enabling teams do.
 */
public final class ConwayAnalyzer {

    public static final int MAX_COLLABORATION_MONTHS = 6;

    /** An unordered pair of teams. */
    public record Pair(String a, String b) implements Comparable<Pair> {
        public static Pair of(String x, String y) {
            return x.compareTo(y) <= 0 ? new Pair(x, y) : new Pair(y, x);
        }

        @Override
        public int compareTo(Pair o) {
            int c = a.compareTo(o.a);
            return c != 0 ? c : b.compareTo(o.b);
        }

        @Override
        public String toString() {
            return a + " <-> " + b;
        }
    }

    public record Requirement(Pair pair, Set<String> reasons) {
    }

    public record Result(int required, int met, double congruence, List<Requirement> missing,
                         List<Pair> unexplained, List<String> modeWarnings) {
    }

    private final FlowAnalyzer flow = new FlowAnalyzer();

    public Result analyze(Topology topology, List<Feature> features) {
        Map<Pair, Set<String>> required = new TreeMap<>();
        for (Dependency d : topology.dependencies()) {
            String from = topology.ownerOf(d.from());
            String to = topology.ownerOf(d.to());
            if (!from.equals(to)) {
                required.computeIfAbsent(Pair.of(from, to), p -> new TreeSet<>()).add(d.from() + " -> " + d.to());
            }
        }
        for (Feature f : features) {
            List<String> teams = new ArrayList<>(flow.teamsFor(topology, f));
            for (int i = 0; i < teams.size(); i++) {
                for (int j = i + 1; j < teams.size(); j++) {
                    required.computeIfAbsent(Pair.of(teams.get(i), teams.get(j)), p -> new TreeSet<>())
                            .add("feature: " + f.name());
                }
            }
        }

        Set<Pair> declared = new TreeSet<>();
        Set<Pair> declaredWorking = new TreeSet<>();
        for (Interaction i : topology.interactions()) {
            Pair p = Pair.of(i.teamA(), i.teamB());
            declared.add(p);
            if (i.mode() != Mode.FACILITATING) {
                declaredWorking.add(p);
            }
        }

        List<Requirement> missing = new ArrayList<>();
        required.forEach((pair, reasons) -> {
            if (!declared.contains(pair)) {
                missing.add(new Requirement(pair, reasons));
            }
        });
        List<Pair> unexplained = declaredWorking.stream().filter(p -> !required.containsKey(p)).toList();
        int met = required.size() - missing.size();
        double congruence = required.isEmpty() ? 1.0 : (double) met / required.size();
        return new Result(required.size(), met, congruence, missing, unexplained, modeWarnings(topology));
    }

    private static List<String> modeWarnings(Topology topology) {
        Map<String, Team> teams = topology.teamsByName();
        List<String> warnings = new ArrayList<>();
        for (Interaction i : topology.interactions()) {
            Pair p = Pair.of(i.teamA(), i.teamB());
            TeamType a = teams.get(i.teamA()).type();
            TeamType b = teams.get(i.teamB()).type();
            if (i.mode() == Mode.COLLABORATION) {
                long months = ChronoUnit.MONTHS.between(i.since(), topology.asOf());
                if (months > MAX_COLLABORATION_MONTHS) {
                    warnings.add(p + ": collaborating for " + months + " months; time-box collaboration, then move to X-as-a-Service");
                }
                if (a == TeamType.PLATFORM || b == TeamType.PLATFORM) {
                    warnings.add(p + ": collaboration with the platform team; platforms should be consumed as a service");
                }
            }
            if (i.mode() == Mode.FACILITATING && a != TeamType.ENABLING && b != TeamType.ENABLING) {
                warnings.add(p + ": facilitating without an enabling team; is this really collaboration?");
            }
        }
        return warnings;
    }
}
