import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongSupplier;

/**
 * The chapter's test bench: a server with a fixed number of workers behind a FIFO queue (a fair semaphore, the way
 * a thread pool's queue behaves), whose service time can change during a run, and open-loop users arriving as a
 * Poisson process on virtual threads. The control code under test is real: Netflix concurrency-limits 0.5.4,
 * Resilience4j 2.4.0's Hedge, the JDK's Flow publisher, and the chapter's own classes.
 */
final class Lab {

    private Lab() {
    }

    /** A long-tailed service time: mostly fast, sometimes slow, rarely a stall (a GC pause, a cold cache, a lock). */
    static long tail() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double u = r.nextDouble();
        if (u < 0.94) {
            return 5 + (long) (-Math.log(1 - r.nextDouble()) * 15);
        }
        if (u < 0.99) {
            return 100 + r.nextLong(200);
        }
        return 1000 + r.nextLong(1000);
    }

    static long exponential(double meanMillis) {
        return Math.max(1, Math.round(-Math.log(1 - ThreadLocalRandom.current().nextDouble()) * meanMillis));
    }

    static final class Server {
        final long start;
        final Semaphore workers;
        final LongSupplier serviceMillis;
        final DoubleUnaryOperator slowdownAt;   // seconds since start -> multiplier on service time
        final AtomicInteger waiting = new AtomicInteger();
        final AtomicInteger maxWaiting = new AtomicInteger();
        final AtomicLong handled = new AtomicLong();

        Server(long start, int workers, LongSupplier serviceMillis, DoubleUnaryOperator slowdownAt) {
            this.start = start;
            this.workers = new Semaphore(workers, true);
            this.serviceMillis = serviceMillis;
            this.slowdownAt = slowdownAt;
        }

        double now() {
            return (System.nanoTime() - start) / 1e9;
        }

        /** Waits for a worker (FIFO), then works. Abandoned callers don't cancel the work. */
        void handle() throws InterruptedException {
            maxWaiting.accumulateAndGet(waiting.incrementAndGet(), Math::max);
            workers.acquire();
            waiting.decrementAndGet();
            try {
                Thread.sleep(Math.max(1, Math.round(serviceMillis.getAsLong() * slowdownAt.applyAsDouble(now()))));
                handled.incrementAndGet();
            } finally {
                workers.release();
            }
        }
    }

    interface User {
        void arrive(double atSecond) throws Exception;
    }

    interface Rate {
        double at(double second);
    }

    static void openLoop(long start, Rate rate, double seconds, User user) throws InterruptedException {
        ExecutorService users = Executors.newVirtualThreadPerTaskExecutor();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double t = 0;
        while (true) {
            t += -Math.log(1 - r.nextDouble()) / Math.max(0.001, rate.at(t));
            if (t >= seconds) {
                break;
            }
            long wait = start + (long) (t * 1e9) - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
            double at = t;
            users.submit(() -> {
                user.arrive(at);
                return null;
            });
        }
        users.shutdown();
        users.awaitTermination(120, TimeUnit.SECONDS);
    }

    static void parallel(List<Runnable> tasks) throws InterruptedException {
        List<Thread> ts = new ArrayList<>();
        for (Runnable task : tasks) {
            ts.add(Thread.ofPlatform().start(task));
        }
        for (Thread t : ts) {
            t.join();
        }
    }

    interface Body {
        void run() throws Exception;
    }

    static Runnable unchecked(Body b) {
        return () -> {
            try {
                b.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return -1;
        }
        List<Long> s = new ArrayList<>(values);
        Collections.sort(s);
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }

    static String pct(long part, long whole) {
        return whole == 0 ? "-" : String.format("%.1f%%", 100.0 * part / whole);
    }
}
