package dev.foodfleet.ch54;

import dev.foodfleet.ch54.runtime.PodInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PodInfoTest {

    @Test
    void readsTheDownwardApiVariables() {
        PodInfo p = PodInfo.from(Map.of("POD_NAME", "menu-service-7d9f-x2k", "POD_NAMESPACE", "menu", "NODE_NAME", "ip-10-0-1-7",
                "CPU_LIMIT_MILLIS", "500", "MEMORY_LIMIT_BYTES", "805306368"));
        assertThat(p.name()).isEqualTo("menu-service-7d9f-x2k");
        assertThat(p.cpuLimitMillis()).isEqualTo(500L);
        assertThat(p.memoryLimitBytes()).isEqualTo(805_306_368L);
    }

    @Test
    void cpuBoundThreadsFollowTheLimitNotTheNode() {
        assertThat(new PodInfo("p", "n", "x", 500, 0).cpuBoundThreads(16)).isEqualTo(1);
        assertThat(new PodInfo("p", "n", "x", 1500, 0).cpuBoundThreads(16)).isEqualTo(2);
        assertThat(new PodInfo("p", "n", "x", 64000, 0).cpuBoundThreads(16)).isEqualTo(16);
        assertThat(new PodInfo("p", "n", "x", 0, 0).cpuBoundThreads(16)).isEqualTo(16);
    }

    @Test
    void outsideKubernetesItIsLocal() {
        PodInfo p = PodInfo.from(Map.of());
        assertThat(p.name()).isEqualTo("local");
        assertThat(p.cpuLimitMillis()).isEqualTo(0L);
    }
}
