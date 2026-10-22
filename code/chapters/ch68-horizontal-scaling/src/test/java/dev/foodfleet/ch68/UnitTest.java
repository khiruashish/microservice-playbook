package dev.foodfleet.ch68;

import dev.foodfleet.ch68.app.Carts;
import dev.foodfleet.ch68.partition.Analysis;
import dev.foodfleet.ch68.partition.Placement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    static final List<String> KEYS = IntStream.range(0, 20_000).mapToObj(i -> "order-" + i).toList();

    @Test
    void everyPlacementIsDeterministicAndInRange() {
        for (Placement p : List.of(new Placement.Modulo(), new Placement.Ring(16), new Placement.Rendezvous(), new Placement.Jump())) {
            for (String k : KEYS.subList(0, 1000)) {
                int owner = p.owner(k, 7);
                assertTrue(owner >= 0 && owner < 7, p.name());
                assertEquals(owner, p.owner(k, 7), p.name());
            }
        }
    }

    @Test
    void consistentPlacementsMoveOnlyTheNewNodesShare() {
        assertTrue(Analysis.moved(new Placement.Modulo(), KEYS, 4, 5) > 0.7, "mod n moves most keys");
        for (Placement p : List.of(new Placement.Ring(256), new Placement.Rendezvous(), new Placement.Jump())) {
            double moved = Analysis.moved(p, KEYS, 4, 5);
            assertTrue(moved > 0.15 && moved < 0.25, p.name() + " moved " + moved);
        }
    }

    @Test
    void moreVirtualNodesSpreadKeysMoreEvenly() {
        double one = Analysis.maxOverMean(Analysis.spread(new Placement.Ring(1), KEYS, 10));
        double many = Analysis.maxOverMean(Analysis.spread(new Placement.Ring(256), KEYS, 10));
        assertTrue(many < one, many + " vs " + one);
        assertTrue(many < 1.2, "256 points per node: within 20% of even");
    }

    @Test
    void zipfMakesTheTopRankThePopularOne() {
        Analysis.Zipf z = new Analysis.Zipf(2000, 1.0);
        SplittableRandom r = new SplittableRandom(1);
        int top = 0;
        for (int i = 0; i < 100_000; i++) {
            if (z.next(r) == 0) {
                top++;
            }
        }
        assertEquals(z.top(), top / 100_000.0, 0.01);
    }

    @Test
    void cartsInMemoryCountPerUser() throws Exception {
        Carts carts = new Carts.InMemory();
        carts.add("a");
        carts.add("a");
        carts.add("b");
        assertEquals(2, carts.count("a"));
        assertEquals(0, carts.count("nobody"));
    }
}
