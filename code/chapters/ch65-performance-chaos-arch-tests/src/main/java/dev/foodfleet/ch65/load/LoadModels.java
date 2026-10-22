package dev.foodfleet.ch65.load;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Two ways a load generator can produce traffic.
 * <ul>
 *   <li><b>Closed</b>: a fixed number of virtual users, each sending its next request when the previous one returns
 *       (k6's {@code constant-vus}, Gatling's {@code constantConcurrentUsers}, a JMeter thread group). When the
 *       service slows down, the users send less, and the slow period produces few slow samples.</li>
 *   <li><b>Open</b>: requests at a fixed rate whatever happens to earlier ones (k6's {@code constant-arrival-rate},
 *       Gatling's {@code constantUsersPerSec}), latency measured from when each request was due. This is how
 *       independent users arrive.</li>
 * </ul>
 */
public final class LoadModels {

    public record Result(Latencies latencies, Latencies corrected, long requests, long errors, double seconds) {
        public double rate() {
            return requests / seconds;
        }
    }

    private LoadModels() {
    }

    static HttpClient client() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).executor(Executors.newVirtualThreadPerTaskExecutor()).build();
    }

    /** {@code users} loops, back to back. {@code expectedIntervalMillis} feeds the corrected histogram. */
    public static Result closed(String url, int users, double seconds, double expectedIntervalMillis) throws InterruptedException {
        Latencies raw = new Latencies();
        Latencies corrected = new Latencies();
        AtomicLong errors = new AtomicLong();
        long end = System.nanoTime() + (long) (seconds * 1e9);
        CountDownLatch done = new CountDownLatch(users);
        HttpClient http = client();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
        long t0 = System.nanoTime();
        for (int u = 0; u < users; u++) {
            Thread.ofPlatform().start(() -> {
                while (System.nanoTime() < end) {
                    long t = System.nanoTime();
                    try {
                        http.send(req, HttpResponse.BodyHandlers.discarding());
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                    long micros = (System.nanoTime() - t) / 1000;
                    raw.record(micros);
                    corrected.recordCorrected(micros, (long) (expectedIntervalMillis * 1000));
                }
                done.countDown();
            });
        }
        done.await();
        return new Result(raw, corrected, raw.count(), errors.get(), (System.nanoTime() - t0) / 1e9);
    }

    /** {@code rate} requests a second, each timed from the moment it was due. */
    public static Result open(String url, double rate, double seconds) throws InterruptedException {
        Latencies lat = new Latencies();
        AtomicLong errors = new AtomicLong();
        HttpClient http = client();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
        long total = (long) (rate * seconds);
        CountDownLatch done = new CountDownLatch((int) total);
        long t0 = System.nanoTime();
        for (long i = 0; i < total; i++) {
            long due = t0 + (long) (i * 1e9 / rate);
            long wait = due - System.nanoTime();
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            Thread.ofVirtual().start(() -> {
                try {
                    http.send(req, HttpResponse.BodyHandlers.discarding());
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
                lat.record((System.nanoTime() - due) / 1000);
                done.countDown();
            });
        }
        done.await();
        return new Result(lat, lat, total, errors.get(), (System.nanoTime() - t0) / 1e9);
    }
}
