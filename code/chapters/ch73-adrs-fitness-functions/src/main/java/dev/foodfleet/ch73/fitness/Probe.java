package dev.foodfleet.ch73.fitness;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

/**
 * Runs a release as its own JVM and measures what the dynamic fitness functions need: the time from launching the
 * process to its first healthy answer, and request latencies under an open (arrival-rate) load, measured from each
 * request's scheduled time so a stalled server can't hide its stall (no coordinated omission).
 */
public final class Probe implements AutoCloseable {

    public record Latencies(double[] millis, int errors) {
        public double percentile(double p) {
            double[] s = millis.clone();
            Arrays.sort(s);
            return s[Math.min(s.length - 1, (int) Math.ceil(p / 100 * s.length) - 1)];
        }
    }

    private final Process process;
    private final int port;
    private final double startupMillis;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .executor(Executors.newVirtualThreadPerTaskExecutor()).connectTimeout(Duration.ofSeconds(2)).build();

    private Probe(Process process, int port, double startupMillis) {
        this.process = process;
        this.port = port;
        this.startupMillis = startupMillis;
    }

    /** Launches {@code mainClass} from {@code classes} with a 512 MiB heap and waits (up to 30 s) until it is healthy. */
    public static Probe start(Path classes, String mainClass) throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        long t0 = System.nanoTime();
        Process p = new ProcessBuilder(java, "-Xmx512m", "-cp", classes.toString(), mainClass, String.valueOf(port))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        HttpClient c = HttpClient.newHttpClient();
        HttpRequest health = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).timeout(Duration.ofSeconds(1)).build();
        while (true) {
            try {
                if (c.send(health, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    break;
                }
            } catch (IOException e) {
                // not listening yet
            }
            if (!p.isAlive() || System.nanoTime() - t0 > 30_000_000_000L) {
                p.destroyForcibly();
                throw new IllegalStateException("release did not become healthy");
            }
            Thread.sleep(5);
        }
        return new Probe(p, port, (System.nanoTime() - t0) / 1e6);
    }

    public double startupMillis() {
        return startupMillis;
    }

    /** Sends GET {@code path(i)} at {@code rate} per second for {@code seconds}; latency counts from the scheduled send time. */
    public Latencies load(int rate, double seconds, java.util.function.IntFunction<String> path) throws Exception {
        int n = (int) (rate * seconds);
        double[] lat = new double[n];
        int[] errors = new int[1];
        List<CompletableFuture<?>> all = new ArrayList<>(n);
        long start = System.nanoTime() + 20_000_000;
        for (int i = 0; i < n; i++) {
            long due = start + (long) (i * 1e9 / rate);
            long wait = due - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            int k = i;
            HttpRequest r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path.apply(i))).timeout(Duration.ofSeconds(5)).build();
            all.add(client.sendAsync(r, HttpResponse.BodyHandlers.discarding()).handle((resp, ex) -> {
                lat[k] = (System.nanoTime() - due) / 1e6;
                if (ex != null || resp.statusCode() != 200) {
                    synchronized (errors) {
                        errors[0]++;
                    }
                }
                return null;
            }));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
        return new Latencies(lat, errors[0]);
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
        }
    }
}
