package dev.foodfleet.ch08.eta.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch08.eta.domain.EtaCalculator.Breakdown;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.Eta;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.RiderLeg;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EtaCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private final EtaCalculator calculator = new EtaCalculator();

    @Test
    void foodIsTheBottleneck_riderArrivesFirst() {
        Eta eta = calculator.calculate(12, new RiderLeg("rider-7", 8, 15), 20, NOW);

        assertThat(eta.minutes()).isEqualTo(27);
        assertThat(eta.breakdown()).isEqualTo(new Breakdown(12, 0, 15));
        assertThat(eta.window().display()).isEqualTo("24–30 min");
        assertThat(eta.estimatedArrival()).isEqualTo(Instant.parse("2026-10-01T08:27:00Z"));
        assertThat(eta.degraded()).isFalse();
    }

    @Test
    void riderIsTheBottleneck_foodWaits() {
        Eta eta = calculator.calculate(5, new RiderLeg("rider-7", 14, 10), 20, NOW);

        assertThat(eta.breakdown()).isEqualTo(new Breakdown(5, 9, 10));
        assertThat(eta.minutes()).isEqualTo(24);
        assertThat(eta.window().display()).as("10%% of 24 rounds to 2, so the 3-minute floor applies").isEqualTo("21–27 min");
    }

    @Test
    void withoutLiveRiderDataTheWindowWidensAndSaysSo() {
        Eta eta = calculator.calculate(12, null, 20, NOW);

        assertThat(eta.minutes()).isEqualTo(32);
        assertThat(eta.window().display()).isEqualTo("24–40 min");
        assertThat(eta.degraded()).isTrue();
    }

    @Test
    void rejectsImpossibleInputs() {
        assertThatThrownBy(() -> calculator.calculate(-1, null, 20, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RiderLeg("r", 3, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
