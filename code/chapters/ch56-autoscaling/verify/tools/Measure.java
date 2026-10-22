import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntUnaryOperator;

/**
 * Chapter 56's experiments: a real HorizontalPodAutoscaler (autoscaling/v2, CPU utilization, metrics-server 0.9 at a
 * 15 s resolution) on k3s 1.37 scaling menu-service ({@code ScaleLab}) under open-loop load. Every request is
 * {@code /work?ms=20} on a new connection to the Service.
 * <ul>
 *   <li>{@code step}: 30 requests/s (600m of CPU) for 4 minutes, then nothing; how fast up, how slow down</li>
 *   <li>{@code warmup}: new pods burn a CPU for 30 s at startup; readiness gated on warm-up or not</li>
 *   <li>{@code flap}: load on for 60 s, off for 60 s; the default 300 s scale-down window against none</li>
 * </ul>
 * System properties: c56.kubectl, c56.k8s (the directory with deployment.yaml and hpa.yaml).
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c56.kubectl", "kubectl").split(" ");
    static final Path K8S = Path.of(System.getProperty("c56.k8s"));
    static final int WORK_MS = 20;

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            switch (mode) {
                case "step" -> step();
                case "warmup" -> warmup();
                case "flap" -> flap();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ===================================================================================================== helpers

    static String kubectl(String stdin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(KUBECTL));
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        out = out.lines().filter(l -> !l.contains("DEPRECATION")).reduce((a, b) -> a + "\n" + b).orElse("");
        if (code != 0 && !Arrays.asList(args).contains("--ignore-not-found")) {
            throw new IllegalStateException(String.join(" ", args) + ": " + out);
        }
        return out;
    }

    record Variant(String name, int minReplicas, int warmupSeconds, boolean gate, int scaleDownWindow) {
    }

    record Sample(double t, int spec, int ready, String utilization, int rps) {
    }

    record Done(double t, double ms, boolean ok) {
    }

    record Result(List<Sample> samples, List<Done> requests, double loadStart) {
    }

    /** Deploys a variant, waits for the HPA to have metrics, then runs the load profile (requests/s by second). */
    static Result run(Variant v, IntUnaryOperator rps, int seconds) throws Exception {
        String ns = "hpa-" + v.name().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        kubectl(null, "delete", "namespace", ns, "--ignore-not-found", "--wait=true", "--timeout=180s");
        kubectl(null, "create", "namespace", ns);
        String deployment = Files.readString(K8S.resolve("deployment.yaml"))
                .replace("{name: WARMUP_SECONDS, value: \"0\"}", "{name: WARMUP_SECONDS, value: \"" + v.warmupSeconds() + "\"}")
                .replace("{name: READY_AFTER_WARMUP, value: \"true\"}", "{name: READY_AFTER_WARMUP, value: \"" + v.gate() + "\"}");
        String hpa = Files.readString(K8S.resolve("hpa.yaml"))
                .replace("minReplicas: 1", "minReplicas: " + v.minReplicas())
                .replace("stabilizationWindowSeconds: 300", "stabilizationWindowSeconds: " + v.scaleDownWindow());
        kubectl(deployment, "apply", "-n", ns, "-f", "-");
        kubectl(null, "-n", ns, "scale", "deployment/menu-service", "--replicas=" + v.minReplicas());
        kubectl(null, "-n", ns, "rollout", "status", "deployment/menu-service", "--timeout=180s");
        kubectl(hpa, "apply", "-n", ns, "-f", "-");
        for (int i = 0; i < 120 && hpaStatus(ns)[2].equals("-"); i++) {      // until the HPA has seen metrics
            Thread.sleep(1000);
        }
        Thread.sleep(15_000);
        String ip = kubectl(null, "-n", ns, "get", "svc", "menu-service", "-o", "jsonpath={.spec.clusterIP}").trim();
        ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
        List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean running = new AtomicBoolean(true);
        long t0 = System.nanoTime();
        Thread load = Thread.ofPlatform().daemon().start(() -> {
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int s = 0; s < seconds && running.get(); s++) {
                    int n = rps.applyAsInt(s);
                    for (int i = 0; i < n; i++) {
                        long at = t0 + s * 1_000_000_000L + i * 1_000_000_000L / n;
                        long wait = at - System.nanoTime();
                        if (wait > 0) {
                            TimeUnit.NANOSECONDS.sleep(wait);
                        }
                        pool.submit(() -> done.add(request(ip, t0)));
                    }
                    long wait = t0 + (s + 1) * 1_000_000_000L - System.nanoTime();
                    if (wait > 0) {
                        TimeUnit.NANOSECONDS.sleep(wait);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        while (load.isAlive()) {
            double t = (System.nanoTime() - t0) / 1e9;
            String[] h = hpaStatus(ns);
            samples.add(new Sample(t, Integer.parseInt(h[0]), Integer.parseInt(h[1]), h[2], rps.applyAsInt((int) Math.min(seconds - 1, t))));
            Thread.sleep(5000);
        }
        running.set(false);
        kubectl(null, "delete", "namespace", ns, "--wait=false");
        return new Result(samples, new ArrayList<>(done), 0);
    }

    /** spec replicas, ready replicas, current CPU utilization of the request (as the HPA last saw it). */
    static String[] hpaStatus(String ns) throws Exception {
        String d = kubectl(null, "-n", ns, "get", "deployment", "menu-service", "-o", "jsonpath={.spec.replicas} {.status.readyReplicas}").trim();
        String u = kubectl(null, "-n", ns, "get", "hpa", "menu-service", "-o", "jsonpath={.status.currentMetrics[0].resource.current.averageUtilization}",
                "--ignore-not-found").trim();
        String[] p = d.split(" ");
        return new String[] {p[0], p.length > 1 && !p[1].isEmpty() ? p[1] : "0", u.isEmpty() ? "-" : u};
    }

    static Done request(String ip, long t0) {
        long start = System.nanoTime();
        boolean ok;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, 80), 2000);
            s.setSoTimeout(10_000);
            s.getOutputStream().write(("GET /work?ms=" + WORK_MS + " HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            ok = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).startsWith("HTTP/1.1 200");
        } catch (IOException e) {
            ok = false;
        }
        long end = System.nanoTime();
        return new Done((start - t0) / 1e9, (end - start) / 1e6, ok);
    }

    static double p(List<Done> ds, double q) {
        List<Double> ms = ds.stream().filter(Done::ok).map(Done::ms).sorted().toList();
        return ms.isEmpty() ? Double.NaN : ms.get(Math.min(ms.size() - 1, (int) Math.ceil(q * ms.size()) - 1));
    }

    static List<Done> between(List<Done> ds, double from, double to) {
        return ds.stream().filter(d -> d.t() >= from && d.t() < to).toList();
    }

    static long failed(List<Done> ds) {
        return ds.stream().filter(d -> !d.ok()).count();
    }

    /** First time at or after {@code from} when spec replicas satisfy the predicate. */
    static String firstAt(Result r, double from, java.util.function.IntPredicate spec) {
        return r.samples().stream().filter(s -> s.t() >= from && spec.test(s.spec())).findFirst().map(s -> String.format("%.0f s", s.t() - from)).orElse("not seen");
    }

    static void timeline(Result r, int every) {
        System.out.println("   t     load  replicas (desired/ready)  CPU of request  p50 / p99 latency  failed");
        for (int t = 0; ; t += every) {
            final int at = t;
            Sample s = r.samples().stream().filter(x -> x.t() >= at).findFirst().orElse(null);
            if (s == null) {
                break;
            }
            List<Done> w = between(r.requests(), t, t + every);
            System.out.printf("%5d s  %3d/s  %3d / %-3d                %4s%%           %s  %6d%n", t, s.rps(), s.spec(), s.ready(), s.utilization(),
                    w.isEmpty() ? "      -          " : String.format("%5.0f / %5.0f ms ", p(w, 0.5), p(w, 0.99)), failed(w));
        }
    }

    // ======================================================================================================== step

    static void step() throws Exception {
        int on = 30;
        int start = 30;
        int stop = 270;
        System.out.printf("HPA: 50%% of a 200m request, min 1, max 8, Kubernetes' default behavior. Load: %d requests/s x %d ms of CPU (%dm)%n", on, WORK_MS, on * WORK_MS);
        System.out.printf("from %d s to %d s; pods warm up for 5 s (readiness-gated)%n", start, stop);
        Result r = run(new Variant("step", 1, 5, true, 300), s -> s >= start && s < stop ? on : 0, 720);
        timeline(r, 30);
        int peak = r.samples().stream().mapToInt(Sample::spec).max().orElse(0);
        System.out.printf("first scale-up %s after the load started; peak %d replicas, reached %s after the load started%n", firstAt(r, start, n -> n > 1), peak,
                firstAt(r, start, n -> n == peak));
        System.out.printf("after the load stopped: first scale-down %s later, back to 1 replica %s later%n", firstAt(r, stop, n -> n < peak), firstAt(r, stop, n -> n == 1));
        List<Done> first = between(r.requests(), start, start + 60);
        List<Done> later = between(r.requests(), start + 120, stop);
        System.out.printf("latency p99: first minute of load %.0f ms (%d failed), minutes 2-4 %.0f ms (%d failed)%n", p(first, 0.99), failed(first), p(later, 0.99),
                failed(later));
        System.out.println("slow (over 1 s) or failed requests, by 5 s window: " + trouble(r));
        System.out.println();
    }

    // ====================================================================================================== warmup

    static void warmup() throws Exception {
        int on = 20;
        System.out.printf("HPA: 50%% of a 200m request, min 2, max 8. Load: %d requests/s x %d ms (%dm of CPU: 4 pods' worth) from t = 0 for 4 minutes.%n", on,
                WORK_MS, on * WORK_MS);
        System.out.println("Each new pod burns a full CPU for its first 30 s, like a cold JVM compiling.");
        System.out.println("variant                                            peak replicas  replicas at 1 / 2 / 4 min   p99 (0-2 min / 2-4 min)  failed");
        for (Variant v : List.of(new Variant("ready-after-warmup", 2, 30, true, 300), new Variant("ready-at-once", 2, 30, false, 300))) {
            Result r = run(v, s -> on, 240);
            int peak = r.samples().stream().mapToInt(Sample::spec).max().orElse(0);
            System.out.printf("%-50s %-14d %-27s %5.0f / %5.0f ms          %d%n", v.gate() ? "readiness fails until warm (HPA ignores warm-up)" : "ready at once (warm-up CPU counted)",
                    peak, at(r, 60) + " / " + at(r, 120) + " / " + at(r, 235), p(between(r.requests(), 0, 120), 0.99), p(between(r.requests(), 120, 240), 0.99),
                    failed(r.requests()));
            System.out.println("    slow (over 1 s) or failed, by 5 s window: " + trouble(r));
        }
        System.out.println();
    }

    /** Windows of 5 s with requests that failed or took over a second, as "from-to s: n". */
    static String trouble(Result r) {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < 1200; t += 5) {
            final int from = t;
            long n = r.requests().stream().filter(d -> d.t() >= from && d.t() < from + 5 && (!d.ok() || d.ms() > 1000)).count();
            if (n > 0) {
                sb.append(from).append('-').append(from + 5).append(" s: ").append(n).append("  ");
            }
        }
        return sb.isEmpty() ? "none" : sb.toString().trim();
    }

    static String at(Result r, double t) {
        return r.samples().stream().filter(s -> s.t() >= t).findFirst().map(s -> String.valueOf(s.spec())).orElse("-");
    }

    // ======================================================================================================== flap

    static void flap() throws Exception {
        int on = 24;
        System.out.printf("HPA: 50%% of a 200m request, min 1, max 8. Load: %d requests/s x %d ms (%dm) for 60 s, then 60 s off, 4 times.%n", on, WORK_MS,
                on * WORK_MS);
        System.out.println("Pods warm up for 10 s (readiness-gated).");
        System.out.println("scale-down window   replica changes  pods started  replicas during on / off phases   p99 first 20 s of each on phase  failed");
        for (Variant v : List.of(new Variant("window-300", 1, 10, true, 300), new Variant("window-0", 1, 10, true, 0))) {
            Result r = run(v, s -> (s / 60) % 2 == 0 ? on : 0, 480);
            int changes = 0;
            int started = 0;
            for (int i = 1; i < r.samples().size(); i++) {
                int d = r.samples().get(i).spec() - r.samples().get(i - 1).spec();
                if (d != 0) {
                    changes++;
                }
                started += Math.max(0, d);
            }
            StringBuilder phases = new StringBuilder();
            List<Done> firsts = new ArrayList<>();
            for (int k = 0; k < 8; k++) {
                final int from = k * 60;
                int mx = r.samples().stream().filter(s -> s.t() >= from && s.t() < from + 60).mapToInt(Sample::spec).max().orElse(0);
                int mn = r.samples().stream().filter(s -> s.t() >= from && s.t() < from + 60).mapToInt(Sample::spec).min().orElse(0);
                phases.append(mn == mx ? String.valueOf(mx) : mn + "-" + mx).append(k % 2 == 0 ? "/" : " ");
                if (k % 2 == 0) {
                    firsts.addAll(between(r.requests(), from, from + 20));
                }
            }
            System.out.printf("%-19s %-16d %-13d %-33s %6.0f ms                        %d%n", v.scaleDownWindow() + " s", changes, started, phases.toString().trim(),
                    p(firsts, 0.99), failed(r.requests()));
            System.out.println("    slow (over 1 s) or failed, by 5 s window: " + trouble(r));
        }
        System.out.println();
    }
}
