package dev.foodfleet.ch07.topology;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch07.topology.Topology.Component;
import dev.foodfleet.ch07.topology.Topology.Domain;
import dev.foodfleet.ch07.topology.Topology.Team;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopologyTest {

    @Test
    void everyComponentNeedsAnOwnerThatExists() {
        assertThatThrownBy(() -> new Topology("broken", LocalDate.of(2026, 1, 1),
                List.of(new Team("Checkout", Topology.TeamType.STREAM_ALIGNED, 6)),
                List.of(new Domain("Ordering", Topology.Complexity.COMPLICATED)),
                List.of(new Component("ordering", "services/ordering/", "Nobody", "Ordering", null)),
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ordering: unknown owner team Nobody");
    }
}
