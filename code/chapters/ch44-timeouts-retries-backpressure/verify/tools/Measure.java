import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.FixedLimit;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import com.netflix.concurrency.limits.limiter.LifoBlockingLimiter;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import dev.foodfleet.ch44.degrade.Brownout;
import dev.foodfleet.ch44.hedge.HedgedReads;
import dev.foodfleet.ch44.limit.AdaptiveLimits;
import dev.foodfleet.ch44.limit.Priority;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Chapter 44's experiments. */
public final class Measure {

    public static void main(String[] args) throws Exception {
        run(args[0]);
        System.exit(0);   // Resilience4j's Hedge keeps a non-daemon scheduler alive
    }

    static void run(String mode) throws Exception {
        switch (mode) {
            case "tail" -> tail();
            case "limits" -> limits();
            case "priority" -> priority();
            case "brownout" -> brownout();
            case "streams" -> streams();
            default -> throw new IllegalArgumentException(mode);
        }
    }

    // ============================================================================================ tail: timeouts, retries, hedging

    interface Work {
        void run() throws Exception;
    }

    static void tail() throws Exception {
        System.out.println("each user has a 1 s budget; the dependency's service time: 94% fast (p50 ~15 ms), 5% 100-300 ms, 1% 1-2 s");
        System.out.println("users arrive for 15 s per row; abandoned calls keep running at the dependency");
        List<String> policies = List.of("one call, wait up to 1 s", "timeout 300 ms, retry within the budget",
                "timeout 100 ms, retry within the budget", "hedge after 100 ms (~p95)", "hedge after 50 ms (~p90)",
                "hedge after 50 ms, budget 5% of reads");
        for (boolean loaded : new boolean[] {false, true}) {
            System.out.println();
            System.out.println(loaded
                    ? "B) 620 users/s; the dependency has 32 workers: about 85% busy before any retry or hedge (two trials per policy)"
                    : "A) 500 users/s; the dependency has spare capacity (every call starts at once)");
            System.out.printf("%-46s %10s %8s %8s %9s %16s%n", "policy", "within 1 s", "p50", "p99", "p99.9", "calls per user");
            for (String policy : policies) {
                for (int trial = 1; trial <= (loaded ? 2 : 1); trial++) {
                    tailRow(policy, loaded, trial);
                }
            }
        }
    }

    static void tailRow(String policy, boolean loaded, int trial) throws Exception {
        ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
        long start = System.nanoTime() + 100_000_000L;
        Lab.Server server = new Lab.Server(start, 32, Lab::tail, t -> 1.0);
        AtomicLong calls = new AtomicLong();
        Work dependency = loaded ? server::handle : () -> Thread.sleep(Lab.tail());
        Supplier<String> call = () -> {
            calls.incrementAndGet();
            try {
                dependency.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return "menu";
        };
        HedgedReads hedge = policy.startsWith("hedge")
                ? new HedgedReads("menu", Duration.ofMillis(policy.contains("100") ? 100 : 50), Duration.ofSeconds(1),
                        policy.contains("budget") ? 0.05 : 1.0, vt)
                : null;
        long attemptTimeout = policy.contains("300") ? 300 : policy.contains("100 ms, retry") ? 100 : 1000;
        ConcurrentLinkedQueue<Long> latency = new ConcurrentLinkedQueue<>();
        AtomicLong ok = new AtomicLong();
        AtomicLong users = new AtomicLong();
        double rate = loaded ? 620 : 500;
        Lab.openLoop(start, t -> rate, 15, at -> {
            long t0 = System.nanoTime();
            long deadline = t0 + 1_000_000_000L;
            boolean success = false;
            if (hedge != null) {
                try {
                    hedge.read(call).join();
                    success = true;
                } catch (Exception e) {
                    success = false;
                }
            } else {
                while (!success) {
                    long remaining = (deadline - System.nanoTime()) / 1_000_000;
                    if (remaining <= 0) {
                        break;
                    }
                    CompletableFuture<String> f = CompletableFuture.supplyAsync(call, vt);
                    try {
                        f.get(Math.min(attemptTimeout, remaining), TimeUnit.MILLISECONDS);
                        success = true;
                    } catch (TimeoutException e) {
                        // abandoned: the work goes on at the dependency
                    }
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            users.incrementAndGet();
            if (success && ms <= 1000) {
                ok.incrementAndGet();
            }
            latency.add(success ? Math.min(ms, 1000) : 1000);
        });
        List<Long> l = new ArrayList<>(latency);
        System.out.printf("%-46s %10s %5d ms %5d ms %6d ms %16.2f%n", trial == 1 ? policy : "  (trial 2)", Lab.pct(ok.get(), users.get()),
                Lab.percentile(l, 50), Lab.percentile(l, 99), Lab.percentile(l, 99.9), (double) calls.get() / users.get());
        vt.shutdown();
        vt.awaitTermination(10, TimeUnit.SECONDS);
    }

    // ============================================================================================ adaptive concurrency limits

    record Phase(String name, double from, double to) {
    }

    static final List<Phase> PHASES = List.of(new Phase("0-15 s", 0, 15), new Phase("15-30 s (2.5x slower)", 15, 30), new Phase("30-45 s", 30, 45));

    static final class PhaseStats {
        final AtomicLong users = new AtomicLong();
        final AtomicLong good = new AtomicLong();
        final AtomicLong rejected = new AtomicLong();
        final ConcurrentLinkedQueue<Long> served = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> limits = new ConcurrentLinkedQueue<>();
    }

    static int phaseOf(double at) {
        for (int i = 0; i < PHASES.size(); i++) {
            if (at >= PHASES.get(i).from() && at < PHASES.get(i).to()) {
                return i;
            }
        }
        return PHASES.size() - 1;
    }

    static void limits() throws Exception {
        System.out.println("a service with 20 workers, 20 ms per request (1,000/s); users at 700/s for 45 s, each waits up to 500 ms");
        System.out.println("from 15 s to 30 s every request takes 2.5x longer (capacity 400/s), then it recovers");
        record V(String name, Supplier<Limiter<Void>> limiter) {
        }
        List<V> vs = List.of(
                new V("no limit (queue in front of the workers)", () -> null),
                new V("fixed 20 (the worker count)", () -> SimpleLimiter.newBuilder().limit(FixedLimit.of(20)).build()),
                new V("fixed 100", () -> SimpleLimiter.newBuilder().limit(FixedLimit.of(100)).build()),
                new V("Vegas (library defaults)", () -> SimpleLimiter.newBuilder().limit(VegasLimit.newDefault()).build()),
                new V("Gradient2 (library defaults: min 20)", () -> SimpleLimiter.newBuilder().limit(Gradient2Limit.newDefault()).build()),
                new V("Gradient2, min 5", () -> SimpleLimiter.newBuilder().limit(Gradient2Limit.newBuilder().minLimit(5).build()).build()),
                new V("AIMD, slow above 500 ms (the deadline)", () -> AdaptiveLimits.simple("a", Duration.ofMillis(500))),
                new V("AIMD, slow above 250 ms (FoodFleet)", () -> AdaptiveLimits.simple("b", Duration.ofMillis(250))));
        System.out.printf("%-42s %-22s %16s %12s %14s %12s%n", "limiter", "phase", "users in 500 ms", "rejected", "p99 served", "mean limit");
        for (V v : vs) {
            Limiter<Void> limiter = v.limiter().get();
            long start = System.nanoTime() + 100_000_000L;
            Lab.Server server = new Lab.Server(start, 20, () -> Lab.exponential(20), t -> t >= 15 && t < 30 ? 2.5 : 1.0);
            List<PhaseStats> stats = List.of(new PhaseStats(), new PhaseStats(), new PhaseStats());
            ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
            if (limiter instanceof AbstractLimiter<?> al) {
                sampler.scheduleAtFixedRate(() -> stats.get(phaseOf(server.now())).limits.add((long) al.getLimit()), 100, 100, TimeUnit.MILLISECONDS);
            }
            Lab.openLoop(start, t -> 700, 45, at -> {
                PhaseStats s = stats.get(phaseOf(at));
                s.users.incrementAndGet();
                long t0 = System.nanoTime();
                Optional<Limiter.Listener> permit = limiter == null ? Optional.empty() : limiter.acquire(null);
                if (limiter != null && permit.isEmpty()) {
                    s.rejected.incrementAndGet();
                    return;
                }
                try {
                    server.handle();
                } finally {
                    permit.ifPresent(Limiter.Listener::onSuccess);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                s.served.add(ms);
                if (ms <= 500) {
                    s.good.incrementAndGet();
                }
            });
            sampler.shutdownNow();
            for (int i = 0; i < PHASES.size(); i++) {
                PhaseStats s = stats.get(i);
                List<Long> lim = new ArrayList<>(s.limits);
                String meanLimit = lim.isEmpty() ? "-" : String.format("%.0f", lim.stream().mapToLong(Long::longValue).average().orElse(0));
                System.out.printf("%-42s %-22s %16s %12s %11d ms %12s%n", i == 0 ? v.name() : "", PHASES.get(i).name(),
                        Lab.pct(s.good.get(), s.users.get()), Lab.pct(s.rejected.get(), s.users.get()),
                        Lab.percentile(new ArrayList<>(s.served), 99), meanLimit);
            }
        }
    }

    // ============================================================================================ priority and queue order

    static void priority() throws Exception {
        System.out.println("a service with 20 workers, 20 ms per request (1,000/s); 2,000 requests/s for 15 s, half critical (checkout), half sheddable (browse)");
        System.out.println("each caller waits up to 500 ms");
        record V(String name, Supplier<Limiter<Priority>> limiter) {
        }
        List<V> vs = List.of(
                new V("no limit (FIFO queue)", () -> null),
                new V("adaptive limit, one for all", () -> AdaptiveLimits.simple("all", Duration.ofMillis(250))),
                new V("adaptive limit, partitioned 90% critical / 10% sheddable", () -> AdaptiveLimits.byPriority("orders", Duration.ofMillis(250))),
                new V("adaptive limit + FIFO wait up to 250 ms", () -> com.netflix.concurrency.limits.limiter.BlockingLimiter.wrap(
                        AdaptiveLimits.<Priority>simple("fifo", Duration.ofMillis(250)), Duration.ofMillis(250))),
                new V("adaptive limit + LIFO wait up to 250 ms (backlog 200)", () -> LifoBlockingLimiter.newBuilder(
                        AdaptiveLimits.<Priority>simple("lifo", Duration.ofMillis(250)))
                        .backlogSize(200).backlogTimeout(250, TimeUnit.MILLISECONDS).build()));
        System.out.printf("%-54s %18s %18s %16s %16s%n", "admission", "critical in 500ms", "sheddable in 500ms", "all in 500 ms", "p99 served");
        for (V v : vs) {
            Limiter<Priority> limiter = v.limiter().get();
            long start = System.nanoTime() + 100_000_000L;
            Lab.Server server = new Lab.Server(start, 20, () -> Lab.exponential(20), t -> 1.0);
            AtomicLong[] users = {new AtomicLong(), new AtomicLong()};
            AtomicLong[] good = {new AtomicLong(), new AtomicLong()};
            ConcurrentLinkedQueue<Long> served = new ConcurrentLinkedQueue<>();
            Lab.openLoop(start, t -> 2000, 15, at -> {
                Priority p = Math.random() < 0.5 ? Priority.CRITICAL : Priority.SHEDDABLE;
                users[p.ordinal()].incrementAndGet();
                long t0 = System.nanoTime();
                Optional<Limiter.Listener> permit = limiter == null ? Optional.empty() : limiter.acquire(p);
                if (limiter != null && permit.isEmpty()) {
                    return;
                }
                try {
                    server.handle();
                } finally {
                    permit.ifPresent(Limiter.Listener::onSuccess);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                served.add(ms);
                if (ms <= 500) {
                    good[p.ordinal()].incrementAndGet();
                }
            });
            long all = users[0].get() + users[1].get();
            System.out.printf("%-54s %18s %18s %16s %13d ms%n", v.name(), Lab.pct(good[0].get(), users[0].get()),
                    Lab.pct(good[1].get(), users[1].get()), Lab.pct(good[0].get() + good[1].get(), all),
                    Lab.percentile(new ArrayList<>(served), 99));
        }
    }

    // ============================================================================================ brownout

    static void brownout() throws Exception {
        System.out.println("menu page: 10 ms of its own work, plus recommendations if it can get them within 150 ms");
        System.out.println("recommendation-service: 8 workers, 20 ms per request (400/s)");
        System.out.println("pages: 250/s, 600/s from 10 s to 30 s (a lunch spike), 250/s again until 45 s");
        record P(String name, double from, double to) {
        }
        List<P> phases = List.of(new P("before (0-10 s)", 0, 10), new P("spike (10-30 s)", 10, 30), new P("after (30-45 s)", 30, 45));
        System.out.printf("%-48s %-18s %18s %14s %20s%n", "strategy", "phase", "pages with recs", "page p99", "recs calls per page");
        for (String strategy : List.of("timeout only", "recommendation-service limits itself (AIMD)", "brownout dimmer + timeout")) {
            ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
            long start = System.nanoTime() + 100_000_000L;
            Lab.Server recs = new Lab.Server(start, 8, () -> Lab.exponential(20), t -> 1.0);
            Limiter<Void> limiter = strategy.startsWith("recommendation") ? AdaptiveLimits.simple("recs", Duration.ofMillis(75)) : null;
            Brownout dimmer = strategy.startsWith("brownout") ? new Brownout(0.1, 0.05) : null;
            ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
            if (dimmer != null) {
                ticker.scheduleAtFixedRate(dimmer::adjust, 1, 1, TimeUnit.SECONDS);
            }
            int n = phases.size();
            AtomicLong[] pages = new AtomicLong[n];
            AtomicLong[] withRecs = new AtomicLong[n];
            AtomicLong[] recsCalls = new AtomicLong[n];
            List<ConcurrentLinkedQueue<Long>> lat = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                pages[i] = new AtomicLong();
                withRecs[i] = new AtomicLong();
                recsCalls[i] = new AtomicLong();
                lat.add(new ConcurrentLinkedQueue<>());
            }
            Lab.openLoop(start, t -> t >= 10 && t < 30 ? 600 : 250, 45, at -> {
                int ph = at < 10 ? 0 : at < 30 ? 1 : 2;
                long t0 = System.nanoTime();
                Thread.sleep(10);
                boolean got = false;
                if (dimmer == null || dimmer.shouldTry()) {
                    recsCalls[ph].incrementAndGet();
                    CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> {
                        Optional<Limiter.Listener> permit = limiter == null ? Optional.empty() : limiter.acquire(null);
                        if (limiter != null && permit.isEmpty()) {
                            return false;
                        }
                        try {
                            recs.handle();
                            return true;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        } finally {
                            permit.ifPresent(Limiter.Listener::onSuccess);
                        }
                    }, vt);
                    try {
                        got = f.get(150, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        got = false;
                    }
                    if (dimmer != null) {
                        if (got) {
                            dimmer.succeeded();
                        } else {
                            dimmer.failedOrSlow();
                        }
                    }
                }
                pages[ph].incrementAndGet();
                if (got) {
                    withRecs[ph].incrementAndGet();
                }
                lat.get(ph).add((System.nanoTime() - t0) / 1_000_000);
            });
            ticker.shutdownNow();
            for (int i = 0; i < n; i++) {
                System.out.printf("%-48s %-18s %18s %11d ms %20.2f%n", i == 0 ? strategy : "", phases.get(i).name(),
                        Lab.pct(withRecs[i].get(), pages[i].get()), Lab.percentile(new ArrayList<>(lat.get(i)), 99),
                        (double) recsCalls[i].get() / pages[i].get());
            }
            vt.shutdownNow();
            Thread.sleep(3000);   // let the abandoned recommendation work drain before the next strategy
        }
    }

    // ============================================================================================ streams

    static void streams() throws Exception {
        System.out.println("a producer emits 2,000 events/s for 10 s; the consumer handles 1,000/s (1 ms each)");
        System.out.printf("%-46s %10s %10s %16s %20s %18s%n", "strategy", "accepted", "dropped", "most waiting", "p99 event age", "producer rate");
        for (String strategy : List.of("unbounded queue", "Flow: submit() blocks, buffer 256", "Flow: offer() drops when full, buffer 256",
                "Flow: offer() waits up to 5 ms, buffer 256")) {
            AtomicLong dropped = new AtomicLong();
            AtomicLong accepted = new AtomicLong();
            AtomicInteger waiting = new AtomicInteger();
            AtomicInteger maxWaiting = new AtomicInteger();
            ConcurrentLinkedQueue<Long> ages = new ConcurrentLinkedQueue<>();
            java.util.function.Consumer<Long> consume = born -> {
                waiting.decrementAndGet();
                long end = System.nanoTime() + 1_000_000L;
                while (System.nanoTime() < end) {
                    Thread.onSpinWait();
                }
                ages.add((System.nanoTime() - born) / 1_000_000);
            };
            long t0 = System.nanoTime();
            long producedFor;
            if (strategy.startsWith("unbounded")) {
                ThreadPoolExecutor consumer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
                producedFor = produce(t0, born -> {
                    maxWaiting.accumulateAndGet(waiting.incrementAndGet(), Math::max);
                    accepted.incrementAndGet();
                    consumer.execute(() -> consume.accept(born));
                });
                consumer.shutdown();
                consumer.awaitTermination(60, TimeUnit.SECONDS);
            } else {
                ExecutorService exec = Executors.newSingleThreadExecutor();
                SubmissionPublisher<Long> pub = new SubmissionPublisher<>(exec, 256);
                java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                pub.subscribe(new Flow.Subscriber<>() {
                    Flow.Subscription sub;

                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        sub = s;
                        s.request(1);
                    }

                    @Override
                    public void onNext(Long born) {
                        consume.accept(born);
                        sub.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        done.countDown();
                    }

                    @Override
                    public void onComplete() {
                        done.countDown();
                    }
                });
                producedFor = produce(t0, born -> {
                    maxWaiting.accumulateAndGet(waiting.incrementAndGet(), Math::max);
                    if (strategy.contains("submit")) {
                        pub.submit(born);
                        accepted.incrementAndGet();
                    } else {
                        long timeout = strategy.contains("waits") ? 5 : 0;
                        int lag = pub.offer(born, timeout, TimeUnit.MILLISECONDS, (s, item) -> false);
                        if (lag < 0) {
                            dropped.incrementAndGet();
                            waiting.decrementAndGet();
                        } else {
                            accepted.incrementAndGet();
                        }
                    }
                });
                pub.close();
                done.await(60, TimeUnit.SECONDS);
                exec.shutdown();
            }
            double rate = (accepted.get() + dropped.get()) / (producedFor / 1e9);
            System.out.printf("%-46s %10d %10d %16d %17d ms %15.0f/s%n", strategy, accepted.get(), dropped.get(), maxWaiting.get(),
                    Lab.percentile(new ArrayList<>(ages), 99), rate);
        }
    }

    /** Emits an event every 0.5 ms for 10 s, or as fast as {@code emit} returns if that is slower. */
    static long produce(long t0, java.util.function.LongConsumer emit) throws InterruptedException {
        long end = t0 + 10_000_000_000L;
        long next = t0;
        while (System.nanoTime() < end) {
            long wait = next - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
            emit.accept(System.nanoTime());
            next += 500_000L;
            if (next < System.nanoTime() - 1_000_000L) {
                next = System.nanoTime();   // fell behind (blocked): don't burst to catch up
            }
        }
        return System.nanoTime() - t0;
    }
}
