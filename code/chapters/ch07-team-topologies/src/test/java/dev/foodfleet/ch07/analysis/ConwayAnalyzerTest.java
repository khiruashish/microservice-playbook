package dev.foodfleet.ch07.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.foodfleet.ch07.analysis.ConwayAnalyzer.Pair;
import dev.foodfleet.ch07.analysis.ConwayAnalyzer.Requirement;
import dev.foodfleet.ch07.topology.FoodFleetTeams;
import org.junit.jupiter.api.Test;

class ConwayAnalyzerTest {

    private final ConwayAnalyzer analyzer = new ConwayAnalyzer();

    @Test
    void beforeTheReorgAThirdOfCoordinationNeedsHadNoAgreedChannel() {
        ConwayAnalyzer.Result result = analyzer.analyze(FoodFleetTeams.before(), FoodFleetTeams.features());

        assertThat(result.required()).isEqualTo(13);
        assertThat(result.met()).isEqualTo(8);
        assertThat(result.missing()).extracting(Requirement::pair)
                .contains(Pair.of("Mobile", "DBA"), Pair.of("Mobile", "Ops"), Pair.of("Web", "Ops"));
        assertThat(result.modeWarnings()).hasSize(4).allMatch(w -> w.contains("time-box collaboration"));
    }

    @Test
    void afterTheReorgTheNewTeamWasNeverOnboardedToThePlatform() {
        ConwayAnalyzer.Result result = analyzer.analyze(FoodFleetTeams.after(), FoodFleetTeams.features());

        assertThat(result.congruence()).isCloseTo(12.0 / 13, within(1e-9));
        assertThat(result.missing()).singleElement().satisfies(r -> {
            assertThat(r.pair()).isEqualTo(Pair.of("Platform", "Dispatch"));
            assertThat(r.reasons()).contains("dispatch-optimizer -> k8s-platform");
        });
        assertThat(result.unexplained()).isEmpty();
        assertThat(result.modeWarnings()).containsExactly(
                "Dispatch <-> Logistics: collaborating for 7 months; time-box collaboration, then move to X-as-a-Service");
    }
}
