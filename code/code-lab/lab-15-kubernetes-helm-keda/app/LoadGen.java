import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An open-loop load generator: requests start on schedule whether or not earlier ones finished.
 * java -cp /app LoadGen <url> <requests per second> <seconds>; prints a line every 10 s and a summary.
 */
public final class LoadGen {

    public static void main(String[] args) throws Exception {
        URI uri = URI.create(args[0]);
        int rate = Integer.parseInt(args[1]);
        int seconds = Integer.parseInt(args[2]);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long i = 0; i < (long) rate * seconds; i++) {
                long due = start + i * 1_000_000_000L / rate;
                long wait = due - System.nanoTime();
                if (wait > 0) {
                    Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                }
                if (i > 0 && i % (rate * 10L) == 0) {
                    System.out.printf("  load: %d s, %d ok, %d failed%n", i / rate, ok.get(), failed.get());
                }
                pool.submit(() -> {
                    try {
                        HttpResponse<Void> r = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(),
                                HttpResponse.BodyHandlers.discarding());
                        (r.statusCode() == 200 ? ok : failed).incrementAndGet();
                        latencies.add((System.nanoTime() - due) / 1_000_000);
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                });
            }
        }
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        System.out.printf("  load done: %d ok, %d failed, p50 %d ms, p99 %d ms%n", ok.get(), failed.get(),
                sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2), sorted.isEmpty() ? 0 : sorted.get(sorted.size() * 99 / 100));
    }
}
