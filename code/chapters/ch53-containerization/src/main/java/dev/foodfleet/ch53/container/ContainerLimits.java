package dev.foodfleet.ch53.container;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * The limits a container really runs under, read from its cgroup (v2 or v1), next to what the JVM made of them:
 * available processors, maximum heap and the garbage collector it chose. Logged once at startup, it answers "why is
 * the heap 128 MB?" and "why SerialGC?" before anyone has to ask.
 */
public final class ContainerLimits {

    /** What the cgroup allows: memory in bytes and CPUs (quota / period); empty when unlimited. */
    public record Limits(OptionalLong memoryBytes, OptionalDouble cpus) {
    }

    /** What the JVM decided. */
    public record Jvm(int availableProcessors, long maxHeapBytes, List<String> collectors) {
    }

    private ContainerLimits() {
    }

    /** Reads the cgroup files under {@code root} (normally /sys/fs/cgroup). */
    public static Limits read(Path root) throws IOException {
        Path v2Memory = root.resolve("memory.max");
        if (Files.exists(v2Memory)) {
            return new Limits(parseV2Memory(Files.readString(v2Memory)),
                    Files.exists(root.resolve("cpu.max")) ? parseV2Cpu(Files.readString(root.resolve("cpu.max"))) : OptionalDouble.empty());
        }
        OptionalLong memory = OptionalLong.empty();
        Path v1Memory = root.resolve("memory/memory.limit_in_bytes");
        if (Files.exists(v1Memory)) {
            memory = parseV1Memory(Files.readString(v1Memory));
        }
        OptionalDouble cpus = OptionalDouble.empty();
        Path quota = root.resolve("cpu/cpu.cfs_quota_us");
        Path period = root.resolve("cpu/cpu.cfs_period_us");
        if (Files.exists(quota) && Files.exists(period)) {
            cpus = parseV1Cpu(Files.readString(quota), Files.readString(period));
        }
        return new Limits(memory, cpus);
    }

    /** cgroup v2 memory.max: a byte count, or "max". */
    static OptionalLong parseV2Memory(String s) {
        s = s.trim();
        return s.equals("max") ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(s));
    }

    /** cgroup v2 cpu.max: "quota period", quota "max" when unlimited. */
    static OptionalDouble parseV2Cpu(String s) {
        String[] p = s.trim().split("\\s+");
        if (p[0].equals("max")) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Double.parseDouble(p[0]) / Double.parseDouble(p[1]));
    }

    /** cgroup v1 memory.limit_in_bytes: unlimited is a huge number close to Long.MAX_VALUE. */
    static OptionalLong parseV1Memory(String s) {
        long v = Long.parseLong(s.trim());
        return v >= (1L << 60) ? OptionalLong.empty() : OptionalLong.of(v);
    }

    /** cgroup v1 cpu.cfs_quota_us and cpu.cfs_period_us: quota -1 when unlimited. */
    static OptionalDouble parseV1Cpu(String quota, String period) {
        long q = Long.parseLong(quota.trim());
        return q <= 0 ? OptionalDouble.empty() : OptionalDouble.of((double) q / Long.parseLong(period.trim()));
    }

    public static Jvm jvm() {
        List<String> gcs = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcs.add(gc.getName());
        }
        return new Jvm(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(), gcs);
    }

    /** One line for the startup log. */
    public static String describe(Limits limits, Jvm jvm) {
        String mem = limits.memoryBytes().isPresent() ? limits.memoryBytes().getAsLong() / (1024 * 1024) + " MiB" : "unlimited";
        String cpu = limits.cpus().isPresent() ? String.format("%.2f", limits.cpus().getAsDouble()) : "unlimited";
        Optional<String> heapShare = limits.memoryBytes().isPresent()
                ? Optional.of(String.format(" (%.0f%% of the limit)", 100.0 * jvm.maxHeapBytes() / limits.memoryBytes().getAsLong()))
                : Optional.empty();
        return "container: memory " + mem + ", cpus " + cpu + "; jvm: " + jvm.availableProcessors() + " processors, max heap "
                + jvm.maxHeapBytes() / (1024 * 1024) + " MiB" + heapShare.orElse("") + ", gc " + String.join(" + ", jvm.collectors());
    }
}
