package dev.foodfleet.ch53;

import dev.foodfleet.ch53.container.ContainerLimits;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerLimitsTest {

    @Test
    void readsCgroupV2() throws Exception {
        Path root = Files.createTempDirectory("cg2");
        Files.writeString(root.resolve("memory.max"), "536870912\n");
        Files.writeString(root.resolve("cpu.max"), "150000 100000\n");
        ContainerLimits.Limits l = ContainerLimits.read(root);
        assertThat(l.memoryBytes().getAsLong()).isEqualTo(536_870_912L);
        assertThat(Math.abs(l.cpus().getAsDouble() - 1.5) < 1e-9).isTrue();
    }

    @Test
    void cgroupV2Unlimited() throws Exception {
        Path root = Files.createTempDirectory("cg2");
        Files.writeString(root.resolve("memory.max"), "max\n");
        Files.writeString(root.resolve("cpu.max"), "max 100000\n");
        ContainerLimits.Limits l = ContainerLimits.read(root);
        assertThat(l.memoryBytes().isPresent() || l.cpus().isPresent()).isFalse();
    }

    @Test
    void readsCgroupV1() throws Exception {
        Path root = Files.createTempDirectory("cg1");
        Files.createDirectories(root.resolve("memory"));
        Files.createDirectories(root.resolve("cpu"));
        Files.writeString(root.resolve("memory/memory.limit_in_bytes"), "268435456\n");
        Files.writeString(root.resolve("cpu/cpu.cfs_quota_us"), "50000\n");
        Files.writeString(root.resolve("cpu/cpu.cfs_period_us"), "100000\n");
        ContainerLimits.Limits l = ContainerLimits.read(root);
        assertThat(l.memoryBytes().getAsLong()).isEqualTo(268_435_456L);
        assertThat(Math.abs(l.cpus().getAsDouble() - 0.5) < 1e-9).isTrue();
    }

    @Test
    void cgroupV1Unlimited() throws Exception {
        Path root = Files.createTempDirectory("cg1");
        Files.createDirectories(root.resolve("memory"));
        Files.createDirectories(root.resolve("cpu"));
        Files.writeString(root.resolve("memory/memory.limit_in_bytes"), "9223372036854771712\n");
        Files.writeString(root.resolve("cpu/cpu.cfs_quota_us"), "-1\n");
        Files.writeString(root.resolve("cpu/cpu.cfs_period_us"), "100000\n");
        ContainerLimits.Limits l = ContainerLimits.read(root);
        assertThat(l.memoryBytes().isPresent() || l.cpus().isPresent()).isFalse();
    }

    @Test
    void describesTheHeapAsAShareOfTheLimit() {
        String s = ContainerLimits.describe(new ContainerLimits.Limits(OptionalLong.of(512L << 20), OptionalDouble.of(1)),
                new ContainerLimits.Jvm(1, 128L << 20, List.of("Copy", "MarkSweepCompact")));
        assertThat(s).isEqualTo("container: memory 512 MiB, cpus 1.00; jvm: 1 processors, max heap 128 MiB (25% of the limit), gc Copy + MarkSweepCompact");
    }
}
