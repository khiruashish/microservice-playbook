import dev.foodfleet.ch66.tail.FanOut;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Chapter 66's experiments. The services run in their own JVMs; this JVM generates the load.
 * <ul>
 *   <li>{@code models}: the quote endpoint (three sequential 50 ms downstream calls) in four concurrency models, under
 *       an open load of rising rates</li>
 *   <li>{@code tail}: fanning out to 1, 5, 20 and 50 backends whose calls are slow 1% of the time, with and without
 *       hedging</li>
 *   <li>{@code pinning}: 1,000 virtual threads blocking inside {@code synchronized}, on JDK 21 and on this JDK</li>
 * </ul>
 * System properties: c66.classes (the compiled services), c66.jdk21 (a JDK 21's java).
 */
public final class Lab {

    static final String CLASSES = System.getProperty("c66.classes");

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "models" -> models();
                case "tail" -> tail();
                case "pinning" -> pinning();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------ concurrency models

    static void models() throws Exception {
        int backendPort = freePort();
        Process backend = java(List.of("-Xmx256m"), "dev.foodfleet.ch66.backend.Backend", String.valueOf(backendPort), "50", "50", "0");
        waitPort(backendPort);
        System.out.println("quote: three sequential calls to services that answer in 50 ms (150 ms of waiting per request); the server JVM has -Xmx512m;");
        System.out.println("server, backend and load generator share the machine's 2 CPUs");
        System.out.println("open load, 20 s per rate, requests timed from when due, given up after 10 s; latencies are of successful requests");
        System.out.printf("%-16s %8s %10s %9s %9s %9s %9s %8s %10s %12s%n", "model", "offered", "served/s", "p50 ms", "p99 ms", "max ms", "rejected", "failed", "OS threads", "peak RSS");
        try {
            for (String model : List.of("platform", "virtual", "async", "virtual-limited")) {
                int port = freePort();
                Process server = java(List.of("-Xmx512m"), "dev.foodfleet.ch66.quote.QuoteServer", model, String.valueOf(port), "http://127.0.0.1:" + backendPort);
                waitPort(port);
                String url = "http://127.0.0.1:" + port + "/quote";
                open(url, 200, 5); // warm-up
                for (int rate : new int[] {250, 1000, 1500, 2500}) {
                    long[] peak = new long[2];
                    Thread sampler = Thread.ofPlatform().daemon().start(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            long[] now = proc(server.pid());
                            peak[0] = Math.max(peak[0], now[0]);
                            peak[1] = Math.max(peak[1], now[1]);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    });
                    Result r = open(url, rate, 20);
                    sampler.interrupt();
                    System.out.printf("%-16s %8d %10.0f %9.1f %9.1f %9.0f %9d %8d %10d %9d MB%s%n", model, rate, r.served, r.p(50), r.p(99), r.p(100), r.rejected, r.failed, peak[0], peak[1] / 1024,
                            server.isAlive() ? "" : "   the server died (see its OutOfMemoryError above)");
                    if (!server.isAlive()) {
                        break;
                    }
                    Thread.sleep(3000); // let any queue drain
                }
                server.destroy();
                server.waitFor();
            }
        } finally {
            backend.destroy();
        }
    }

    record Result(long[] micros, double served, long failed, long rejected) {
        double p(double pct) {
            if (micros.length == 0) {
                return Double.NaN;
            }
            int i = (int) Math.ceil(pct / 100 * micros.length) - 1;
            return micros[Math.max(0, Math.min(micros.length - 1, i))] / 1000.0;
        }
    }

    /**
     * An open load: {@code rate} requests a second for {@code seconds}, each timed from its due time. Percentiles are
     * of the successful requests; 503s are counted as rejected, anything else (errors, the 10 s timeout) as failed.
     */
    static Result open(String url, int rate, int seconds) throws Exception {
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build();
        int total = rate * seconds;
        long[] lat = new long[total];
        AtomicLong failed = new AtomicLong();
        AtomicLong rejected = new AtomicLong();
        AtomicLong ok = new AtomicLong();
        CountDownLatch done = new CountDownLatch(total);
        long t0 = System.nanoTime();
        long[] lastDone = new long[1];
        for (int i = 0; i < total; i++) {
            long due = t0 + (long) (i * 1e9 / rate);
            long wait = due - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            int idx = i;
            Thread.ofVirtual().start(() -> {
                lat[idx] = -1;
                try {
                    int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                    if (status == 200) {
                        ok.incrementAndGet();
                        long now = System.nanoTime();
                        lat[idx] = (now - due) / 1000;
                        synchronized (lastDone) {
                            lastDone[0] = Math.max(lastDone[0], now);
                        }
                    } else if (status == 503) {
                        rejected.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
                done.countDown();
            });
        }
        done.await();
        long[] sorted = Arrays.stream(lat).filter(v -> v >= 0).sorted().toArray();
        double span = lastDone[0] == 0 ? seconds : (lastDone[0] - t0) / 1e9;
        return new Result(sorted, ok.get() / span, failed.get(), rejected.get());
    }

    /** Threads and resident memory (KB) of a process, from /proc. */
    static long[] proc(long pid) {
        long threads = 0;
        long rss = 0;
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of("/proc/" + pid + "/status"));
        } catch (Exception e) {
            return new long[] {0, 0}; // the process is gone
        }
        for (String line : lines) {
            if (line.startsWith("Threads:")) {
                threads = Long.parseLong(line.replaceAll("\\D", ""));
            } else if (line.startsWith("VmRSS:")) {
                rss = Long.parseLong(line.replaceAll("\\D", ""));
            }
        }
        return new long[] {threads, rss};
    }

    // ------------------------------------------------------------------ tail

    static void tail() throws Exception {
        int port = freePort();
        Process backend = java(List.of("-Xmx256m"), "dev.foodfleet.ch66.backend.Backend", String.valueOf(port), "5", "200", "0.01");
        waitPort(port);
        System.out.println("each backend call takes 5 ms, and 200 ms for 1 in 100; a request fans out to n calls in parallel and waits for all;");
        System.out.println("2,000 requests per row; hedging sends a second copy of a call that hasn't answered in 20 ms");
        System.out.printf("%-4s %-11s %-22s %9s %9s %9s %9s %18s %12s%n", "n", "in flight", "hedged", "p50 ms", "p90 ms", "p99 ms", "max ms", "requests > 100 ms", "extra calls");
        try {
            FanOut warm = new FanOut("http://127.0.0.1:" + port);
            for (int i = 0; i < 500; i++) {
                warm.request(5, 0);
            }
            record Row(int n, int concurrency, long hedge, double budget) {
            }
            List<Row> rows = new ArrayList<>();
            for (int n : new int[] {1, 5, 20, 50}) {
                rows.add(new Row(n, 4, 0, 1.0));
                rows.add(new Row(n, 4, 20, 1.0));
            }
            // the same fan-out of 50 with five times as many requests in flight, where 20 ms is no longer slow
            rows.add(new Row(50, 20, 0, 1.0));
            rows.add(new Row(50, 20, 20, 1.0));
            rows.add(new Row(50, 20, 20, 0.05));
            for (Row row : rows) {
                FanOut fo = new FanOut("http://127.0.0.1:" + port, row.budget());
                long[] lat = new long[2000];
                var pool = Executors.newFixedThreadPool(row.concurrency());
                List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < lat.length; i++) {
                    int idx = i;
                    fs.add(pool.submit(() -> {
                        lat[idx] = fo.request(row.n(), row.hedge());
                        return null;
                    }));
                }
                for (var x : fs) {
                    x.get();
                }
                pool.shutdown();
                Arrays.sort(lat);
                Result r = new Result(lat, 0, 0, 0);
                long slow = Arrays.stream(lat).filter(v -> v > 100_000).count();
                String hedged = row.hedge() == 0 ? "no" : row.budget() < 1 ? "20 ms, at most 5%" : "20 ms";
                System.out.printf("%-4d %-11d %-22s %9.1f %9.1f %9.1f %9.0f %18s %12s%n", row.n(), row.concurrency() * row.n(), hedged, r.p(50), r.p(90), r.p(99), r.p(100),
                        String.format("%d (%.1f%%)", slow, 100.0 * slow / lat.length),
                        row.hedge() == 0 ? "-" : String.format("%.1f%%", 100.0 * fo.hedges.get() / (fo.calls.get() - fo.hedges.get())));
            }
        } finally {
            backend.destroy();
        }
    }

    // ------------------------------------------------------------------ pinning

    static void pinning() throws Exception {
        System.out.println("1,000 virtual threads each block 50 ms holding their own lock (no contention), on 2 CPUs");
        for (String java : List.of(System.getProperty("c66.jdk21"), Path.of(System.getProperty("java.home"), "bin", "java").toString())) {
            for (String mode : List.of("none", "synchronized", "reentrantlock")) {
                ProcessBuilder pb = new ProcessBuilder(java, "-cp", CLASSES, "dev.foodfleet.ch66.pinning.Pinning", mode).redirectErrorStream(true);
                pb.environment().remove("JAVA_TOOL_OPTIONS");
                Process p = pb.start();
                System.out.print("   " + new String(p.getInputStream().readAllBytes()));
                p.waitFor();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static Process java(List<String> jvm, String main, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        cmd.addAll(jvm);
        cmd.addAll(List.of("-cp", CLASSES, main));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        return pb.start();
    }

    static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    static void waitPort(int port) throws Exception {
        for (int i = 0; i < 400; i++) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                return;
            } catch (Exception e) {
                Thread.sleep(25);
            }
        }
        throw new IllegalStateException("port " + port + " not open");
    }
}
