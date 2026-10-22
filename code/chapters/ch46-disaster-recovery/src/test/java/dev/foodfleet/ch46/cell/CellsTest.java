package dev.foodfleet.ch46.cell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CellsTest {

    private static final List<String> FOUR = List.of("cell-1", "cell-2", "cell-3", "cell-4");

    private static Map<String, String> placement(CellRouter router, int tenants) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < tenants; i++) {
            m.put("tenant-" + i, router.cellOf("tenant-" + i));
        }
        return m;
    }

    @Test
    void tenantsSpreadEvenlyAndStayPut() {
        Map<String, String> a = placement(new CellRouter(FOUR), 10_000);
        Map<String, String> b = placement(new CellRouter(FOUR), 10_000);
        assertThat(a).isEqualTo(b);
        for (String cell : FOUR) {
            long n = a.values().stream().filter(cell::equals).count();
            assertThat(n).isBetween(2_300, 2_700);
        }
    }

    @Test
    void addingAFifthCellMovesOnlyTheTenantsItWins() {
        Map<String, String> before = placement(new CellRouter(FOUR), 10_000);
        List<String> five = List.of("cell-1", "cell-2", "cell-3", "cell-4", "cell-5");
        Map<String, String> after = placement(new CellRouter(five), 10_000);
        long moved = before.keySet().stream().filter(t -> !before.get(t).equals(after.get(t))).count();
        long toNew = after.values().stream().filter("cell-5"::equals).count();
        assertThat(moved).isEqualTo(toNew);              // nobody moves between old cells
        assertThat(moved).isBetween(1_800, 2_200);       // about a fifth
    }

    @Test
    void drainingACellMovesOnlyItsTenants() {
        CellRouter router = new CellRouter(FOUR);
        Map<String, String> before = placement(router, 5_000);
        router.drain("cell-2");
        Map<String, String> after = placement(router, 5_000);
        for (String t : before.keySet()) {
            if (!before.get(t).equals("cell-2")) {
                assertThat(after.get(t)).isEqualTo(before.get(t));
            } else {
                assertThat(after.get(t)).isNotEqualTo("cell-2");
            }
        }
    }

    @Test
    void aPinnedTenantStaysUnlessItsCellIsDrained() {
        CellRouter router = new CellRouter(FOUR);
        router.pin("big-chain", "cell-3");
        assertThat(router.cellOf("big-chain")).isEqualTo("cell-3");
        router.drain("cell-3");
        assertThat(router.cellOf("big-chain")).isNotEqualTo("cell-3");
    }

    @Test
    void shuffleShardsAreSmallStableAndRarelyIdentical() {
        List<String> eight = List.of("i1", "i2", "i3", "i4", "i5", "i6", "i7", "i8");
        ShuffleSharder sharder = new ShuffleSharder(eight, 2);
        List<String> poison = sharder.shardOf("poison");
        assertThat(poison).hasSize(2);
        assertThat(sharder.shardOf("poison")).isEqualTo(poison);
        int same = 0;
        for (int i = 0; i < 28_000; i++) {
            List<String> s = sharder.shardOf("tenant-" + i);
            if (s.containsAll(poison)) {
                same++;
            }
        }
        assertThat(same).isBetween(800, 1_200);          // 1 in 28 = 1,000
    }
}
