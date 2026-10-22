package dev.foodfleet.ch33.sharding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotRouterTest {

    @Test
    void theSameKeyAlwaysLandsOnTheSameSlot() {
        assertThat(SlotRouter.slotOf("customer-42")).isEqualTo(SlotRouter.slotOf("customer-42"));
        assertThat(SlotRouter.slotOf("customer-42")).isBetween(0, SlotRouter.SLOTS - 1);
    }

    @Test
    void keysSpreadEvenlyOverShards() {
        SlotRouter router = SlotRouter.evenly(16);
        int[] perShard = new int[16];
        for (int i = 0; i < 160_000; i++) {
            perShard[router.shardOf("customer-" + i)]++;
        }
        for (int n : perShard) {
            assertThat(n).isBetween(9_000, 11_000);            // within 10% of the 10,000 average
        }
    }

    @Test
    void growingFrom16To20ShardsMovesOnlyTheSlotsTheNewShardsNeed() {
        SlotRouter before = SlotRouter.evenly(16);
        List<SlotRouter.Move> moves = before.planGrowth(4);
        SlotRouter after = before.apply(moves);

        assertThat(after.shards()).isEqualTo(20);
        assertThat(moves).hasSize(4 * (SlotRouter.SLOTS / 20));                          // 816 of 4,096 slots: 19.9%
        assertThat(new HashSet<>(moves.stream().map(SlotRouter.Move::slot).toList()).size()).isEqualTo(moves.size());
        for (int shard = 0; shard < 20; shard++) {
            assertThat(after.slotsOn(shard)).isBetween(204, 205);
        }
        int moved = 0;
        for (int i = 0; i < 100_000; i++) {
            String key = "customer-" + i;
            if (before.shardOf(key) != after.shardOf(key)) {
                moved++;
                assertThat(after.shardOf(key)).isGreaterThan(15);                           // keys only move to new shards
            }
        }
        assertThat(moved).isBetween(18_000, 22_000);
    }

    @Test
    void aMovePlannedAgainstAnOldMapIsRefused() {
        SlotRouter before = SlotRouter.evenly(16);
        List<SlotRouter.Move> moves = before.planGrowth(4);
        SlotRouter after = before.apply(moves);
        assertThatThrownBy(() -> after.apply(moves)).isInstanceOf(IllegalStateException.class);
    }
}
