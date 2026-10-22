package dev.foodfleet.interview;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** JDK-only tests for the four coding-round answers, including the concurrent cases interviewers ask about. */
public final class CodingRoundsTest {

    private static int passed;

    public static void main(String[] args) throws Exception {
        breakerOpensAtTheFailureRate();
        breakerHalfOpensAndCloses();
        breakerHalfOpenFailureReopens();
        breakerIgnoresFailuresBelowMinimumCalls();
        bucketAllowsBurstThenRefills();
        bucketKeysAreIndependent();
        bucketIsExactUnderConcurrency();
        consumerAppliesDuplicatesOnce();
        consumerFailureRollsBackClaim();
        consumerConcurrentDuplicates();
        sagaCompletes();
        sagaCompensatesBeforePivot();
        sagaGoesStuckAfterPivotThenResumes();
        sagaResumesAfterCrash();
        System.out.println("all " + passed + " tests passed");
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("FAIL " + what);
        }
        passed++;
        System.out.println("PASS " + what);
    }

    // ---- circuit breaker
    static void breakerOpensAtTheFailureRate() {
        AtomicLong now = new AtomicLong();
        CircuitBreaker cb = new CircuitBreaker(10, 5, 0.5, Duration.ofSeconds(5), 2, now::get);
        for (int i = 0; i < 4; i++) {
            ok(cb);
        }
        for (int i = 0; i < 3; i++) {
            fail(cb);
        }
        check(cb.state() == CircuitBreaker.State.CLOSED, "breaker: 3 of 7 failed (43%) stays closed");
        fail(cb);
        check(cb.state() == CircuitBreaker.State.OPEN, "breaker: 4 of 8 failed (50%) opens");
        check(rejected(cb), "breaker: open rejects without calling");
    }

    static void breakerHalfOpensAndCloses() {
        AtomicLong now = new AtomicLong();
        CircuitBreaker cb = openBreaker(now);
        now.addAndGet(Duration.ofSeconds(5).toNanos());
        check(cb.state() == CircuitBreaker.State.HALF_OPEN, "breaker: half-open after the open duration");
        ok(cb);
        ok(cb);
        check(cb.state() == CircuitBreaker.State.CLOSED, "breaker: two successful trials close it");
    }

    static void breakerHalfOpenFailureReopens() {
        AtomicLong now = new AtomicLong();
        CircuitBreaker cb = openBreaker(now);
        now.addAndGet(Duration.ofSeconds(5).toNanos());
        fail(cb);
        check(cb.state() == CircuitBreaker.State.OPEN, "breaker: a failed trial reopens it");
    }

    static void breakerIgnoresFailuresBelowMinimumCalls() {
        CircuitBreaker cb = new CircuitBreaker(10, 5, 0.5, Duration.ofSeconds(5), 2, () -> 0);
        for (int i = 0; i < 4; i++) {
            fail(cb);
        }
        check(cb.state() == CircuitBreaker.State.CLOSED, "breaker: 4 failures below the 5-call minimum don't open it");
    }

    static CircuitBreaker openBreaker(AtomicLong now) {
        CircuitBreaker cb = new CircuitBreaker(10, 5, 0.5, Duration.ofSeconds(5), 2, now::get);
        for (int i = 0; i < 5; i++) {
            fail(cb);
        }
        return cb;
    }

    static void ok(CircuitBreaker cb) {
        cb.call(() -> "ok");
    }

    static void fail(CircuitBreaker cb) {
        try {
            cb.call(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the dependency's failure passes through
        }
    }

    static boolean rejected(CircuitBreaker cb) {
        AtomicInteger calls = new AtomicInteger();
        try {
            cb.call(calls::incrementAndGet);
            return false;
        } catch (CircuitBreaker.OpenException e) {
            return calls.get() == 0;
        }
    }

    // ---- token bucket
    static void bucketAllowsBurstThenRefills() {
        AtomicLong now = new AtomicLong();
        TokenBucket tb = new TokenBucket(5, 2, now::get);
        int allowed = 0;
        for (int i = 0; i < 8; i++) {
            allowed += tb.tryAcquire("tenant-a") ? 1 : 0;
        }
        check(allowed == 5, "bucket: a burst of 8 gets the capacity of 5");
        check(Math.abs(tb.secondsUntilToken("tenant-a") - 0.5) < 1e-9, "bucket: Retry-After is 0.5 s at 2 tokens a second");
        now.addAndGet(Duration.ofMillis(1500).toNanos());
        allowed = 0;
        for (int i = 0; i < 5; i++) {
            allowed += tb.tryAcquire("tenant-a") ? 1 : 0;
        }
        check(allowed == 3, "bucket: 1.5 s later, 3 more tokens");
    }

    static void bucketKeysAreIndependent() {
        TokenBucket tb = new TokenBucket(1, 1, () -> 0);
        tb.tryAcquire("noisy");
        check(!tb.tryAcquire("noisy") && tb.tryAcquire("quiet"), "bucket: one tenant's empty bucket doesn't limit another");
    }

    static void bucketIsExactUnderConcurrency() throws Exception {
        TokenBucket tb = new TokenBucket(1000, 1e-9, () -> 0);   // no refill: exactly 1,000 tokens
        AtomicInteger granted = new AtomicInteger();
        runConcurrently(64, 100, () -> {
            if (tb.tryAcquire("k")) {
                granted.incrementAndGet();
            }
        });
        check(granted.get() == 1000, "bucket: 6,400 concurrent requests for 1,000 tokens get exactly 1,000");
    }

    // ---- idempotent consumer
    static void consumerAppliesDuplicatesOnce() {
        IdempotentConsumer.Database db = new IdempotentConsumer.Database();
        IdempotentConsumer c = new IdempotentConsumer(db, m -> { });
        IdempotentConsumer.Message m = new IdempotentConsumer.Message("evt-1", "order-1", 24900);
        boolean first = c.handle(m);
        boolean second = c.handle(m);
        check(first && !second && db.balance("order-1") == 24900, "consumer: a redelivered message is applied once");
    }

    static void consumerFailureRollsBackClaim() {
        IdempotentConsumer.Database db = new IdempotentConsumer.Database();
        AtomicInteger attempt = new AtomicInteger();
        IdempotentConsumer c = new IdempotentConsumer(db, m -> {
            if (attempt.incrementAndGet() == 1) {
                throw new IllegalStateException("crash before commit");
            }
        });
        IdempotentConsumer.Message m = new IdempotentConsumer.Message("evt-2", "order-2", 14900);
        try {
            c.handle(m);
        } catch (IllegalStateException expected) {
            // the broker will redeliver
        }
        check(db.balance("order-2") == 0, "consumer: a failure part-way leaves no effect");
        check(c.handle(m) && db.balance("order-2") == 14900, "consumer: the redelivery is processed, not skipped");
    }

    static void consumerConcurrentDuplicates() throws Exception {
        IdempotentConsumer.Database db = new IdempotentConsumer.Database();
        IdempotentConsumer c = new IdempotentConsumer(db, m -> { });
        AtomicInteger applied = new AtomicInteger();
        runConcurrently(32, 50, () -> {
            for (int i = 0; i < 10; i++) {
                if (c.handle(new IdempotentConsumer.Message("evt-" + i, "order-3", 100))) {
                    applied.incrementAndGet();
                }
            }
        });
        check(applied.get() == 10 && db.balance("order-3") == 1000,
                "consumer: 16,000 concurrent deliveries of 10 messages apply each once");
    }

    // ---- saga
    static List<String> effects;

    static List<SagaCoordinator.Step> placeOrder(Runnable kitchen, Runnable capture) {
        effects = Collections.synchronizedList(new ArrayList<>());
        return List.of(
                SagaCoordinator.Step.compensatable("create order", id -> effects.add("order"), id -> effects.add("-order")),
                SagaCoordinator.Step.compensatable("reserve coupon", id -> effects.add("coupon"), id -> effects.add("-coupon")),
                SagaCoordinator.Step.compensatable("authorize payment", id -> effects.add("auth"), id -> effects.add("-auth")),
                SagaCoordinator.Step.pivot("kitchen accepts", id -> {
                    kitchen.run();
                    effects.add("kitchen");
                }),
                SagaCoordinator.Step.retriable("capture payment", id -> {
                    capture.run();
                    effects.add("capture");
                }),
                SagaCoordinator.Step.retriable("request rider", id -> effects.add("rider")));
    }

    static void sagaCompletes() {
        List<SagaCoordinator.Entry> log = new ArrayList<>();
        var saga = new SagaCoordinator(placeOrder(() -> { }, () -> { }), log, 3);
        check(saga.run("s1") == SagaCoordinator.Status.COMPLETED
                && effects.equals(List.of("order", "coupon", "auth", "kitchen", "capture", "rider")), "saga: completes in order");
        check(saga.run("s1") == SagaCoordinator.Status.COMPLETED && effects.size() == 6, "saga: running a finished saga again does nothing");
    }

    static void sagaCompensatesBeforePivot() {
        var steps = new ArrayList<>(placeOrder(() -> { }, () -> { }));
        steps.set(2, SagaCoordinator.Step.compensatable("authorize payment", id -> {
            throw new IllegalStateException("card declined");
        }, id -> effects.add("-auth")));
        check(new SagaCoordinator(steps, new ArrayList<>(), 3).run("s2") == SagaCoordinator.Status.COMPENSATED
                && effects.equals(List.of("order", "coupon", "-coupon", "-order")),
                "saga: a declined card releases the coupon and rejects the order, in reverse");
        var rejecting = new SagaCoordinator(placeOrder(() -> {
            throw new IllegalStateException("kitchen rejects");
        }, () -> { }), new ArrayList<>(), 3);
        check(rejecting.run("s2b") == SagaCoordinator.Status.COMPENSATED
                && effects.equals(List.of("order", "coupon", "auth", "-auth", "-coupon", "-order")),
                "saga: a kitchen rejection at the pivot voids the authorization and unwinds the rest");
        boolean refused = false;
        try {
            new SagaCoordinator(List.of(SagaCoordinator.Step.pivot("p", id -> { }),
                    SagaCoordinator.Step.compensatable("late", id -> { }, id -> { })), new ArrayList<>(), 3);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "saga: a compensatable step after the pivot is refused at definition time");
    }

    static void sagaGoesStuckAfterPivotThenResumes() {
        AtomicInteger captures = new AtomicInteger();
        boolean[] providerDown = {true};
        List<SagaCoordinator.Entry> log = new ArrayList<>();
        var saga = new SagaCoordinator(placeOrder(() -> { }, () -> {
            captures.incrementAndGet();
            if (providerDown[0]) {
                throw new IllegalStateException("provider down");
            }
        }), log, 3);
        var first = saga.run("s3");
        check(first == SagaCoordinator.Status.STUCK && captures.get() == 3 && !effects.contains("-auth"),
                "saga: capture failing after the pivot is retried 3 times, then STUCK, never compensated");
        providerDown[0] = false;
        check(saga.run("s3") == SagaCoordinator.Status.COMPLETED && effects.contains("rider"),
                "saga: the retry job resumes a STUCK saga to completion");
    }

    static void sagaResumesAfterCrash() {
        List<SagaCoordinator.Entry> log = new ArrayList<>();
        boolean[] crash = {true};
        var steps = placeOrder(() -> {
            if (crash[0]) {
                throw new Error("coordinator process killed");   // not a RuntimeException: escapes the saga
            }
        }, () -> { });
        try {
            new SagaCoordinator(steps, log, 3).run("s4");
        } catch (Error expected) {
            // the coordinator died mid-pivot; its log survives
        }
        crash[0] = false;
        var restarted = new SagaCoordinator(steps, log, 3);
        check(restarted.run("s4") == SagaCoordinator.Status.COMPLETED
                && effects.equals(List.of("order", "coupon", "auth", "kitchen", "capture", "rider")),
                "saga: a new coordinator resumes from the log without repeating finished steps");
    }

    static void runConcurrently(int threads, int perThread, Runnable task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        task.run();
                    }
                    return null;
                });
            }
            start.countDown();
        }
    }
}
