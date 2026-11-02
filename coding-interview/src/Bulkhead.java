import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Problem: isolate a slow dependency so it can use at most N concurrent calls; excess calls fail fast instead of queuing. */
public class Bulkhead {

    static final class BulkheadFullException extends RuntimeException { BulkheadFullException(String m) { super(m); } }

    static final class SemaphoreBulkhead {
        private final String name;
        private final Semaphore permits;
        private final long maxWaitMillis;
        final AtomicInteger rejected = new AtomicInteger();

        SemaphoreBulkhead(String name, int maxConcurrent, long maxWaitMillis) {
            this.name = name; this.permits = new Semaphore(maxConcurrent, true); this.maxWaitMillis = maxWaitMillis;
        }

        <T> T call(Callable<T> op) throws Exception {
            if (!permits.tryAcquire(maxWaitMillis, TimeUnit.MILLISECONDS)) {
                rejected.incrementAndGet();
                throw new BulkheadFullException(name + " bulkhead full");
            }
            try {
                return op.call();
            } finally {
                permits.release();            // ALWAYS release, even when the call throws
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SemaphoreBulkhead inventory = new SemaphoreBulkhead("inventory", 3, 0);
        AtomicInteger inFlight = new AtomicInteger(), maxSeen = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                int id = i;
                results.add(pool.submit(() -> {
                    try {
                        return inventory.call(() -> {
                            maxSeen.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                            release.await();       // simulate a hung dependency
                            inFlight.decrementAndGet();
                            return "ok-" + id;
                        });
                    } catch (BulkheadFullException e) {
                        return "rejected";
                    }
                }));
            }
            while (inventory.rejected.get() < 7) Thread.sleep(5);   // 3 inside, 7 rejected immediately
            release.countDown();
            long ok = 0;
            for (Future<String> f : results) if (f.get().startsWith("ok")) ok++;
            System.out.println("10 concurrent calls to a hung dependency, bulkhead=3: succeeded=" + ok + " rejected=" + inventory.rejected.get());
            System.out.println("max concurrent calls observed inside = " + maxSeen.get());
            check(ok == 3 && inventory.rejected.get() == 7 && maxSeen.get() == 3, "never more than 3 inside");
        }

        // Permits are returned even when the operation throws
        SemaphoreBulkhead b = new SemaphoreBulkhead("payments", 1, 0);
        for (int i = 0; i < 3; i++) { try { b.call(() -> { throw new IllegalStateException("boom"); }); } catch (IllegalStateException ignored) { } }
        System.out.println("after 3 failing calls the permit is free again: " + b.call(() -> "ok"));
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
