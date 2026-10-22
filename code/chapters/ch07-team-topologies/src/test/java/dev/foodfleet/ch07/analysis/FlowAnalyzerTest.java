package dev.foodfleet.ch07.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.foodfleet.ch07.topology.FoodFleetTeams;
import dev.foodfleet.ch07.topology.Topology.Feature;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowAnalyzerTest {

    private final FlowAnalyzer analyzer = new FlowAnalyzer();
    private final Feature noteToRestaurant = FoodFleetTeams.features().getFirst();

    @Test
    void layerTeamsAndASharedDatabaseTurnOneFeatureIntoFourTeams() {
        assertThat(analyzer.teamsFor(FoodFleetTeams.before(), noteToRestaurant))
                .containsExactly("Backend A", "DBA", "Mobile", "Web");
    }

    @Test
    void streamAlignedTeamsOwningTheirDataNeedTwo() {
        assertThat(analyzer.teamsFor(FoodFleetTeams.after(), noteToRestaurant))
                .containsExactly("Checkout", "Restaurant Partner");
    }

    @Test
    void sameRoadmapHalvesTheTeamsPerFeature() {
        List<Feature> roadmap = FoodFleetTeams.features();
        FlowAnalyzer.Result before = analyzer.analyze(FoodFleetTeams.before(), roadmap);
        FlowAnalyzer.Result after = analyzer.analyze(FoodFleetTeams.after(), roadmap);

        assertThat(before.averageTeams()).isCloseTo(3.375, within(1e-9));
        assertThat(before.handoffs()).isEqualTo(19);
        assertThat(before.singleTeamFeatures()).isZero();
        assertThat(after.averageTeams()).isCloseTo(1.75, within(1e-9));
        assertThat(after.handoffs()).isEqualTo(6);
        assertThat(after.singleTeamFeatures()).isEqualTo(2);
    }
}
