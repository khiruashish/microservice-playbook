package dev.foodfleet.ch19.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch19.catalog.MenuItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplicaTest {

    private final List<MenuItem> applied = new ArrayList<>();
    private final Replica replica = new Replica(applied::add);
    private final MenuItem v1 = new MenuItem("item-1", "rest-1", "Biryani", 29_900, true, 1);

    @Test
    void newerVersionsReplaceOlderOnes() {
        replica.apply(v1);
        replica.apply(v1.withAvailable(false));

        assertThat(replica.get("item-1").orElseThrow().available()).isFalse();
    }

    @Test
    void duplicatesAndLateOlderVersionsAreIgnored() {
        MenuItem v2 = v1.withPrice(31_900);
        replica.apply(v2);
        replica.apply(v2);                // duplicate
        replica.apply(v1);                // late redelivery

        assertThat(replica.get("item-1").orElseThrow()).isEqualTo(v2);
        assertThat(replica.ignoredStale()).isEqualTo(2L);
        assertThat(applied).containsExactly(v2);
    }
}
