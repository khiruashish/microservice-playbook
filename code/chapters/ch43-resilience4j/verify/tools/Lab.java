import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleFunction;

/**
 * Shared pieces for the chapter's experiments: a dependency whose behaviour follows a script over time, an open-loop
 * load generator, and percentile helpers. The resilience code under test is the real library (Resilience4j 2.4.0,
 * Spring Framework 7.0.9); only the dependency is simulated, as a function that sleeps and throws.
 */
final class Lab {

    private Lab() {
    }

    /** What the dependency does at a given second of the run. */
    record Behaviour(double errorRate, long latencyMillis) {
    }

    static class Dependency {
        final long start;
        final DoubleFunction<Behaviour> script;
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicLong calls = new AtomicLong();

        Dependency(long start, DoubleFunction<Behaviour> script) {
            this.start = start;
            this.script = script;
        }

        double now() {
            return (System.nanoTime() - start) / 1e9;
        }

        String call() throws IOException, InterruptedException {
            calls.incrementAndGet();
            int n = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(n, Math::max);
            try {
                Behaviour b = script.apply(now());
                if (b.latencyMillis() > 0) {
                    Thread.sleep(b.latencyMillis());
                }
                if (ThreadLocalRandom.current().nextDouble() < b.errorRate()) {
                    throw new IOException("dependency failed");
                }
                return "menu";
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    interface User {
        void arrive(double atSecond);
    }

    /**
     * Users arrive as a Poisson process at {@code rate} per second for {@code seconds}, each on its own virtual
     * thread, whatever the system is doing. Returns when every user has finished (or after a grace period).
     */
    static void openLoop(long start, double rate, double seconds, User user) throws InterruptedException {
        ExecutorService users = Executors.newVirtualThreadPerTaskExecutor();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double t = 0;
        while (true) {
            t += -Math.log(1 - r.nextDouble()) / rate;
            if (t >= seconds) {
                break;
            }
            long due = start + (long) (t * 1e9);
            long wait = due - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
            double at = t;
            users.submit(() -> user.arrive(at));
        }
        users.shutdown();
        users.awaitTermination(60, TimeUnit.SECONDS);
    }

    /** Runs the tasks on platform threads and waits for all of them. */
    static void parallel(List<Runnable> tasks) throws InterruptedException {
        List<Thread> ts = new ArrayList<>();
        for (Runnable task : tasks) {
            ts.add(Thread.ofPlatform().start(task));
        }
        for (Thread t : ts) {
            t.join();
        }
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

    static String secs(double s) {
        return s < 0 ? "never" : String.format("%.1f s", s);
    }

    /** Wraps checked exceptions for lambdas that must be Runnables. */
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
}
