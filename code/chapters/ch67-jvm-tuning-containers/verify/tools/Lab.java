import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 67's experiments. Every JVM runs in its own Docker container with CPU and memory limits; the JDK and the
 * compiled classes are mounted read-only into a small image that has nothing but a C library.
 * <ul>
 *   <li>{@code ergonomics}: what the JVM chooses (collector, heap, threads) for a range of container sizes</li>
 *   <li>{@code gc}: menu-service under steady load with each collector, and with 1 CPU instead of 2</li>
 *   <li>{@code heap}: the heap as a share of a 1 GiB container, from 25% to 90%: who kills the process, Java or the kernel</li>
 *   <li>{@code nmt}: where the memory beyond the heap goes (Native Memory Tracking)</li>
 *   <li>{@code jfr}: what a continuous flight recording costs</li>
 * </ul>
 * System properties: c67.jdk (the JDK directory to mount), c67.classes, c67.image (default ff-jre:25).
 */
public final class Lab {

    static final String JDK = System.getProperty("c67.jdk");
    static final String CLASSES = System.getProperty("c67.classes");
    static final String IMAGE = System.getProperty("c67.image", "ff-jre:25");
    static final String LOAD = "6000 10 30 300 2"; // requests/s, warm-up s, measured s, cache MB, refresh 1 in 2

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "ergonomics" -> ergonomics();
                case "gc" -> gc();
                case "heap" -> heap();
                case "nmt" -> nmt();
                case "jfr" -> jfr();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------ ergonomics

    static void ergonomics() throws Exception {
        System.out.println("no options except those shown; the host has 2 CPUs");
        System.out.printf("%-6s %-8s %-30s %5s %-11s %11s %9s %9s %9s%n", "cpus", "memory", "options", "CPUs", "GC", "max heap", "GC thr", "conc thr", "FJ pool");
        String[][] rows = {
                {"", "", ""}, {"0.5", "256m", ""}, {"0.5", "512m", ""}, {"1", "512m", ""}, {"1", "1g", ""}, {"1", "2g", ""}, {"1.5", "2g", ""},
                {"2", "1g", ""}, {"2", "1792m", ""}, {"2", "2g", ""}, {"2", "4g", ""},
                {"1", "2g", "-XX:ActiveProcessorCount=2"}, {"2", "1g", "-XX:+UseG1GC"}, {"2", "1g", "-XX:MaxRAMPercentage=75"},
        };
        for (String[] row : rows) {
            List<String> jvm = row[2].isEmpty() ? List.of() : List.of(row[2]);
            Run r = docker(row[0], row[1], jvm, "dev.foodfleet.ch67.ergonomics.WhatTheJvmSees");
            Map<String, String> kv = kv(r.out);
            System.out.printf("%-6s %-8s %-30s %5s %-11s %8s MB %9s %9s %9s%n", row[0].isEmpty() ? "-" : row[0], row[1].isEmpty() ? "-" : row[1], row[2].isEmpty() ? "-" : row[2],
                    kv.get("cpus"), kv.get("gc"), kv.get("maxHeapMB"), kv.get("parallelGcThreads"), kv.get("concGcThreads"), kv.get("forkJoin"));
        }
    }

    // ------------------------------------------------------------------ collectors

    static void gc() throws Exception {
        System.out.println("menu-service: 6,000 requests/s for 30 s after 10 s of warm-up; a ~350 MB cache of small objects, half the requests refresh a menu;");
        System.out.println("container: 2 GiB; heap -Xmx1g; latency from when each request was due; pauses from JFR's jdk.GarbageCollection");
        header();
        String[][] rows = {
                {"2", "-XX:+UseSerialGC"}, {"2", "-XX:+UseParallelGC"}, {"2", "-XX:+UseG1GC"}, {"2", "-XX:+UseG1GC -XX:+UseCompactObjectHeaders"},
                {"2", "-XX:+UseZGC"}, {"2", "-XX:+UseShenandoahGC"}, {"1", "-XX:+UseG1GC"}, {"1", "-XX:+UseZGC"},
        };
        for (String[] row : rows) {
            List<String> jvm = new ArrayList<>(List.of("-Xmx1g"));
            jvm.addAll(List.of(row[1].split(" ")));
            service(row[0], "2g", jvm, row[1].replace("-XX:+Use", "").replace("ZGC", "Z").replace("GC", "").replace("Z", "ZGC").replace(" CompactObjectHeaders", " + compact headers"));
        }
    }

    // ------------------------------------------------------------------ heap share of the container

    static void heap() throws Exception {
        System.out.println("the same load in a 1 GiB container (2 CPUs, G1, -XX:+ExitOnOutOfMemoryError), the heap set by MaxRAMPercentage as a share of the container's memory");
        header();
        String[] rows = {"-XX:MaxRAMPercentage=25", "-XX:MaxRAMPercentage=50", "-XX:MaxRAMPercentage=75", "-XX:MaxRAMPercentage=90",
                "-XX:MaxRAMPercentage=75 -DdirectMb=150", "-XX:MaxRAMPercentage=90 -DdirectMb=150"};
        for (String row : rows) {
            List<String> jvm = new ArrayList<>(List.of("-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError"));
            jvm.addAll(List.of(row.split(" ")));
            service("2", "1g", jvm, row.replace("-XX:MaxRAMPercentage=", "heap ").replace(" -DdirectMb=150", "% + 150 MB of direct buffers").replace("heap 25", "heap 25%").replace("heap 50", "heap 50%").replace("heap 75", "heap 75%").replace("heap 90", "heap 90%").replace("%%", "%"));
        }
    }

    // ------------------------------------------------------------------ native memory

    static void nmt() throws Exception {
        System.out.println("committed memory by category (Native Memory Tracking, at the end of the run), against the process's peak RSS; -Xmx1g, 2 CPUs, 2 GiB");
        for (String gc : List.of("-XX:+UseG1GC", "-XX:+UseZGC")) {
            Run r = docker("2", "2g", List.of("-Xmx1g", gc, "-XX:NativeMemoryTracking=summary", "-Dnmt=true"), "dev.foodfleet.ch67.service.MenuService", LOAD.split(" "));
            Map<String, String> res = kv(line(r.out, "result "));
            String nmt = line(r.out, "nmt ");
            System.out.printf("%-6s peak RSS %s MB; %s%n", gc.replace("-XX:+Use", ""), res.get("peakRssMB"), nmt.replace("nmt ", ""));
        }
    }

    // ------------------------------------------------------------------ flight recorder

    static void jfr() throws Exception {
        System.out.println("G1, 2 CPUs, 2 GiB, -Xmx1g; a continuous recording to a file in the container, as in production");
        header();
        String[][] rows = {{"", "GC events only (the lab's own stream)"},
                {"-XX:StartFlightRecording=settings=default,maxsize=100m,filename=/tmp/rec.jfr", "+ continuous recording, default settings"},
                {"-XX:StartFlightRecording=settings=profile,maxsize=100m,filename=/tmp/rec.jfr", "+ continuous recording, profile settings"}};
        for (String[] row : rows) {
            List<String> jvm = new ArrayList<>(List.of("-Xmx1g", "-XX:+UseG1GC"));
            if (!row[0].isEmpty()) {
                jvm.add(row[0]);
            }
            service("2", "2g", jvm, row[1]);
        }
    }

    // ------------------------------------------------------------------ helpers

    static void header() {
        System.out.printf("%-4s %-48s %8s %7s %8s %8s %8s %6s %10s %10s %6s %9s %s%n", "cpus", "configuration", "live MB", "served", "p50 ms", "p99 ms", "p99.9 ms", "GCs",
                "pauses ms", "longest ms", "cores", "peak RSS", "outcome / collections");
    }

    static void service(String cpus, String memory, List<String> jvm, String label) throws Exception {
        Run r = docker(cpus, memory, jvm, "dev.foodfleet.ch67.service.MenuService", LOAD.split(" "));
        Map<String, String> res = kv(line(r.out, "result "));
        Map<String, String> cache = kv(line(r.out, "cache "));
        String outcome = r.oomKilled ? "killed by the kernel (OOMKilled, exit " + r.exit + ")"
                : r.out.contains("OutOfMemoryError") ? "java.lang.OutOfMemoryError (exit " + r.exit + ")"
                : r.exit != 0 ? "exit " + r.exit : "ok " + res.getOrDefault("kinds", "");
        if (res.isEmpty()) {
            System.out.printf("%-4s %-48s %8s %7s %8s %8s %8s %6s %10s %10s %6s %9s %s%n", cpus, label, cache.getOrDefault("liveMB", "-"), "-", "-", "-", "-", "-", "-", "-", "-", "-", outcome);
            return;
        }
        String served = res.get("served");
        long ok = Long.parseLong(served.split("/")[0]);
        long all = Long.parseLong(served.split("/")[1]);
        System.out.printf("%-4s %-48s %8s %6.1f%% %8s %8s %8s %6s %10s %10s %6s %6s MB %s%n", cpus, label, cache.get("liveMB"), 100.0 * ok / all, res.get("p50"), res.get("p99"), res.get("p999"),
                res.get("gcs"), res.get("pauseMs"), res.get("longestPauseMs"), res.get("cpuCores"), res.get("peakRssMB"), outcome);
    }

    record Run(String out, int exit, boolean oomKilled) {
    }

    /** Runs a class in a fresh container with the given limits; empty cpus/memory: no limit. */
    static Run docker(String cpus, String memory, List<String> jvm, String main, String... args) throws Exception {
        String name = "c67-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--name", name, "--entrypoint", "/jdk/bin/java", "-v", JDK + ":/jdk:ro", "-v", CLASSES + ":/app:ro"));
        if (!cpus.isEmpty()) {
            cmd.addAll(List.of("--cpus", cpus));
        }
        if (!memory.isEmpty()) {
            cmd.addAll(List.of("--memory", memory, "--memory-swap", memory));
        }
        cmd.add(IMAGE);
        cmd.addAll(jvm);
        cmd.addAll(List.of("-cp", "/app", main));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        Process inspect = new ProcessBuilder("docker", "inspect", "-f", "{{.State.ExitCode}} {{.State.OOMKilled}}", name).redirectErrorStream(true).start();
        String[] state = new String(inspect.getInputStream().readAllBytes()).trim().split(" ");
        inspect.waitFor();
        new ProcessBuilder("docker", "rm", "-f", name).redirectErrorStream(true).start().waitFor();
        return new Run(out, Integer.parseInt(state[0]), Boolean.parseBoolean(state[1]));
    }

    static String line(String out, String prefix) {
        return out.lines().filter(l -> l.startsWith(prefix)).findFirst().orElse("");
    }

    static final Pattern KV = Pattern.compile("(\\w+)=(\\S+)");

    static Map<String, String> kv(String line) {
        Map<String, String> m = new LinkedHashMap<>();
        Matcher x = KV.matcher(line);
        while (x.find()) {
            m.put(x.group(1), x.group(2));
        }
        return m;
    }
}
