package dev.foodfleet.ch35.threatmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ThreatModelTest {

    @Test
    void theCheckoutModelHasNoUnaddressedThreats() {
        ThreatModel model = CheckoutThreatModel.build();
        assertThat(model.threats()).hasSize(69);
        assertThat(model.open()).isEmpty();
    }

    @Test
    void anElementAddedWithoutMitigationsFailsTheGate() {
        ThreatModel model = CheckoutThreatModel.build()
                .element("rider-app", ElementType.EXTERNAL_ENTITY, "internet", "Rider mobile app")
                .flow("f11-rider-gateway", "rider-app", "api-gateway", "Pick-up and delivery updates");
        assertThat(model.open()).hasSize(5);                                     // S, R for the app; T, I, D for the flow
    }

    @Test
    void mitigationsMustMatchThreatsThatApply() {
        assertThatThrownBy(() -> CheckoutThreatModel.build()
                .mitigate(Stride.ELEVATION_OF_PRIVILEGE, "n/a", "n/a", "orders-db"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThreatModel("x").element("a", ElementType.PROCESS, "same", "")
                .element("b", ElementType.PROCESS, "same", "").flow("f", "a", "b", ""))
                .hasMessageContaining("does not cross a trust boundary");
    }
}
