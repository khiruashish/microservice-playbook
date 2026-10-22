package dev.foodfleet.ch06.decomposition;

import static dev.foodfleet.ch06.decomposition.FoodFleetModel.BROWSE_MENU;
import static dev.foodfleet.ch06.decomposition.FoodFleetModel.PLACE_ORDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.foodfleet.ch06.decomposition.DecompositionEvaluator.Evaluation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The chapter's comparison table, pinned as tests so the numbers in the docs can't drift from the code. */
class DecompositionEvaluatorTest {

    private final DecompositionEvaluator evaluator = new DecompositionEvaluator();
    private final DomainModel model = FoodFleetModel.model();

    @Test
    void perEntitySplit_isChattyAndLessAvailable() {
        Evaluation e = evaluator.evaluate(model, FoodFleetModel.perEntity());

        assertThat(e.services()).isEqualTo(14);
        assertThat(e.useCases().get(PLACE_ORDER).syncHops()).isEqualTo(16);
        assertThat(e.useCases().get(PLACE_ORDER).servicesOnSyncPath()).isEqualTo(11);
        assertThat(e.useCases().get(PLACE_ORDER).availability()).isCloseTo(0.98906, within(0.00001));
        assertThat(e.useCases().get(BROWSE_MENU).syncHops()).isEqualTo(12);
        assertThat(e.cohesion()).isZero();
        assertThat(e.maxServicesPerTeam()).isEqualTo(4); // Restaurant Partner team owns four services
    }

    @Test
    void capabilitySplit_keepsChattyPairsTogether() {
        Evaluation e = evaluator.evaluate(model, FoodFleetModel.byBusinessCapability());

        assertThat(e.services()).isEqualTo(9);
        assertThat(e.useCases().get(PLACE_ORDER).syncHops()).isEqualTo(9);
        assertThat(e.useCases().get(PLACE_ORDER).servicesOnSyncPath()).isEqualTo(6);
        assertThat(e.useCases().get(PLACE_ORDER).availability()).isCloseTo(0.99401, within(0.00001));
        assertThat(e.useCases().get(BROWSE_MENU).syncHops()).isEqualTo(1);
        assertThat(e.cohesion()).isCloseTo(18.0 / 31, within(0.0001)); // 18 of 31 calls stay inside a service
        assertThat(e.maxTeamsPerService()).isEqualTo(1);
    }

    @Test
    void coarseSplit_sharesServicesBetweenTeams() {
        Evaluation e = evaluator.evaluate(model, FoodFleetModel.coarse());

        assertThat(e.useCases().get(PLACE_ORDER).syncHops()).isEqualTo(6);
        assertThat(e.teamsPerService()).containsEntry("customer-facing", 3).containsEntry("fulfilment", 3);
        assertThat(e.maxTeamsPerService()).isEqualTo(3);
    }

    @Test
    void monolith_hasNoHopsButEveryTeamInOneDeployable() {
        Evaluation e = evaluator.evaluate(model, FoodFleetModel.monolith());

        assertThat(e.useCases().get(PLACE_ORDER).syncHops()).isZero();
        assertThat(e.useCases().get(PLACE_ORDER).availability()).isCloseTo(0.999, within(0.0000001));
        assertThat(e.cohesion()).isEqualTo(1.0);
        assertThat(e.maxTeamsPerService()).isEqualTo(6);
    }

    @Test
    void everyCapabilityMustBeAssigned() {
        Decomposition incomplete = Decomposition.of("broken", Map.of("ordering", List.of("order")));

        assertThatThrownBy(() -> evaluator.evaluate(model, incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not assigned to a service");
    }

    @Test
    void aCapabilityCannotLiveInTwoServices() {
        assertThatThrownBy(() -> Decomposition.of("broken", Map.of(
                "a", List.of("order"),
                "b", List.of("order"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("order assigned to more than one service");
    }
}
