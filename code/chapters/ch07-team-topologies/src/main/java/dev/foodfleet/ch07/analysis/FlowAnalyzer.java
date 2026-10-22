package dev.foodfleet.ch07.analysis;

import dev.foodfleet.ch07.topology.Topology;
import dev.foodfleet.ch07.topology.Topology.Component;
import dev.foodfleet.ch07.topology.Topology.Feature;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * How many teams does a typical piece of work need? Every extra team is a handoff: a ticket in
 * someone else's backlog, a meeting, a wait. This is the most direct measure of whether the
 * organisation matches the architecture.
 */
public final class FlowAnalyzer {

    public record FeatureFlow(String feature, Set<String> teams) {
        public int handoffs() {
            return teams.size() - 1;
        }
    }

    public record Result(List<FeatureFlow> flows, double averageTeams, long singleTeamFeatures, int handoffs) {
    }

    /**
     * Teams involved = owners of the changed components, plus the data owners of those components
     * when the feature changes a schema (with a shared database, that is the DBA team every time).
     */
    public Set<String> teamsFor(Topology topology, Feature feature) {
        Map<String, Component> components = topology.componentsByName();
        Set<String> teams = new TreeSet<>();
        for (String name : feature.components()) {
            Component c = components.get(name);
            if (c == null) {
                throw new IllegalArgumentException(topology.name() + ": feature " + feature.name()
                        + " changes unknown component " + name);
            }
            teams.add(c.owner());
            if (feature.changesSchema() && c.dataOwner() != null) {
                teams.add(c.dataOwner());
            }
        }
        return teams;
    }

    public Result analyze(Topology topology, List<Feature> features) {
        List<FeatureFlow> flows = features.stream()
                .map(f -> new FeatureFlow(f.name(), teamsFor(topology, f)))
                .toList();
        double average = flows.stream().mapToInt(f -> f.teams().size()).average().orElse(0);
        long single = flows.stream().filter(f -> f.teams().size() == 1).count();
        int handoffs = flows.stream().mapToInt(FeatureFlow::handoffs).sum();
        return new Result(flows, average, single, handoffs);
    }
}
