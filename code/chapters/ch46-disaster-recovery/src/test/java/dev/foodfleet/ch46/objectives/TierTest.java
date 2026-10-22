package dev.foodfleet.ch46.objectives;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TierTest {

    @Test
    void ordersMayLoseNothing() {
        assertThat(Tier.TIER_0.met(Duration.ZERO, Duration.ofMinutes(4))).isTrue();
        assertThat(Tier.TIER_0.met(Duration.ofMillis(1), Duration.ofMinutes(1))).isFalse();
        assertThat(Tier.TIER_0.replication()).isEqualTo(Tier.Replication.SYNC_IN_REGION_ASYNC_CROSS_REGION);
    }

    @Test
    void menusTolerateSecondsOfLossButNotAnHourOfDowntime() {
        assertThat(Tier.TIER_1.met(Duration.ofSeconds(10), Duration.ofMinutes(10))).isTrue();
        assertThat(Tier.TIER_1.met(Duration.ofSeconds(10), Duration.ofHours(1))).isFalse();
    }
}
