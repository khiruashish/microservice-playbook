package dev.foodfleet.ch34.freshness;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FreshnessTest {

    static final Instant NOW = Instant.parse("2025-04-14T10:00:00Z");
    static final Duration OBJECTIVE = Duration.ofMinutes(5);

    @Test
    void withinTheObjectiveIsFresh() {
        Freshness f = Freshness.of(NOW.minusSeconds(90), NOW, OBJECTIVE);
        assertThat(f.lag()).isEqualTo(Duration.ofSeconds(90));
        assertThat(f.stale()).isFalse();
    }

    @Test
    void beyondTheObjectiveIsStale() {
        assertThat(Freshness.of(NOW.minus(Duration.ofMinutes(6)), NOW, OBJECTIVE).stale()).isTrue();
    }

    @Test
    void aStoreThatHasNeverReceivedAnEventIsStale() {
        Freshness f = Freshness.of(null, NOW, OBJECTIVE);
        assertThat(f.stale()).isTrue();
        assertThat(f.asOf()).isNull();
    }

    @Test
    void eventsFromAProducerWhoseClockIsAheadDontProduceANegativeLag() {
        assertThat(Freshness.of(NOW.plusSeconds(3), NOW, OBJECTIVE).lag()).isEqualTo(Duration.ZERO);
    }
}
