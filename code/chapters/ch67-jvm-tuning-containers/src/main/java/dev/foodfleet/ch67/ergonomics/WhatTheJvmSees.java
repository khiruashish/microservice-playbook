package dev.foodfleet.ch67.ergonomics;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * What the JVM decided about the machine it runs on, as one line: the CPUs and memory it sees (inside a container,
 * the container's limits), the collector it chose, its maximum heap ({@code MaxHeapSize}), and the thread counts derived from the CPUs.
 */
public final class WhatTheJvmSees {

    private WhatTheJvmSees() {
    }

    public static String describe() {
        OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        HotSpotDiagnosticMXBean hs = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        String gc = ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).collect(Collectors.joining("+"));
        return String.format("cpus=%d memoryMB=%d gc=%s maxHeapMB=%d parallelGcThreads=%s concGcThreads=%s forkJoin=%d",
                Runtime.getRuntime().availableProcessors(), os.getTotalMemorySize() >> 20, collector(gc), Long.parseLong(hs.getVMOption("MaxHeapSize").getValue()) >> 20,
                hs.getVMOption("ParallelGCThreads").getValue(), hs.getVMOption("ConcGCThreads").getValue(), ForkJoinPool.commonPool().getParallelism());
    }

    /** The collector's usual name, from its MXBeans' names. */
    public static String collector(String beans) {
        if (beans.startsWith("Copy")) {
            return "Serial";
        } else if (beans.startsWith("PS ")) {
            return "Parallel";
        } else if (beans.startsWith("G1")) {
            return "G1";
        } else if (beans.startsWith("ZGC")) {
            return "ZGC";
        } else if (beans.startsWith("Shenandoah")) {
            return "Shenandoah";
        }
        return beans;
    }

    public static void main(String[] args) {
        System.out.println(describe());
    }
}
