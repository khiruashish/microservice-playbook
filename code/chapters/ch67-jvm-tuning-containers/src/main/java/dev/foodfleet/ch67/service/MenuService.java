package dev.foodfleet.ch67.service;

import com.sun.management.OperatingSystemMXBean;
import dev.foodfleet.ch67.ergonomics.WhatTheJvmSees;
import dev.foodfleet.ch67.memory.NativeMemory;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * menu-service under a steady arrival rate, measured from inside: requests arrive on schedule (an open model, as in
 * chapter 65), each runs on a virtual thread, and its latency counts from when it was due, so a GC pause that delays
 * the scheduler still shows. Prints what the JVM chose, the live set, request latency, GC pauses (from JFR), CPU used
 * and peak resident memory.
 * <p>
 * {@code MenuService <requests/s> <warm-up s> <measured s> <live MB> [refresh 1 in N]}; {@code -Dnmt=true} also
 * prints Native Memory Tracking's summary, and {@code -DdirectMb=N} holds N MB of direct buffers outside the heap.
 */
public final class MenuService {

    public record Result(int requests, long served, long[] micros, long collections, String kinds, double pauseMs, double longestPauseMs, double cpuCores) {

        public double p(double pct) {
            if (micros.length == 0) {
                return Double.NaN;
            }
            int i = (int) Math.ceil(pct / 100 * micros.length) - 1;
            return micros[Math.max(0, Math.min(micros.length - 1, i))] / 1000.0;
        }
    }

    public static Result run(MenuCache cache, int rate, int warmupSeconds, int seconds, int refreshEvery) throws InterruptedException {
        OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        int warm = rate * warmupSeconds;
        int total = warm + rate * seconds;
        long[] micros = new long[rate * seconds];
        Arrays.fill(micros, -1);
        AtomicLong served = new AtomicLong();
        CountDownLatch done = new CountDownLatch(total);
        long cpu0 = 0;
        long wall0 = 0;
        try (GcEvents gc = new GcEvents(); ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor()) {
            long t0 = System.nanoTime();
            for (int i = 0; i < total; i++) {
                long due = t0 + (long) (i * 1e9 / rate);
                long wait = due - System.nanoTime();
                if (wait > 0) {
                    LockSupport.parkNanos(wait);
                }
                if (i == warm) {
                    gc.startCounting();
                    cpu0 = os.getProcessCpuTime();
                    wall0 = System.nanoTime();
                }
                int idx = i - warm;
                long seed = i;
                requests.execute(() -> {
                    cache.handle(new SplittableRandom(seed), refreshEvery);
                    if (idx >= 0) {
                        micros[idx] = (System.nanoTime() - due) / 1000;
                        served.incrementAndGet();
                    }
                    done.countDown();
                });
            }
            done.await(60, TimeUnit.SECONDS);
            double cores = (os.getProcessCpuTime() - cpu0) / (double) (System.nanoTime() - wall0);
            long[] sorted = Arrays.stream(micros).filter(v -> v >= 0).sorted().toArray();
            return new Result(micros.length, served.get(), sorted, gc.collections(), gc.byName().toString().replace(" ", "_"), gc.pauses().toNanos() / 1e6, gc.longestPause().toNanos() / 1e6, cores);
        }
    }

    /** Peak resident memory of this process (VmHWM), in MB. */
    static long peakRssMb() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmHWM:")) {
                    return Long.parseLong(line.replaceAll("\\D", "")) >> 10;
                }
            }
        } catch (Exception e) {
            // not Linux
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        int rate = Integer.parseInt(args[0]);
        int warmup = Integer.parseInt(args[1]);
        int seconds = Integer.parseInt(args[2]);
        int liveMb = Integer.parseInt(args[3]);
        int refreshEvery = args.length > 4 ? Integer.parseInt(args[4]) : 10;
        System.out.println("jvm " + WhatTheJvmSees.describe());
        int directMb = Integer.getInteger("directMb", 0);
        List<ByteBuffer> offHeap = new ArrayList<>();
        for (int i = 0; i < directMb; i++) { // buffers held outside the heap, as Netty's pooled allocator or a client library would
            ByteBuffer b = ByteBuffer.allocateDirect(1 << 20);
            for (int j = 0; j < b.capacity(); j += 4096) {
                b.put(j, (byte) 1);
            }
            offHeap.add(b);
        }
        MenuCache cache = new MenuCache(liveMb);
        System.gc();
        long live = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >> 20;
        System.out.println("cache menus=" + cache.size() + " liveMB=" + live + " directMB=" + offHeap.size());
        Result r = run(cache, rate, warmup, seconds, refreshEvery);
        System.out.printf("result served=%d/%d p50=%.1f p99=%.1f p999=%.1f max=%.1f gcs=%d kinds=%s pauseMs=%.0f longestPauseMs=%.1f cpuCores=%.2f peakRssMB=%d heapCommittedMB=%d%n",
                r.served(), r.requests(), r.p(50), r.p(99), r.p(99.9), r.p(100), r.collections(), r.kinds(), r.pauseMs(), r.longestPauseMs(), r.cpuCores(), peakRssMb(),
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted() >> 20);
        if (Boolean.getBoolean("nmt")) {
            System.out.println("nmt " + NativeMemory.committed(NativeMemory.summary()));
        }
    }
}
