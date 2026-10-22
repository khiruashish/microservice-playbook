package dev.foodfleet.ch06.decomposition;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The facts a decomposition is judged against: what the business does (capabilities), who owns it
 * (teams), and how capabilities interact in real use cases. Collected from Event Storming, use-case
 * walkthroughs and production traces, not invented at a whiteboard.
 */
public record DomainModel(List<Capability> capabilities, List<Interaction> interactions) {

    /** A fine-grained business capability and the team that owns it. */
    public record Capability(String name, String team) {
        public Capability {
            Objects.requireNonNull(name);
            Objects.requireNonNull(team);
        }
    }

    public enum Kind { SYNC, ASYNC }

    /**
     * One step of a use case: {@code from} calls (SYNC) or notifies (ASYNC) {@code to},
     * {@code timesPerUseCase} times per execution.
     */
    public record Interaction(String useCase, String from, String to, Kind kind, int timesPerUseCase) {
        public Interaction {
            if (timesPerUseCase < 1) {
                throw new IllegalArgumentException("timesPerUseCase must be >= 1");
            }
        }
    }

    public DomainModel {
        capabilities = List.copyOf(capabilities);
        interactions = List.copyOf(interactions);
        Set<String> names = new HashSet<>();
        for (Capability c : capabilities) {
            if (!names.add(c.name())) {
                throw new IllegalArgumentException("Duplicate capability " + c.name());
            }
        }
        for (Interaction i : interactions) {
            if (!names.contains(i.from()) || !names.contains(i.to())) {
                throw new IllegalArgumentException("Interaction references unknown capability: " + i);
            }
        }
    }

    public Map<String, Capability> byName() {
        return capabilities.stream().collect(Collectors.toMap(Capability::name, Function.identity()));
    }

    public Set<String> useCases() {
        return interactions.stream().map(Interaction::useCase).collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}
