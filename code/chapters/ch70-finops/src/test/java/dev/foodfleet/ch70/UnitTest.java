package dev.foodfleet.ch70;

import dev.foodfleet.ch70.cost.Fleet;
import dev.foodfleet.ch70.rightsize.Recommender;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    @Test
    void recommendsThe90thPercentilePlusFifteenPercent() {
        double[] cpu = new double[100];
        for (int i = 0; i < 100; i++) {
            cpu[i] = (i + 1) / 100.0; // 0.01 .. 1.00 cores
        }
        double[] mem = {300, 320, 310, 400};
        Recommender.Recommendation r = Recommender.recommend(cpu, mem, mem.length);
        assertEquals(0.90 * 1.15, r.cpuTarget(), 1e-9);
        assertEquals(0.50 * 1.15, r.cpuLower(), 1e-9);
        assertEquals(400 * 1.15, r.memTargetMiB(), 1e-9, "one interval: its peak");
    }

    @Test
    void neverRecommendsLessThanTheMinimums() {
        Recommender.Recommendation r = Recommender.recommend(new double[] {0.001, 0.002}, new double[] {50}, 1);
        assertEquals(0.025, r.cpuTarget(), 1e-9);
        assertEquals(250, r.memTargetMiB(), 1e-9);
    }

    @Test
    void packsPodsOntoTheFewestNodesTheirRequestsAllow() {
        List<Fleet.Service> fleet = List.of(new Fleet.Service("a", "t", "api", 4, 1, 2), new Fleet.Service("b", "t", "cache", 2, 1, 6));
        Fleet.Packing p = Fleet.pack(fleet, new Fleet.Node(3.8, 14.5));
        assertEquals(6, p.cpuRequested(), 1e-9);
        assertEquals(2, p.nodes(), "6 CPUs need 2 nodes of 3.8");
    }

    @Test
    void showbackChargesTheLargerOfRequestAndUsageAndLeavesTheRestIdle() {
        List<Fleet.Service> fleet = List.of(new Fleet.Service("a", "ordering", "api", 1, 1, 2));
        Map<String, double[]> show = Fleet.showback(fleet, Map.of("api", new double[] {0.25, 0.5}), 100);
        double[] t = show.get("ordering");
        assertTrue(t[1] < t[0], "usage is below request");
        assertEquals(t[0], t[2], 1e-9, "max(request, usage) = request here");
        assertEquals(100 - t[0], show.get("idle")[0], 1e-9);
    }
}
