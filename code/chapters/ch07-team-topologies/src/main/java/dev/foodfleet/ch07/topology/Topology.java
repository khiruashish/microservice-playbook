package dev.foodfleet.ch07.topology;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An organisation's socio-technical design as data: who the teams are, what they own, how the
 * software depends on itself, and how the teams have agreed to interact. Everything the analyses in
 * this chapter check is derived from this one model, so it can live in version control and be
 * reviewed like code.
 */
public record Topology(String name, LocalDate asOf, List<Team> teams, List<Domain> domains,
                       List<Component> components, List<Dependency> dependencies, List<Interaction> interactions) {

    /** The four Team Topologies types, plus LAYER for teams organised around a technology layer. */
    public enum TeamType { STREAM_ALIGNED, PLATFORM, ENABLING, COMPLICATED_SUBSYSTEM, LAYER }

    /** Team Topologies' interaction modes. */
    public enum Mode { COLLABORATION, X_AS_A_SERVICE, FACILITATING }

    /** Domain complexity in Team Topologies' sense; the weight feeds the cognitive-load estimate. */
    public enum Complexity {
        SIMPLE(1), COMPLICATED(2), COMPLEX(3);

        private final int weight;

        Complexity(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }
    }

    public record Team(String name, TeamType type, int members) {
        public Team {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
            if (members < 1) {
                throw new IllegalArgumentException(name + ": a team needs at least one member");
            }
        }
    }

    public record Domain(String name, Complexity complexity) {
    }

    /**
     * A deployable or app module.
     *
     * @param path      repository path, used for CODEOWNERS
     * @param owner     the team that builds and runs it
     * @param dataOwner the team that must approve its schema changes, or {@code null} if it keeps no data
     */
    public record Component(String name, String path, String owner, String domain, String dataOwner) {
        public Component {
            Objects.requireNonNull(name);
            Objects.requireNonNull(path);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(domain);
        }
    }

    /** {@code from} calls, consumes events from, runs on, or otherwise needs {@code to}. */
    public record Dependency(String from, String to, boolean async) {
    }

    /** An agreed way of working between two teams, and when it started. */
    public record Interaction(String teamA, String teamB, Mode mode, LocalDate since) {
        public boolean involves(String team) {
            return teamA.equals(team) || teamB.equals(team);
        }
    }

    /** A typical piece of work: which components it changes, and whether it changes a schema. */
    public record Feature(String name, List<String> components, boolean changesSchema) {
        public Feature {
            components = List.copyOf(components);
        }
    }

    public Topology {
        teams = List.copyOf(teams);
        domains = List.copyOf(domains);
        components = List.copyOf(components);
        dependencies = List.copyOf(dependencies);
        interactions = List.copyOf(interactions);
        Set<String> teamNames = unique(teams.stream().map(Team::name).toList(), "team");
        Set<String> domainNames = unique(domains.stream().map(Domain::name).toList(), "domain");
        Set<String> componentNames = unique(components.stream().map(Component::name).toList(), "component");
        for (Component c : components) {
            require(teamNames.contains(c.owner()), c.name() + ": unknown owner team " + c.owner());
            require(c.dataOwner() == null || teamNames.contains(c.dataOwner()), c.name() + ": unknown data owner " + c.dataOwner());
            require(domainNames.contains(c.domain()), c.name() + ": unknown domain " + c.domain());
        }
        for (Dependency d : dependencies) {
            require(componentNames.contains(d.from()) && componentNames.contains(d.to()), "Unknown component in " + d);
        }
        for (Interaction i : interactions) {
            require(teamNames.contains(i.teamA()) && teamNames.contains(i.teamB()), "Unknown team in " + i);
            require(!i.teamA().equals(i.teamB()), "A team does not interact with itself: " + i);
        }
    }

    public Map<String, Component> componentsByName() {
        return components.stream().collect(Collectors.toMap(Component::name, Function.identity()));
    }

    public Map<String, Team> teamsByName() {
        return teams.stream().collect(Collectors.toMap(Team::name, Function.identity()));
    }

    public Map<String, Domain> domainsByName() {
        return domains.stream().collect(Collectors.toMap(Domain::name, Function.identity()));
    }

    public String ownerOf(String component) {
        Component c = componentsByName().get(component);
        if (c == null) {
            throw new IllegalArgumentException(name + ": no component " + component);
        }
        return c.owner();
    }

    private static Set<String> unique(List<String> names, String what) {
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            require(seen.add(n), "Duplicate " + what + " " + n);
        }
        return seen;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
