package dev.foodfleet.ch67;

import dev.foodfleet.ch67.ergonomics.WhatTheJvmSees;
import dev.foodfleet.ch67.memory.NativeMemory;
import dev.foodfleet.ch67.service.MenuCache;
import dev.foodfleet.ch67.service.MenuService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    @Test
    void namesTheCollectorFromItsBeans() {
        assertEquals("Serial", WhatTheJvmSees.collector("Copy+MarkSweepCompact"));
        assertEquals("Parallel", WhatTheJvmSees.collector("PS Scavenge+PS MarkSweep"));
        assertEquals("G1", WhatTheJvmSees.collector("G1 Young Generation+G1 Concurrent GC+G1 Old Generation"));
        assertEquals("ZGC", WhatTheJvmSees.collector("ZGC Minor Cycles+ZGC Minor Pauses+ZGC Major Cycles+ZGC Major Pauses"));
        assertTrue(WhatTheJvmSees.describe().matches("cpus=\\d+ memoryMB=\\d+ gc=\\S+ maxHeapMB=\\d+ .*"));
    }

    @Test
    void readsNativeMemoryTrackingsSummary() {
        String summary = """
                Native Memory Tracking:

                Total: reserved=2456MB, committed=694MB
                       malloc: 60MB #64000
                -                 Java Heap (reserved=1024MB, committed=594MB)
                                            (mmap: reserved=1024MB, committed=594MB)
                -                        GC (reserved=60MB, committed=48MB)
                -                    Thread (reserved=20MB, committed=1MB)
                """;
        Map<String, Long> m = NativeMemory.committed(summary);
        assertEquals(594L, m.get("Java Heap"));
        assertEquals(48L, m.get("GC"));
        assertEquals(694L, m.get("Total"));
    }

    @Test
    void aRequestRendersMenusAndSometimesRefreshesOne() {
        MenuCache cache = new MenuCache(1);
        assertTrue(cache.size() > 500);
        assertTrue(cache.handle(new SplittableRandom(1), 1) > 5 * 20 * 30, "five menus of twenty items");
    }

    @Test
    void theLoadIsServedAndTimedFromWhenItWasDue() throws Exception {
        MenuService.Result r = MenuService.run(new MenuCache(1), 200, 0, 1, 2);
        assertEquals(200, r.served());
        assertTrue(r.p(50) >= 0 && r.p(100) >= r.p(99));
    }
}
