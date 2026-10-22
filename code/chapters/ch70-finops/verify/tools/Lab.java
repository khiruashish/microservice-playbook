import dev.foodfleet.ch70.cost.Fleet;
import dev.foodfleet.ch70.rightsize.Recommender;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Chapter 70's experiments.
 * <ul>
 *   <li>{@code usage}: three workloads in Docker containers sized as FoodFleet declares them, through a compressed day
 *       of traffic; what they use, what VPA's rules recommend, and the api again at the recommended size</li>
 *   <li>{@code fleet}: the 24 services' requests packed onto nodes before and after right-sizing, and the bill divided
 *       among teams three ways (calculated, from the usage experiment's factors)</li>
 *   <li>{@code spot}: on a cluster, part of the copies on "spot" and reclaimed for a minute under load</li>
 * </ul>
 * System properties: c70.jdk, c70.classes, c70.image (default ff-jre:25), c70.fleet, c70.kubectl, c70.manifest.
 */
public final class Lab {

    static final String JDK = System.getProperty("c70.jdk");
    static final String CLASSES = System.getProperty("c70.classes");
    static final String IMAGE = System.getProperty("c70.image", "ff-jre:25");
    static final List<String> JVM = List.of("-XX:+UseG1GC", "-XX:MaxRAMPercentage=70");
    /** The compressed day: 24 slots of 5 s ("hours"), requests per second in each; lunch and dinner peaks. */
    static final int SLOT = 5;
    static final int[] DAY = {20, 20, 20, 20, 20, 30, 60, 100, 120, 160, 260, 380, 400, 300, 160, 120, 140, 260, 420, 480, 380, 200, 80, 40};
    static final Map<String, double[]> FACTORS = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "usage" -> usage();
                case "fleet" -> fleet();
                case "spot" -> spot();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------ usage and right-sizing

    record Sampled(double[] cpu, double[] mem, long[] micros, long failed, long[] peakMicros) {
    }

    static void usage() throws Exception {
        System.out.println("each workload in a container sized as declared (the limit = its request), JVM: " + String.join(" ", JVM) + ";");
        System.out.println("a compressed day of 24 'hours' of 5 s, CPU and resident memory sampled every second; VPA's rules: CPU p90 + 15%,");
        System.out.println("memory the day's peak + 15% (one day of history: one daily peak), at least 25m and 250 MiB");
        System.out.printf("%-8s %-14s %8s %8s %8s %8s %10s %10s %10s %12s %12s %10s%n", "workload", "declared", "CPU mean", "CPU p90", "CPU p99", "CPU max",
                "mem mean", "mem peak", "VPA CPU", "VPA memory", "CPU used", "mem used");
        String[][] rows = {{"api", "1", "2g", "", "1"}, {"cache", "1", "4g", "CACHE_MB=600", "0.15"}, {"batch", "2", "4g", "PERIOD_S=30,BUSY_S=6", "0"}};
        for (String[] row : rows) {
            Sampled s = run(row[0], row[1], row[2], row[3], Double.parseDouble(row[4]));
            Recommender.Recommendation rec = Recommender.recommend(s.cpu(), s.mem(), s.mem().length);
            double declaredCpu = Double.parseDouble(row[1]);
            double declaredMem = Double.parseDouble(row[2].replace("g", "")) * 1024;
            FACTORS.put(row[0], new double[] {Recommender.mean(s.cpu()) / declaredCpu, Recommender.mean(s.mem()) / declaredMem,
                    rec.cpuTarget() / declaredCpu, rec.memTargetMiB() / declaredMem});
            System.out.printf("%-8s %-14s %8.2f %8.2f %8.2f %8.2f %7.0f MiB %6.0f MiB %10.2f %8.0f MiB %11.0f%% %9.0f%%%n", row[0], row[1] + " CPU, " + row[2].replace("g", " GiB"),
                    Recommender.mean(s.cpu()), Recommender.percentile(s.cpu(), 0.9), Recommender.percentile(s.cpu(), 0.99), Recommender.percentile(s.cpu(), 1),
                    Recommender.mean(s.mem()), Recommender.percentile(s.mem(), 1), rec.cpuTarget(), rec.memTargetMiB(),
                    100 * Recommender.mean(s.cpu()) / declaredCpu, 100 * Recommender.mean(s.mem()) / declaredMem);
            if (row[0].equals("api")) {
                api = rec;
            }
        }
        System.out.println();
        System.out.println("the api through the same day at each size; latency from when each request was due; peak = the 4 busiest 'hours'");
        System.out.printf("%-52s %8s %9s %9s %11s %8s %9s %10s%n", "size", "p50 ms", "p99 ms", "max ms", "peak p99 ms", "failed", "CPU mean", "mem peak");
        double cpu = Math.ceil(api.cpuTarget() * 10) / 10;
        long mem = (long) Math.ceil(api.memTargetMiB() / 64) * 64;
        String[][] sizes = {{"declared: 1 CPU, 2 GiB (limits)", "1", "2048m"},
                {String.format("VPA target as limits: %.1f CPU, %d MiB", cpu, mem), String.valueOf(cpu), mem + "m"},
                {String.format("VPA target memory, no CPU limit (%.1f CPU requested)", cpu), "", mem + "m"},
                {"half the VPA target memory as limit", "", (mem / 2) + "m"}};
        for (String[] size : sizes) {
            Sampled s = run("api", size[1], size[2], "", 1);
            long[] all = s.micros();
            long[] peak = s.peakMicros();
            System.out.printf("%-52s %8.1f %9.1f %9.0f %11.1f %8d %9.2f %6.0f MiB%n", size[0], p(all, 50), p(all, 99), p(all, 100), p(peak, 99), s.failed(),
                    Recommender.mean(s.cpu()), Recommender.percentile(s.mem(), 1));
        }
    }

    static Recommender.Recommendation api;

    /** Runs a workload in a container through the day, sampling it; {@code loadScale} 0 means no traffic. */
    static Sampled run(String mode, String cpus, String memory, String env, double loadScale) throws Exception {
        int port = freePort();
        String name = "c70-" + mode + "-" + System.nanoTime();
        List<String> cmd = new ArrayList<>(List.of("docker", "run", "-d", "--name", name, "--entrypoint", "/jdk/bin/java", "-v", JDK + ":/jdk:ro", "-v", CLASSES + ":/app:ro",
                "-p", "127.0.0.1:" + port + ":8080", "--memory", memory, "--memory-swap", memory));
        if (!cpus.isEmpty()) {
            cmd.addAll(List.of("--cpus", cpus));
        }
        for (String e : env.isEmpty() ? new String[0] : env.split(",")) {
            cmd.addAll(List.of("-e", e));
        }
        cmd.add(IMAGE);
        cmd.addAll(JVM);
        cmd.addAll(List.of("-cp", "/app", "dev.foodfleet.ch70.app.MenuApi", mode));
        sh(cmd.toArray(String[]::new));
        try {
            long pid = Long.parseLong(sh("docker", "inspect", "-f", "{{.State.Pid}}", name).strip());
            HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
            String url = "http://127.0.0.1:" + port;
            for (int i = 0; i < 300; i++) {
                try {
                    if (http.send(HttpRequest.newBuilder(URI.create(url + "/ready")).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                        break;
                    }
                } catch (Exception e) {
                    Thread.sleep(100);
                }
            }
            if (loadScale > 0) { // warm up, as a pod would before taking a day's traffic
                open(http, url, (int) (100 * loadScale), 10, null);
            }
            double[] cpu = new double[DAY.length * SLOT];
            double[] mem = new double[DAY.length * SLOT];
            Thread sampler = Thread.ofPlatform().start(() -> {
                long[] prev = proc(pid);
                long t = System.nanoTime();
                for (int i = 0; i < cpu.length; i++) {
                    t += 1_000_000_000L;
                    LockSupport.parkNanos(t - System.nanoTime());
                    long[] now = proc(pid);
                    cpu[i] = (now[0] - prev[0]) / 100.0; // clock ticks to CPU seconds over one second
                    mem[i] = now[1] / 1024.0;
                    prev = now;
                }
            });
            List<Long> all = new ArrayList<>();
            List<Long> peak = new ArrayList<>();
            long failed = 0;
            int[] busiest = Arrays.stream(DAY).boxed().sorted((a, b) -> b - a).limit(4).mapToInt(Integer::intValue).toArray();
            for (int rate : DAY) {
                int r = (int) Math.round(rate * loadScale);
                if (r == 0) {
                    Thread.sleep(SLOT * 1000L);
                    continue;
                }
                AtomicLong f = new AtomicLong();
                long[] lat = open(http, url, r, SLOT, f);
                failed += f.get();
                for (long v : lat) {
                    all.add(v);
                    if (Arrays.stream(busiest).anyMatch(b -> b == rate)) {
                        peak.add(v);
                    }
                }
            }
            sampler.join();
            return new Sampled(cpu, mem, all.stream().mapToLong(Long::longValue).sorted().toArray(), failed, peak.stream().mapToLong(Long::longValue).sorted().toArray());
        } finally {
            sh("docker", "rm", "-f", name);
        }
    }

    /** {@code rate} requests a second for {@code seconds}, each timed from when it was due; failures counted. */
    static long[] open(HttpClient http, String url, int rate, int seconds, AtomicLong failed) throws Exception {
        return open(http, url, rate, seconds, failed, false);
    }

    /** With {@code fresh}, every request on a new connection, so the Service balances each one (as a mesh would). */
    static long[] open(HttpClient http, String url, int rate, int seconds, AtomicLong failed, boolean fresh) throws Exception {
        int total = rate * seconds;
        long[] lat = new long[total];
        Arrays.fill(lat, -1);
        CountDownLatch done = new CountDownLatch(total);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url + "/menu?r=" + rate)).timeout(Duration.ofSeconds(fresh ? 2 : 10));
        HttpRequest req = (fresh ? b.header("Connection", "close") : b).build();
        long t0 = System.nanoTime();
        for (int i = 0; i < total; i++) {
            long due = t0 + (long) (i * 1e9 / rate);
            long wait = due - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            int idx = i;
            Thread.ofVirtual().start(() -> {
                try {
                    if (http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                        lat[idx] = (System.nanoTime() - due) / 1000;
                    } else if (failed != null) {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (failed != null) {
                        failed.incrementAndGet();
                    }
                }
                done.countDown();
            });
        }
        done.await();
        return Arrays.stream(lat).filter(v -> v >= 0).toArray();
    }

    static double p(long[] sorted, double pct) {
        if (sorted.length == 0) {
            return Double.NaN;
        }
        int i = (int) Math.ceil(pct / 100 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))] / 1000.0;
    }

    /** CPU clock ticks (user + system) and resident KiB of a process. */
    static long[] proc(long pid) {
        try {
            String stat = Files.readString(Path.of("/proc/" + pid + "/stat"));
            String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
            long ticks = Long.parseLong(f[11]) + Long.parseLong(f[12]);
            long rss = 0;
            for (String line : Files.readAllLines(Path.of("/proc/" + pid + "/status"))) {
                if (line.startsWith("VmRSS:")) {
                    rss = Long.parseLong(line.replaceAll("\\D", ""));
                }
            }
            return new long[] {ticks, rss};
        } catch (Exception e) {
            return new long[] {0, 0};
        }
    }

    // ------------------------------------------------------------------ the fleet: packing and showback

    static void fleet() throws Exception {
        if (FACTORS.isEmpty()) {
            throw new IllegalStateException("fleet uses the usage experiment's measurements: run 'usage fleet' together");
        }
        List<Fleet.Service> declared = Fleet.load(Path.of(System.getProperty("c70.fleet")));
        Map<String, double[]> target = new LinkedHashMap<>();
        Map<String, double[]> used = new LinkedHashMap<>();
        FACTORS.forEach((k, v) -> {
            target.put(k, new double[] {v[2], v[3]});
            used.put(k, new double[] {v[0], v[1]});
        });
        List<Fleet.Service> rightSized = Fleet.rightSize(declared, target);
        Fleet.Node node = new Fleet.Node(4, 16);
        Fleet.Node allocatable = new Fleet.Node(3.8, 14.5); // after the kubelet's and system reservations
        System.out.println("24 services, " + declared.stream().mapToInt(Fleet.Service::replicas).sum() + " pods; nodes of 4 vCPU and 16 GiB (3.8 and 14.5 allocatable);");
        System.out.println("prices: OpenCost's default list ($0.031611 per vCPU-hour, $0.004237 per GiB-hour; spot $0.006655 and $0.000892)");
        System.out.printf("%-34s %10s %12s %7s %13s %13s %16s %14s%n", "requests", "CPU", "memory", "nodes", "CPU alloc.", "mem alloc.", "on demand/month", "spot/month");
        for (Object[] row : new Object[][] {{"as declared", declared}, {"right-sized (VPA targets by kind)", rightSized}}) {
            @SuppressWarnings("unchecked")
            Fleet.Packing pk = Fleet.pack((List<Fleet.Service>) row[1], allocatable);
            System.out.printf("%-34s %10.1f %8.1f GiB %7d %12.0f%% %12.0f%% %15.0f$ %13.0f$%n", row[0], pk.cpuRequested(), pk.memRequested(), pk.nodes(), 100 * pk.cpuShare(),
                    100 * pk.memShare(), pk.nodes() * node.hourly(false) * Fleet.HOURS_PER_MONTH, pk.nodes() * node.hourly(true) * Fleet.HOURS_PER_MONTH);
        }
        System.out.println();
        Fleet.Packing pk = Fleet.pack(declared, allocatable);
        double monthly = pk.nodes() * node.hourly(false) * Fleet.HOURS_PER_MONTH;
        System.out.printf("the declared fleet's month ($%.0f on demand), divided among teams: by requests, by usage, by max(request, usage) per pod%n", monthly);
        System.out.printf("%-12s %14s %14s %16s%n", "team", "by requests", "by usage", "by max (OpenCost)");
        Map<String, double[]> show = Fleet.showback(declared, used, monthly);
        show.forEach((team, v) -> System.out.printf("%-12s %13.0f$ %13.0f$ %15.0f$%n", team, v[0], v[1], v[2]));
    }

    // ------------------------------------------------------------------ spot

    static void spot() throws Exception {
        String kubectl = System.getProperty("c70.kubectl", "kubectl");
        String manifest = Files.readString(Path.of(System.getProperty("c70.manifest")));
        System.out.println("menu-service copies of 0.25 CPU; a steady load at 60% of 6 copies' capacity; at 30 s the spot copies are reclaimed");
        System.out.println("(scaled to 0: SIGTERM, drain) and come back at 90 s, as replacement spot capacity would; 120 s per row; a new connection per request");
        try {
            kubectl(kubectl, "delete", "namespace", "c70", "--wait=true", "--timeout=120s");
            String url = deploy(kubectl, manifest, 1, 0);
            HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
            open(http, url, 20, 10, null, true);
            double capacity = closedThroughput(http, url, 16, 20);
            int rate = (int) Math.round(capacity * 6 * 0.6);
            System.out.printf("one copy's capacity: %.0f requests/s; load: %d requests/s%n", capacity, rate);
            System.out.printf("%-26s %10s %10s %13s %15s %12s %14s %16s%n", "copies", "on demand", "spot", "$ per hour", "served (all)", "failed", "p99 ms (all)", "served 30-90 s");
            int[][] rows = {{6, 0, 0}, {0, 6, 1}, {2, 4, 1}, {3, 3, 1}};
            for (int[] row : rows) {
                kubectl(kubectl, "delete", "namespace", "c70", "--wait=true", "--timeout=120s");
                url = deploy(kubectl, manifest, row[0], row[1]);
                open(http, url, rate, 10, null, true); // warm every copy
                List<long[]> windows = new ArrayList<>();
                AtomicLong failedAll = new AtomicLong();
                long[] servedWindow = new long[1];
                String u = url;
                Thread load = Thread.ofPlatform().start(() -> {
                    for (int w = 0; w < 12; w++) {
                        AtomicLong f = new AtomicLong();
                        try {
                            long[] lat = open(http, u, rate, 10, f, true);
                            windows.add(lat);
                            if (w >= 3 && w < 9) {
                                servedWindow[0] += lat.length;
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        failedAll.addAndGet(f.get());
                    }
                });
                if (row[2] == 1) {
                    Thread.sleep(30_000);
                    kubectl(kubectl, "-n", "c70", "scale", "deploy/menu-spot", "--replicas=0");
                    Thread.sleep(60_000);
                    kubectl(kubectl, "-n", "c70", "scale", "deploy/menu-spot", "--replicas=" + row[1]);
                }
                load.join();
                long[] all = windows.stream().flatMapToLong(Arrays::stream).sorted().toArray();
                double hourly = (row[0] * pod(false) + row[1] * pod(true));
                System.out.printf("%-26s %10d %10d %13.4f %14.1f%% %12d %14.0f %15.1f%%%n", row[2] == 1 ? "reclaimed 30-90 s" : "no interruption", row[0], row[1], hourly,
                        100.0 * all.length / (rate * 120), failedAll.get(), p(all, 99), 100.0 * servedWindow[0] / (rate * 60));
            }
        } finally {
            kubectl(kubectl, "delete", "namespace", "c70", "--wait=true", "--timeout=120s");
        }
    }

    /** A 0.25-CPU, 0.25-GiB pod's hourly price. */
    static double pod(boolean spot) {
        return spot ? 0.25 * Fleet.SPOT_CPU_HOUR + 0.25 * Fleet.SPOT_GIB_HOUR : 0.25 * Fleet.CPU_HOUR + 0.25 * Fleet.GIB_HOUR;
    }

    static double closedThroughput(HttpClient http, String url, int workers, int seconds) throws Exception {
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        AtomicLong n = new AtomicLong();
        List<Thread> ts = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            ts.add(Thread.ofVirtual().start(() -> {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/menu?r=closed")).header("Connection", "close").timeout(Duration.ofSeconds(10)).build();
                while (System.nanoTime() < end) {
                    try {
                        http.send(req, HttpResponse.BodyHandlers.discarding());
                        n.incrementAndGet();
                    } catch (Exception e) {
                        // not counted
                    }
                }
            }));
        }
        for (Thread t : ts) {
            t.join();
        }
        return n.get() / (double) seconds;
    }

    static String deploy(String kubectl, String manifest, int onDemand, int spot) throws Exception {
        String yaml = manifest.replace("ON_DEMAND", String.valueOf(onDemand)).replace("SPOT", String.valueOf(spot));
        Process p = new ProcessBuilder(cmd(kubectl, "apply", "-f", "-")).redirectErrorStream(true).start();
        p.getOutputStream().write(yaml.getBytes());
        p.getOutputStream().close();
        p.getInputStream().readAllBytes();
        p.waitFor();
        kubectl(kubectl, "-n", "c70", "rollout", "status", "deploy/menu-on-demand", "--timeout=180s");
        kubectl(kubectl, "-n", "c70", "rollout", "status", "deploy/menu-spot", "--timeout=180s");
        Thread.sleep(3000);
        return "http://" + kubectl(kubectl, "-n", "c70", "get", "svc", "menu", "-o", "jsonpath={.spec.clusterIP}").strip();
    }

    static List<String> cmd(String kubectl, String... args) {
        List<String> c = new ArrayList<>(List.of(kubectl.split(" ")));
        c.addAll(List.of(args));
        return c;
    }

    static String kubectl(String kubectl, String... args) throws Exception {
        return sh(cmd(kubectl, args).toArray(String[]::new));
    }

    static String sh(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        return out;
    }

    static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
