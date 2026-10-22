import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch42.resilience.Deadline;
import dev.foodfleet.ch42.resilience.QueueAgeShedder;
import dev.foodfleet.ch42.resilience.RetryPolicy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.DoubleSupplier;

/**
 * A small, real distributed system on loopback for the chapter's experiments: HTTP services with a fixed number of
 * workers and a FIFO queue in front of them (like a servlet container's thread pool), and open-loop load generators
 * whose requests time out and retry. Nothing is simulated except the work itself, which is a sleep.
 */
final class Lab {

    private Lab() {
    }

    static final HttpClient HTTP = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(java.net.ProxySelector.of(null))
            .build();

    // ------------------------------------------------------------------------------------------------ time series

    /** Counters per time bucket since {@code start}. */
    static final class Series {
        final long start;
        final long bucketMillis;
        final int buckets;
        private final ConcurrentHashMap<String, AtomicLongArray> counters = new ConcurrentHashMap<>();

        Series(long start, long bucketMillis, int buckets) {
            this.start = start;
            this.bucketMillis = bucketMillis;
            this.buckets = buckets;
        }

        void add(String name, long atMillis) {
            int b = (int) ((atMillis - start) / bucketMillis);
            if (b >= 0 && b < buckets) {
                counters.computeIfAbsent(name, k -> new AtomicLongArray(buckets)).incrementAndGet(b);
            }
        }

        long get(String name, int bucket) {
            AtomicLongArray a = counters.get(name);
            return a == null ? 0 : a.get(bucket);
        }

        long total(String name) {
            long t = 0;
            for (int b = 0; b < buckets; b++) {
                t += get(name, b);
            }
            return t;
        }
    }

    // ------------------------------------------------------------------------------------------------ service

    /** What a service does with a request once a worker picks it up. */
    interface Work {
        /** Returns the HTTP status to answer with. */
        int handle(Deadline deadline) throws Exception;
    }

    /**
     * An HTTP service: {@code workers} threads behind an unbounded FIFO queue. Optionally sheds requests that waited
     * too long or whose deadline passed (when the worker picks them up). Records per-bucket counts in {@code series}
     * under its name: arrived, served, shed, late (finished after the caller's deadline: wasted work).
     */
    static final class Service implements AutoCloseable {
        final String name;
        final HttpServer server;
        final ThreadPoolExecutor pool;
        final Series series;
        volatile QueueAgeShedder shedder;
        volatile Work work;
        final AtomicLong served = new AtomicLong();
        final AtomicLong busyNanos = new AtomicLong();   // time workers spent handling requests
        private static final ThreadLocal<Long> ENQUEUED = new ThreadLocal<>();

        Service(String name, int workers, Series series, Work work) throws IOException {
            this.name = name;
            this.series = series;
            this.work = work;
            this.pool = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
                @Override
                public void execute(Runnable r) {
                    long t = System.currentTimeMillis();
                    super.execute(() -> {
                        ENQUEUED.set(t);
                        r.run();
                    });
                }
            };
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1024);
            server.setExecutor(pool);
            server.createContext("/work", this::handle);
            server.createContext("/health", ex -> respond(ex, 200, "UP"));
            server.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/work");
        }

        URI health() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health");
        }

        int queued() {
            return pool.getQueue().size();
        }

        private void handle(HttpExchange ex) throws IOException {
            long began = System.nanoTime();
            try {
                handleTimed(ex);
            } finally {
                busyNanos.addAndGet(System.nanoTime() - began);
            }
        }

        private void handleTimed(HttpExchange ex) throws IOException {
            long now = System.currentTimeMillis();
            series.add(name + ".arrived", now);
            Deadline deadline = Deadline.parse(ex.getRequestHeaders().getFirst(Deadline.HEADER)).orElse(null);
            QueueAgeShedder s = shedder;
            Long enqueued = ENQUEUED.get();
            if (s != null && s.admit(enqueued == null ? now : enqueued, deadline, now) != QueueAgeShedder.Verdict.SERVE) {
                series.add(name + ".shed", now);
                respond(ex, 503, "shed");
                return;
            }
            int status;
            try {
                status = work.handle(deadline);
            } catch (Exception e) {
                status = 500;
            }
            long done = System.currentTimeMillis();
            if (status == 200) {
                served.incrementAndGet();
                series.add(name + ".served", done);
                if (deadline != null && deadline.expired(done)) {
                    series.add(name + ".late", done);
                }
            } else {
                series.add(name + ".failed", done);
            }
            respond(ex, status, status == 200 ? "ok" : "error");
        }

        static void respond(HttpExchange ex, int status, String body) throws IOException {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            try {
                ex.sendResponseHeaders(status, b.length);
                ex.getResponseBody().write(b);
            } catch (IOException ignored) {
                // the caller timed out and closed the connection: the work was wasted
            } finally {
                ex.close();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            pool.shutdownNow();
        }
    }

    /** Work that sleeps for an exponentially distributed time with mean {@code mean() * slowdown}. */
    static Work sleeping(Duration mean, DoubleSupplier slowdown) {
        return deadline -> {
            double ms = -Math.log(1 - ThreadLocalRandom.current().nextDouble()) * mean.toNanos() / 1e6 * slowdown.getAsDouble();
            Thread.sleep(Duration.ofNanos((long) (ms * 1e6)));
            return 200;
        };
    }

    // ------------------------------------------------------------------------------------------------ calls

    record Outcome(boolean ok, int attempts, long latencyMillis) {
    }

    /**
     * One logical call with retries: each attempt has {@code attemptTimeout}; the whole call stops at
     * {@code deadline}. A retry is sent only if the policy allows it and there is time left.
     */
    static Outcome call(URI target, RetryPolicy policy, Duration attemptTimeout, Deadline deadline, Series series,
            String name) {
        long start = System.currentTimeMillis();
        int attempts = 0;
        while (true) {
            long now = System.currentTimeMillis();
            if (deadline.expired(now)) {
                return new Outcome(false, attempts, now - start);
            }
            attempts++;
            series.add(name + ".attempts", now);
            Duration timeout = attemptTimeout.compareTo(deadline.remaining(now)) < 0 ? attemptTimeout : deadline.remaining(now);
            boolean ok;
            try {
                HttpResponse<Void> r = HTTP.send(HttpRequest.newBuilder(target).timeout(timeout.isZero() ? Duration.ofMillis(1) : timeout)
                        .header(Deadline.HEADER, deadline.header()).GET().build(), HttpResponse.BodyHandlers.discarding());
                ok = r.statusCode() == 200;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Outcome(false, attempts, System.currentTimeMillis() - start);
            } catch (IOException e) {
                ok = false;   // timeout, refused, reset
            }
            if (ok) {
                return new Outcome(true, attempts, System.currentTimeMillis() - start);
            }
            Optional<Duration> wait = policy.next(attempts, deadline.remaining(System.currentTimeMillis()), ThreadLocalRandom.current());
            if (wait.isEmpty()) {
                return new Outcome(false, attempts, System.currentTimeMillis() - start);
            }
            try {
                Thread.sleep(wait.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Outcome(false, attempts, System.currentTimeMillis() - start);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ load

    /**
     * Open-loop load: user requests arrive as a Poisson process at {@code rate.at(second)} per second for
     * {@code seconds}, each on its own virtual thread, regardless of how the system is doing (users keep coming).
     */
    interface Rate {
        double at(double secondsSinceStart);
    }

    interface Request {
        Outcome run(long startMillis);
    }

    static void openLoop(Rate rate, int seconds, Series series, String name, Request request) throws InterruptedException {
        ExecutorService users = Executors.newVirtualThreadPerTaskExecutor();
        long start = series.start;
        long end = start + seconds * 1000L;
        double t = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (t < end) {
            double lambda = Math.max(0.001, rate.at((t - start) / 1000.0));
            t += -Math.log(1 - r.nextDouble()) * 1000.0 / lambda;
            long wait = (long) t - System.currentTimeMillis();
            if (wait > 0) {
                Thread.sleep(wait);
            }
            if (t >= end) {
                break;
            }
            long at = (long) t;
            series.add(name + ".offered", at);
            users.submit(() -> {
                Outcome o = request.run(at);
                series.add(o.ok() ? name + ".ok" : name + ".failed", at);   // counted by when the user arrived
                return o;
            });
        }
        users.shutdown();
        users.awaitTermination(30, TimeUnit.SECONDS);
    }
}
