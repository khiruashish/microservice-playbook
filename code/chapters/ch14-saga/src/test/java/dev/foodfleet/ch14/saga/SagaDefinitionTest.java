package dev.foodfleet.ch14.saga;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SagaDefinitionTest {

    SagaStep<String> c = SagaStep.compensatable("c", (id, x) -> { }, (id, x) -> { });
    SagaStep<String> p = SagaStep.pivot("p", (id, x) -> { });
    SagaStep<String> r = SagaStep.retriable("r", (id, x) -> { });

    @Test
    void compensatableStepsMustComeBeforeThePivot() {
        assertThatThrownBy(() -> new SagaDefinition<>("bad", List.of(c, p, c))).hasMessageContaining("after the pivot");
        assertThatThrownBy(() -> new SagaDefinition<>("bad", List.of(r, c))).hasMessageContaining("after the pivot");
    }

    @Test
    void atMostOnePivot() {
        assertThatThrownBy(() -> new SagaDefinition<>("bad", List.of(c, p, p))).hasMessageContaining("more than one pivot");
    }

    @Test
    void onlyCompensatableStepsHaveCompensations() {
        assertThatThrownBy(() -> new SagaStep<String>("x", StepKind.PIVOT, (id, x) -> { }, (id, x) -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
