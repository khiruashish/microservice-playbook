package dev.foodfleet.ch07.analysis;

import dev.foodfleet.ch07.topology.Topology;
import dev.foodfleet.ch07.topology.Topology.Complexity;
import dev.foodfleet.ch07.topology.Topology.Component;
import dev.foodfleet.ch07.topology.Topology.Domain;
import dev.foodfleet.ch07.topology.Topology.Team;
import dev.foodfleet.ch07.topology.Topology.TeamType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A rough, explainable estimate of each team's cognitive load, from the heuristics in
 * <em>Team Topologies</em> (Skelton &amp; Pais): a team can own two or three simple domains; a team
 * with a complex domain should own nothing else; no team should own two complicated domains.
 *
 * <p>Weights simple = 1, complicated = 2, complex = 3 with a budget of 3 reproduce exactly those
 * rules. It's a conversation starter, not a measurement of people: ask the team, too.
 */
public final class CognitiveLoadAnalyzer {

    public static final int BUDGET = 3;
    public static final int MAX_TEAM_SIZE = 9;
    public static final int MIN_TEAM_SIZE = 4;

    public record TeamLoad(String team, TeamType type, int members, List<Domain> domains, int load,
                           List<String> findings) {
        public boolean overloaded() {
            return load > BUDGET;
        }
    }

    public List<TeamLoad> analyze(Topology topology) {
        Map<String, Domain> domains = topology.domainsByName();
        List<TeamLoad> result = new ArrayList<>();
        for (Team team : topology.teams()) {
            Set<Domain> owned = new LinkedHashSet<>();
            for (Component c : topology.components()) {
                if (c.owner().equals(team.name())) {
                    owned.add(domains.get(c.domain()));
                }
            }
            List<Domain> sorted = owned.stream()
                    .sorted(Comparator.comparing((Domain d) -> d.complexity()).reversed().thenComparing(Domain::name))
                    .toList();
            int load = sorted.stream().mapToInt(d -> d.complexity().weight()).sum();
            result.add(new TeamLoad(team.name(), team.type(), team.members(), sorted, load, findings(team, sorted, load)));
        }
        return result;
    }

    private static List<String> findings(Team team, List<Domain> domains, int load) {
        List<String> findings = new ArrayList<>();
        long complex = domains.stream().filter(d -> d.complexity() == Complexity.COMPLEX).count();
        long complicated = domains.stream().filter(d -> d.complexity() == Complexity.COMPLICATED).count();
        long simple = domains.stream().filter(d -> d.complexity() == Complexity.SIMPLE).count();
        if (complex > 0 && domains.size() > 1) {
            findings.add("owns a complex domain plus " + (domains.size() - 1) + " more: a complex domain should be the team's only one");
        }
        if (complicated >= 2) {
            findings.add("owns " + complicated + " complicated domains: at most one per team");
        }
        if (simple > 3) {
            findings.add("owns " + simple + " simple domains: two or three is the comfortable maximum");
        }
        if (load > BUDGET && findings.isEmpty()) {
            findings.add("cognitive load " + load + " exceeds the budget of " + BUDGET);
        }
        if (team.members() > MAX_TEAM_SIZE) {
            findings.add(team.members() + " people: beyond about " + MAX_TEAM_SIZE + ", trust and communication break down; split the team");
        }
        if (team.members() < MIN_TEAM_SIZE && team.type() != TeamType.ENABLING) {
            findings.add(team.members() + " people: too few to build, run and be on call for what it owns");
        }
        return findings;
    }
}
