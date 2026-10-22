package dev.foodfleet.ch33.pool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch33.pool.PoolSizing.Fleet;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoolSizingTest {

    @Test
    void theStartingPointIsTwicePerCorePlusDisks() {
        assertThat(PoolSizing.suggestedTotal(8, 1)).isEqualTo(17);
    }

    @Test
    void fleetsThatFitWithRoomForADeployPass() {
        var v = PoolSizing.check(List.of(new Fleet("order-service", 4, 10), new Fleet("menu-service", 2, 8)),
                200, 20, 1.25, 16, 2);
        assertThat(v.totalConnections()).isEqualTo(56);
        assertThat(v.ok()).isTrue();
    }

    @Test
    void aRollingDeployThatWouldExhaustMaxConnectionsIsCaught() {
        // 12 instances x 10 = 120 normally, but 150 while new instances start before old ones stop
        var v = PoolSizing.check(List.of(new Fleet("order-service", 12, 10)), 150, 10, 1.25, 32, 4);
        assertThat(v.ok()).isFalse();
        assertThat(v.problems().getFirst()).contains("can open 150 connections; the database allows 140");
    }

    @Test
    void defaultPoolsAcrossManyInstancesAreFlaggedAsOversized() {
        // the "everyone keeps the default" fleet: 30 instances x 10 against a 4-core database
        var v = PoolSizing.check(List.of(new Fleet("api", 30, 10)), 1000, 10, 1.0, 4, 1);
        assertThat(v.problems()).hasSize(1);
        assertThat(v.problems().getFirst()).contains("300 connections for 4 cores");
    }

    @Test
    void aPoolOfOneIsTooSmall() {
        var v = PoolSizing.check(List.of(new Fleet("cron", 1, 1)), 100, 10, 1.0, 4, 1);
        assertThat(v.problems().getFirst()).startsWith("cron: a pool of 1");
    }
}
