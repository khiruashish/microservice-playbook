package dev.foodfleet.ch06.decomposition;

import dev.foodfleet.ch06.decomposition.DomainModel.Capability;
import dev.foodfleet.ch06.decomposition.DomainModel.Interaction;
import dev.foodfleet.ch06.decomposition.DomainModel.Kind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Scores a candidate decomposition against the domain model. Every number answers a question an
 * architecture review should ask:
 * <ul>
 *   <li><b>Sync hops per use case</b>: how many network round trips does one checkout make?</li>
 *   <li><b>Availability</b>: if each service on the synchronous path is 99.9% available, what's the use case's?</li>
 *   <li><b>Cohesion</b>: what share of interaction volume stays inside a service?</li>
 *   <li><b>Teams per service</b>: does any service need several teams to agree on a release?</li>
 *   <li><b>Services per team</b>: is any team spread too thin?</li>
 * </ul>
 */
public final class DecompositionEvaluator {

    public static final double SERVICE_AVAILABILITY = 0.999;

    public Evaluation evaluate(DomainModel model, Decomposition decomposition) {
        decomposition.requireCovers(model);
        Map<String, Capability> capabilities = model.byName();

        Map<String, UseCaseMetrics> perUseCase = new LinkedHashMap<>();
        for (String useCase : model.useCases()) {
            int syncHops = 0;
            int asyncMessages = 0;
            Set<String> syncPath = new HashSet<>();
            Set<String> touched = new HashSet<>();
            for (Interaction i : model.interactions()) {
                if (!i.useCase().equals(useCase)) {
                    continue;
                }
                String from = decomposition.serviceOf(i.from());
                String to = decomposition.serviceOf(i.to());
                touched.add(from);
                touched.add(to);
                if (i.kind() == Kind.SYNC) {
                    syncPath.add(from);
                    syncPath.add(to);
                    if (!from.equals(to)) {
                        syncHops += i.timesPerUseCase();
                    }
                } else if (!from.equals(to)) {
                    asyncMessages += i.timesPerUseCase();
                }
            }
            double availability = Math.pow(SERVICE_AVAILABILITY, syncPath.size());
            perUseCase.put(useCase, new UseCaseMetrics(syncHops, asyncMessages, touched.size(), syncPath.size(), availability));
        }

        int internal = 0;
        int total = 0;
        for (Interaction i : model.interactions()) {
            total += i.timesPerUseCase();
            if (decomposition.serviceOf(i.from()).equals(decomposition.serviceOf(i.to()))) {
                internal += i.timesPerUseCase();
            }
        }

        Map<String, Set<String>> teamsByService = new TreeMap<>();
        Map<String, Set<String>> servicesByTeam = new TreeMap<>();
        for (Capability c : capabilities.values()) {
            String service = decomposition.serviceOf(c.name());
            teamsByService.computeIfAbsent(service, s -> new HashSet<>()).add(c.team());
            servicesByTeam.computeIfAbsent(c.team(), t -> new HashSet<>()).add(service);
        }
        Map<String, Integer> teamsPerService = new TreeMap<>();
        teamsByService.forEach((s, teams) -> teamsPerService.put(s, teams.size()));
        Map<String, Integer> servicesPerTeam = new HashMap<>();
        servicesByTeam.forEach((t, services) -> servicesPerTeam.put(t, services.size()));

        return new Evaluation(
                decomposition.name(),
                decomposition.services().size(),
                perUseCase,
                total == 0 ? 1.0 : (double) internal / total,
                teamsPerService,
                teamsPerService.values().stream().mapToInt(Integer::intValue).max().orElse(0),
                servicesPerTeam.values().stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    public record UseCaseMetrics(int syncHops, int asyncMessages, int servicesTouched, int servicesOnSyncPath,
                                 double availability) {
    }

    public record Evaluation(String decomposition, int services, Map<String, UseCaseMetrics> useCases,
                             double cohesion, Map<String, Integer> teamsPerService, int maxTeamsPerService,
                             int maxServicesPerTeam) {
    }
}
