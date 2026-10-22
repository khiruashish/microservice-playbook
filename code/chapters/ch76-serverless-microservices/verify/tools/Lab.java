import dev.foodfleet.ch76.platform.Prices;
import dev.foodfleet.ch76.platform.Simulation;

import java.io.IOException;
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
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * Chapter 76's experiments.
 * <ul>
 *   <li>{@code starts}: two functions (a light fee quote, a CPU-heavy thumbnail) hosted the way a FaaS runtime hosts
 *       them, each in its own JVM, cold-started repeatedly in four configurations: default JVM, C1 only with Serial GC
 *       (the usual FaaS advice), a JDK 25 AOT cache, and both. Initialisation, first request, memory, warm requests.</li>
 *   <li>{@code day}: four workloads over a simulated day on a FaaS platform, a scale-to-zero container platform and
 *       always-on containers, driven by the times just measured and list prices: cold starts, latency, cost.</li>
 *   <li>{@code breakeven}: the sustained request rate above which containers cost less than functions.</li>
 * </ul>
 */
public final class Lab {

    static final Path WORK = Path.of(System.getProperty("c76.work", "build/work"));
    static final String CP = System.getProperty("c76.classes", "build/functions.jar");
    static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();

    record Fn(String name, String className, String body, int warmRequests, int starts) {
    }

    static final Fn FEE = new Fn("fee quote", "dev.foodfleet.ch76.functions.FeeQuote", "{\"km\":6.5,\"basket\":32000,\"surge\":1.2}", 200, 12);
    static final Fn THUMB = new Fn("thumbnail", "dev.foodfleet.ch76.functions.Thumbnail", "{\"photo\":\"menu/123.jpg\"}", 30, 8);
    static final String[] VARIANTS = {"JVM", "C1 + Serial GC", "AOT cache", "AOT cache + C1 + Serial"};
    static final Map<String, Simulation.Measured> MEASURED = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK);
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "starts" -> starts();
                case "day" -> day();
                case "breakeven" -> breakeven();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ------------------------------------------------------------------ starts

    static List<String> flags(String variant, Fn f) {
        List<String> a = new ArrayList<>(List.of("-Xmx1g", "-Djava.awt.headless=true"));
        if (variant.contains("C1")) {
            a.addAll(List.of("-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC"));
        }
        if (variant.startsWith("AOT")) {
            a.add("-XX:AOTCache=" + aot(f, variant));
        }
        return a;
    }

    static Path aot(Fn f, String variant) {
        return WORK.resolve(f.className().substring(f.className().lastIndexOf('.') + 1) + (variant.contains("C1") ? "-c1" : "") + ".aot");
    }

    static void starts() throws Exception {
        System.out.printf("%-10s %-24s %11s %11s %15s %8s %12s %14s%n", "function", "configuration", "init p50", "first req", "cold p50/p99", "RSS MB",
                "warm p50", "after 2,000");
        for (Fn f : List.of(FEE, THUMB)) {
            for (String v : VARIANTS) {
                String trained = "";
                if (v.startsWith("AOT")) {
                    long t = System.nanoTime();
                    train(f, v);
                    trained = String.format("  (training %.1f s, cache %.0f MB)", (System.nanoTime() - t) / 1e9, Files.size(aot(f, v)) / 1e6);
                }
                double[] init = new double[f.starts()];
                double[] first = new double[f.starts()];
                double[] cold = new double[f.starts()];
                double[] rss = new double[f.starts()];
                List<Double> warm = new ArrayList<>();
                double[] late = null;
                for (int s = 0; s < f.starts(); s++) {
                    int port = port();
                    List<String> cmd = new ArrayList<>(List.of(JAVA));
                    cmd.addAll(flags(v, f));
                    cmd.addAll(List.of("-cp", CP, "dev.foodfleet.ch76.functions.FunctionHost", String.valueOf(port), f.className()));
                    long t0 = System.nanoTime();
                    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                    try {
                        waitHealthy(port, p);
                        init[s] = (System.nanoTime() - t0) / 1e6;
                        long t1 = System.nanoTime();
                        post(port, f.body());
                        first[s] = (System.nanoTime() - t1) / 1e6;
                        cold[s] = init[s] + first[s];
                        rss[s] = rssMb(p.pid());
                        for (int i = 0; i < f.warmRequests(); i++) {
                            long t2 = System.nanoTime();
                            post(port, f.body());
                            if (i >= f.warmRequests() / 4) {
                                warm.add((System.nanoTime() - t2) / 1e6);
                            }
                        }
                        if (s == f.starts() - 1) {
                            int more = f == FEE ? 2_000 : 300;
                            for (int i = 0; i < more; i++) {
                                post(port, f.body());
                            }
                            late = new double[f == FEE ? 500 : 50];
                            for (int i = 0; i < late.length; i++) {
                                long t3 = System.nanoTime();
                                post(port, f.body());
                                late[i] = (System.nanoTime() - t3) / 1e6;
                            }
                        }
                    } finally {
                        p.destroy();
                        p.waitFor();
                    }
                }
                double[] w = warm.stream().mapToDouble(Double::doubleValue).toArray();
                MEASURED.put(f.name() + "/" + v, new Simulation.Measured(cold, init, w));
                System.out.printf("%-10s %-24s %8.0f ms %8.1f ms %7.0f/%4.0f ms %8.0f %9.2f ms %11.2f ms%s%n", f.name(), v, pct(init, 50), pct(first, 50), pct(cold, 50),
                        pct(cold, 99), pct(rss, 50), pct(w, 50), pct(late, 50), trained);
            }
        }
        System.out.printf("(init: launch until /health answers; first req: the first call of the function; warm: requests %d-%d (fee) and %d-%d (thumbnail) of each start; after 2,000: after 2,000 (fee) or 300 (thumbnail) more)%n",
                FEE.warmRequests() / 4 + 1, FEE.warmRequests(), THUMB.warmRequests() / 4 + 1, THUMB.warmRequests());
    }

    static void train(Fn f, String variant) throws Exception {
        Path cache = aot(f, variant);
        Files.deleteIfExists(cache);
        int port = port();
        List<String> cmd = new ArrayList<>(List.of(JAVA, "-Xmx1g", "-Djava.awt.headless=true", "-Dff.exitEndpoint=true", "-XX:AOTCacheOutput=" + cache));
        if (variant.contains("C1")) {
            cmd.addAll(List.of("-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC"));
        }
        cmd.addAll(List.of("-cp", CP, "dev.foodfleet.ch76.functions.FunctionHost", String.valueOf(port), f.className()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(WORK.resolve("train.log").toFile()).start();
        waitHealthy(port, p);
        for (int i = 0; i < (f == FEE ? 500 : 20); i++) {
            post(port, f.body());
        }
        try {
            CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/exit")).build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // the JVM may exit before answering
        }
        if (p.waitFor() != 0 || !Files.exists(cache)) {
            throw new IllegalStateException("training failed: " + Files.readString(WORK.resolve("train.log")));
        }
    }

    static void waitHealthy(int port, Process p) throws Exception {
        HttpRequest h = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).timeout(Duration.ofSeconds(1)).build();
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (true) {
            try {
                if (CLIENT.send(h, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // not yet
            }
            if (!p.isAlive() || System.nanoTime() > deadline) {
                throw new IllegalStateException("function host didn't start");
            }
            Thread.sleep(2);
        }
    }

    static void post(int port, String body) throws Exception {
        HttpResponse<String> r = CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException(r.statusCode() + " " + r.body());
        }
    }

    static double rssMb(long pid) throws IOException {
        for (String line : Files.readAllLines(Path.of("/proc/" + pid + "/status"))) {
            if (line.startsWith("VmRSS:")) {
                return Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024.0;
            }
        }
        return 0;
    }

    static int port() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    // ------------------------------------------------------------------ the day

    record Workload(String name, Fn fn, DoubleUnaryOperator rate, double peak) {
    }

    static List<Workload> workloads() {
        double[] bursts = new double[24];
        Random r = new Random(76);
        for (int i = 0; i < bursts.length; i++) {
            bursts[i] = r.nextDouble() * (86_400 - 60);
        }
        return List.of(
                new Workload("partner webhooks: 2,000 a day, evenly", FEE, t -> 2_000 / 86_400.0, 2_000 / 86_400.0),
                new Workload("menu photo uploads: 24 bursts of 400 in a minute", THUMB, t -> {
                    for (double b : bursts) {
                        if (t >= b && t < b + 60) {
                            return 400 / 60.0;
                        }
                    }
                    return 0;
                }, 400 / 60.0),
                new Workload("checkout fee quotes: lunch and dinner peaks", FEE, t -> {
                    double h = t / 3_600;
                    return 2 + 60 * Math.exp(-Math.pow((h - 13) / 1.2, 2)) + 90 * Math.exp(-Math.pow((h - 20.5) / 1.5, 2));
                }, 92),
                new Workload("thumbnails all day: 12 a second", THUMB, t -> 12, 12));
    }

    static void day() throws Exception {
        if (MEASURED.isEmpty()) {
            starts();
        }
        System.out.println("prices (AWS us-east-1, x86, on demand): Lambda $" + Prices.LAMBDA_GB_SECOND + "/GB-s at 1,769 MB (1 vCPU), $" + Prices.LAMBDA_PER_MILLION
                + "/million requests, duration and INIT billed per ms; container 1 vCPU + 2 GB $" + String.format("%.6f", Prices.INSTANCE_SECOND * 3_600) + "/hour, per second, 1 minute minimum");
        System.out.printf("%-48s %-44s %10s %8s %9s %9s %10s %10s %11s%n", "workload", "platform", "requests", "cold", "p50 ms", "p99 ms", "max ms", "inst-hours", "$ per day");
        long seed = 760;
        for (Workload w : workloads()) {
            double[] arrivals = Simulation.arrivals(w.rate(), Math.max(w.peak(), 1e-6), seed++);
            Simulation.Measured jvm = MEASURED.get(w.fn().name() + "/JVM");
            String bestName = null;
            Simulation.Measured best = null;
            for (String v : VARIANTS) {
                Simulation.Measured m = MEASURED.get(w.fn().name() + "/" + v);
                if (best == null || pct(m.coldMillis(), 50) < pct(best.coldMillis(), 50)) {
                    best = m;
                    bestName = v;
                }
            }
            Map<String, Simulation.Result> rows = new LinkedHashMap<>();
            rows.put("functions, JVM, kept 5 min", Simulation.faas(arrivals, jvm, 300, seed));
            rows.put("functions, " + bestName + ", kept 1 min", Simulation.faas(arrivals, best, 60, seed));
            rows.put("functions, " + bestName + ", kept 5 min", Simulation.faas(arrivals, best, 300, seed));
            rows.put("functions, " + bestName + ", kept 15 min", Simulation.faas(arrivals, best, 900, seed));
            rows.put("scale-to-zero containers, " + bestName, Simulation.scaleToZero(arrivals, best, seed));
            rows.put("always-on containers, JVM", Simulation.alwaysOn(arrivals, jvm, w.peak(), seed));
            for (var e : rows.entrySet()) {
                Simulation.Result x = e.getValue();
                System.out.printf("%-48s %-44s %10d %8s %9.2f %9.1f %10.0f %10.2f %11.4f%n", w.name(), e.getKey(), x.requests(),
                        String.format("%.2f%%", 100.0 * x.cold() / Math.max(1, x.requests())), x.p50(), x.p99(), x.max(), x.instanceHours(), x.cost());
            }
        }
    }

    static void breakeven() throws Exception {
        if (MEASURED.isEmpty()) {
            starts();
        }
        for (Fn f : List.of(FEE, THUMB)) {
            Simulation.Measured m = MEASURED.get(f.name() + "/JVM");
            double[] w = m.warmMillis().clone();
            Arrays.sort(w);
            double d = w[w.length / 2];
            double perRequest = Prices.lambda(d);
            double instance = Prices.INSTANCE_SECOND;
            double perRequestBusy = instance / (0.7 * 1_000 / d);
            double twoInstances = 2 * instance;
            System.out.printf("%-10s warm %.2f ms: a function call costs $%.3g; on a container at 70%% busy, $%.3g (functions cost %.1fx); two always-on containers ($%.2f/day) cost less than functions above %.1f requests/s (%.2f million a day)%n",
                    f.name(), d, perRequest, perRequestBusy, perRequest / perRequestBusy, twoInstances * 86_400, twoInstances / perRequest, twoInstances / perRequest * 86_400 / 1e6);
        }
    }

    static double pct(double[] v, double p) {
        if (v == null || v.length == 0) {
            return 0;
        }
        double[] s = v.clone();
        Arrays.sort(s);
        return s[Math.max(0, Math.min(s.length - 1, (int) Math.ceil(p / 100 * s.length) - 1))];
    }
}
