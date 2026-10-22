package dev.foodfleet.ch07.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch07.analysis.CognitiveLoadAnalyzer.TeamLoad;
import dev.foodfleet.ch07.topology.FoodFleetTeams;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CognitiveLoadAnalyzerTest {

    private final CognitiveLoadAnalyzer analyzer = new CognitiveLoadAnalyzer();

    @Test
    void layerTeamsOwnSlicesOfManyDomains() {
        Map<String, TeamLoad> loads = byTeam(analyzer.analyze(FoodFleetTeams.before()));

        assertThat(loads.get("Backend B").load()).isEqualTo(8);
        assertThat(loads.get("Backend B").findings())
                .anyMatch(f -> f.startsWith("owns a complex domain plus 3 more"))
                .anyMatch(f -> f.startsWith("11 people"));
        assertThat(loads.get("Mobile").findings()).contains("owns 3 complicated domains: at most one per team");
        assertThat(loads.get("DBA").findings()).anyMatch(f -> f.startsWith("3 people: too few"));
        assertThat(loads.values().stream().filter(TeamLoad::overloaded).map(TeamLoad::team))
                .containsExactlyInAnyOrder("Mobile", "Backend A", "Backend B", "Ops");
    }

    @Test
    void afterTheReorgOnlyThePlatformTeamIsOverBudget() {
        Map<String, TeamLoad> loads = byTeam(analyzer.analyze(FoodFleetTeams.after()));

        assertThat(loads.values().stream().filter(TeamLoad::overloaded).map(TeamLoad::team)).containsExactly("Platform");
        assertThat(loads.get("Dispatch").load()).as("one complex domain and nothing else").isEqualTo(3);
        assertThat(loads.get("Dispatch").findings()).isEmpty();
        assertThat(loads.get("SRE Enablement").findings()).as("enabling teams are small by design").isEmpty();
    }

    private static Map<String, TeamLoad> byTeam(List<TeamLoad> loads) {
        return loads.stream().collect(Collectors.toMap(TeamLoad::team, Function.identity()));
    }
}
