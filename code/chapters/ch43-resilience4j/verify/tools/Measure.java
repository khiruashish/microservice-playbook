import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch43.policy.ResiliencePolicies;
import dev.foodfleet.ch43.policy.Resilient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowSynchronizationStrategy;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.ConcurrencyThrottleSupport;

/** Chapter 43's experiments against Resilience4j 2.4.0 and Spring Framework 7.0.9, built from source. */
public final class Measure {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "breaker" -> breaker();
            case "slow" -> slow();
            case "timelimiter" -> timeLimiter();
            case "order" -> order();
            case "ratelimiter" -> rateLimiter();
            case "schedules" -> schedules();
            case "overhead" -> overhead();
            case "overload" -> overload();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    // ============================================================================================ breaker windows

    record Transition(double at, CircuitBreaker.State to) {
    }

    static List<Transition> record(CircuitBreaker cb, long start) {
        List<Transition> ts = Collections.synchronizedList(new ArrayList<>());
        cb.getEventPublisher().onStateTransition(e ->
                ts.add(new Transition((System.nanoTime() - start) / 1e9, e.getStateTransition().getToState())));
        return ts;
    }

    static double firstAfter(List<Transition> ts, double after, CircuitBreaker.State to) {
        synchronized (ts) {
            for (Transition t : ts) {
                if (t.at() >= after && t.to() == to) {
                    return t.at() - after;
                }
            }
        }
        return -1;
    }

    static long countBefore(List<Transition> ts, double before, CircuitBreaker.State to) {
        synchronized (ts) {
            return ts.stream().filter(t -> t.at() < before && t.to() == to).count();
        }
    }

    static void breaker() throws Exception {
        System.out.println("each breaker guards its own dependency; users arrive at 100/s or 5/s for 75 s");
        System.out.println("dependency: 1% errors; 20-25 s a blip of 40% errors; 30-45 s down (every call fails); 45 s on healthy again");
        record Cfg(String name, CircuitBreakerConfig config) {
        }
        List<Cfg> cfgs = List.of(
                new Cfg("library defaults (last 100 calls, open 60 s)", CircuitBreakerConfig.ofDefaults()),
                new Cfg("last 10 calls, open 5 s", CircuitBreakerConfig.custom()
                        .slidingWindow(10, 10, SlidingWindowType.COUNT_BASED)
                        .waitDurationInOpenState(Duration.ofSeconds(5)).permittedNumberOfCallsInHalfOpenState(5).build()),
                new Cfg("last 10 s, min 20 calls, open 5 s doubling", CircuitBreakerConfig.custom()
                        .slidingWindow(10, 20, SlidingWindowType.TIME_BASED).permittedNumberOfCallsInHalfOpenState(5)
                        .waitIntervalFunctionInOpenState(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(5), 2, Duration.ofSeconds(60))).build()),
                new Cfg("FoodFleet: last 5 s, min 20 calls, open 5 s", ResiliencePolicies.circuitBreaker()));
        double[] rates = {100, 5};
        long start = System.nanoTime() + 200_000_000L;
        record Run(String cfg, double rate, CircuitBreaker cb, Lab.Dependency dep, List<Transition> ts,
                   AtomicLong deadCalls, AtomicLong refusedAfter, AtomicLong usersAfter, AtomicLong failedBlip) {
        }
        List<Run> runs = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        for (Cfg c : cfgs) {
            for (double rate : rates) {
                CircuitBreaker cb = CircuitBreaker.of(c.name() + rate, c.config());
                Lab.Dependency dep = new Lab.Dependency(start, t ->
                        new Lab.Behaviour(t >= 30 && t < 45 ? 1.0 : t >= 20 && t < 25 ? 0.40 : 0.01, 2));
                Run run = new Run(c.name(), rate, cb, dep, record(cb, start), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong());
                runs.add(run);
                tasks.add(Lab.unchecked(() -> Lab.openLoop(start, rate, 75, at -> {
                    try {
                        cb.executeCheckedSupplier(() -> {
                            if (at >= 30 && at < 45) {
                                run.deadCalls().incrementAndGet();
                            }
                            return dep.call();
                        });
                    } catch (CallNotPermittedException e) {
                        if (at >= 45) {
                            run.refusedAfter().incrementAndGet();
                        }
                        if (at >= 25 && at < 30) {
                            run.failedBlip().incrementAndGet();
                        }
                    } catch (Throwable ignored) {
                        // the dependency's own failure
                    }
                    if (at >= 45) {
                        run.usersAfter().incrementAndGet();
                    }
                })));
            }
        }
        Lab.parallel(tasks);
        System.out.printf("%-46s %6s %12s %14s %18s %16s %22s%n", "breaker", "rate", "opened <30 s", "opened after",
                "calls to dead dep", "closed after", "users refused after");
        System.out.printf("%-46s %6s %12s %14s %18s %16s %22s%n", "", "", "(1% or blip)", "outage start", "(of all in outage)", "recovery", "recovery");
        for (Run r : runs) {
            long total = Math.round(r.rate() * 15);
            System.out.printf("%-46s %5.0f/s %12d %14s %18s %16s %22s%n", r.cfg(), r.rate(),
                    countBefore(r.ts(), 30, CircuitBreaker.State.OPEN),
                    Lab.secs(firstAfter(r.ts(), 30, CircuitBreaker.State.OPEN)),
                    r.deadCalls().get() + " (~" + total + ")",
                    Lab.secs(firstAfter(r.ts(), 45, CircuitBreaker.State.CLOSED)),
                    r.refusedAfter().get() + " of " + r.usersAfter().get());
        }
    }

    // ============================================================================================ slow dependency

    static void slow() throws Exception {
        System.out.println("users at 50/s for 45 s; the dependency answers in 20 ms, but from 10 s to 30 s in 3 s (no errors)");
        ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
        record Variant(String name, CircuitBreaker cb, Lab.Dependency dep, CheckedSupplier<String> call) {
        }
        long start = System.nanoTime() + 200_000_000L;
        List<Variant> vs = new ArrayList<>();
        java.util.function.Function<Long, Lab.Dependency> newDep = s -> new Lab.Dependency(s, t -> new Lab.Behaviour(0, t >= 10 && t < 30 ? 3000 : 20));
        {
            CircuitBreaker cb = CircuitBreaker.of("a", CircuitBreakerConfig.ofDefaults());
            Lab.Dependency d = newDep.apply(start);
            vs.add(new Variant("library defaults, no timeout", cb, d, () -> cb.executeCheckedSupplier(d::call)));
        }
        {
            CircuitBreaker cb = CircuitBreaker.of("b", ResiliencePolicies.circuitBreaker());
            Lab.Dependency d = newDep.apply(start);
            vs.add(new Variant("FoodFleet breaker (slow call = 1 s), no timeout", cb, d, () -> cb.executeCheckedSupplier(d::call)));
        }
        {
            CircuitBreaker cb = CircuitBreaker.of("c", ResiliencePolicies.circuitBreaker());
            Bulkhead bh = Bulkhead.of("c", ResiliencePolicies.bulkhead(32));
            Lab.Dependency d = newDep.apply(start);
            vs.add(new Variant("FoodFleet breaker + bulkhead 32", cb, d,
                    () -> cb.executeCheckedSupplier(Bulkhead.decorateCheckedSupplier(bh, d::call))));
        }
        {
            CircuitBreaker cb = CircuitBreaker.of("d", CircuitBreakerConfig.custom().slidingWindow(5, 20, SlidingWindowType.TIME_BASED)
                    .waitDurationInOpenState(Duration.ofSeconds(5)).build());
            TimeLimiter tl = TimeLimiter.of(ResiliencePolicies.timeLimiter(Duration.ofSeconds(1)));
            Lab.Dependency d = newDep.apply(start);
            Callable<String> timed = TimeLimiter.decorateFutureSupplier(tl, () -> CompletableFuture.supplyAsync(() -> {
                try {
                    return d.call();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, vt));
            vs.add(new Variant("breaker + TimeLimiter 1 s on a CompletableFuture", cb, d, () -> cb.executeCallable(timed)));
        }
        {
            CircuitBreaker cb = CircuitBreaker.of("e", CircuitBreakerConfig.custom().slidingWindow(5, 20, SlidingWindowType.TIME_BASED)
                    .waitDurationInOpenState(Duration.ofSeconds(5)).build());
            TimeLimiter tl = TimeLimiter.of(ResiliencePolicies.timeLimiter(Duration.ofSeconds(1)));
            Lab.Dependency d = newDep.apply(start);
            Callable<String> timed = TimeLimiter.decorateFutureSupplier(tl, () -> vt.submit(d::call));
            vs.add(new Variant("breaker + TimeLimiter 1 s on an ExecutorService Future", cb, d, () -> cb.executeCallable(timed)));
        }
        record Result(List<Transition> ts, ConcurrentLinkedQueue<Long> incidentLatency, AtomicLong incidentUsers,
                      AtomicLong fast, AtomicLong startedDuring) {
        }
        List<Result> results = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        for (Variant v : vs) {
            Result r = new Result(record(v.cb(), start), new ConcurrentLinkedQueue<>(), new AtomicLong(), new AtomicLong(), new AtomicLong());
            results.add(r);
            tasks.add(Lab.unchecked(() -> Lab.openLoop(start, 50, 45, at -> {
                long t0 = System.nanoTime();
                long before = v.dep().calls.get();
                try {
                    v.call().get();
                } catch (Throwable ignored) {
                    // timeouts, refusals
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                if (at >= 10 && at < 30) {
                    r.incidentUsers().incrementAndGet();
                    r.incidentLatency().add(ms);
                    if (ms <= 1100) {
                        r.fast().incrementAndGet();
                    }
                    if (v.dep().calls.get() > before) {
                        r.startedDuring().incrementAndGet();
                    }
                }
            })));
        }
        Lab.parallel(tasks);
        vt.shutdown();
        System.out.printf("%-56s %13s %16s %10s %10s %18s%n", "configuration", "opened after", "max calls in", "p50", "p99", "users answered");
        System.out.printf("%-56s %13s %16s %10s %10s %18s%n", "", "slowdown", "progress at dep", "(10-30 s)", "(10-30 s)", "within 1.1 s");
        for (int i = 0; i < vs.size(); i++) {
            Variant v = vs.get(i);
            Result r = results.get(i);
            List<Long> lat = new ArrayList<>(r.incidentLatency());
            System.out.printf("%-56s %13s %16d %8d ms %8d ms %18s%n", v.name(),
                    Lab.secs(firstAfter(r.ts(), 10, CircuitBreaker.State.OPEN)), v.dep().maxInFlight.get(),
                    Lab.percentile(lat, 50), Lab.percentile(lat, 99), Lab.pct(r.fast().get(), r.incidentUsers().get()));
        }
    }

    // ============================================================================================ time limiter

    static void timeLimiter() throws Exception {
        int n = 40;
        Duration timeout = Duration.ofMillis(500);
        System.out.println(n + " calls per row, each would take 3 s; TimeLimiter timeout 500 ms, cancelRunningFuture = true (the default)");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 200);
        AtomicInteger serverSaw = new AtomicInteger();
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                byte[] b = "menu".getBytes();
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                serverSaw.incrementAndGet();
            } catch (IOException e) {
                // client went away
            } finally {
                ex.close();
            }
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");
        HttpClient jdk = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();

        record Work(String name, Callable<String> body) {
        }
        List<Work> works = List.of(
                new Work("Thread.sleep(3 s)", () -> {
                    Thread.sleep(3000);
                    return "ok";
                }),
                new Work("JDK HttpClient.send (Spring's JdkClientHttpRequestFactory)", () ->
                        jdk.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body()),
                new Work("HttpURLConnection read (SimpleClientHttpRequestFactory)", () -> {
                    HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
                    try (InputStream in = c.getInputStream()) {
                        return new String(in.readAllBytes());
                    }
                }),
                new Work("CPU loop for 3 s (never checks interruption)", () -> {
                    long end = System.nanoTime() + 3_000_000_000L;
                    long x = 0;
                    while (System.nanoTime() < end) {
                        x++;
                    }
                    return Long.toString(x);
                }));
        System.out.printf("%-60s %-24s %14s %20s%n", "work", "future", "callers waited", "work still running");
        System.out.printf("%-60s %-24s %14s %20s%n", "", "", "(max)", "1 s after timeout");
        for (Work w : works) {
            for (String kind : List.of("CompletableFuture", "ExecutorService.submit")) {
                if (w.name().startsWith("CPU") && kind.startsWith("Completable")) {
                    continue;
                }
                ExecutorService pool = Executors.newFixedThreadPool(n);   // platform threads: the CPU row needs them
                TimeLimiter tl = TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(timeout).cancelRunningFuture(true).build());
                AtomicInteger running = new AtomicInteger();
                Callable<String> body = () -> {
                    running.incrementAndGet();
                    try {
                        return w.body().call();
                    } finally {
                        running.decrementAndGet();
                    }
                };
                Supplier<Future<String>> futures = kind.startsWith("Completable")
                        ? () -> CompletableFuture.supplyAsync(() -> {
                            try {
                                return body.call();
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        }, pool)
                        : () -> pool.submit(body);
                AtomicLong maxWait = new AtomicLong();
                CountDownLatch done = new CountDownLatch(n);
                ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
                for (int i = 0; i < n; i++) {
                    callers.submit(() -> {
                        long t0 = System.nanoTime();
                        try {
                            tl.executeFutureSupplier(futures);
                        } catch (Exception ignored) {
                            // TimeoutException expected
                        }
                        maxWait.accumulateAndGet((System.nanoTime() - t0) / 1_000_000, Math::max);
                        done.countDown();
                    });
                }
                done.await();
                Thread.sleep(1000);
                int still = running.get();
                System.out.printf("%-60s %-24s %11d ms %14d of %d%n", w.name(), kind, maxWait.get(), still, n);
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);
                callers.shutdown();
                Thread.sleep(2500);   // let abandoned work drain before the next row
            }
        }
        server.stop(0);
    }

    // ============================================================================================ decorator order

    static void order() throws Exception {
        System.out.println("users at 50/s for 12 s; Resilience4j Retry: 3 attempts, 500 ms apart (library defaults)");
        System.out.println("breaker: last 20 calls, min 10, open 30 s (stays open for the rest of the run)");
        System.out.println("A) the dependency is down (every call fails)");
        record Row(String name, AtomicLong depCalls, ConcurrentLinkedQueue<Long> openLatency, AtomicLong users) {
        }
        List<Row> rows = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        long start = System.nanoTime() + 200_000_000L;
        for (String variant : List.of("Retry ( CircuitBreaker ( call ) )   Spring Boot's default order",
                "same, Retry ignores CallNotPermittedException",
                "CircuitBreaker ( Retry ( call ) )")) {
            CircuitBreaker cb = CircuitBreaker.of(variant, CircuitBreakerConfig.custom().slidingWindow(20, 10, SlidingWindowType.COUNT_BASED)
                    .waitDurationInOpenState(Duration.ofSeconds(30)).build());
            RetryConfig rc = variant.startsWith("same")
                    ? RetryConfig.custom().ignoreExceptions(CallNotPermittedException.class).build()
                    : RetryConfig.ofDefaults();
            Retry retry = Retry.of(variant, rc);
            Lab.Dependency dep = new Lab.Dependency(start, t -> new Lab.Behaviour(1.0, 2));
            CheckedSupplier<String> pipeline = variant.startsWith("CircuitBreaker")
                    ? CircuitBreaker.decorateCheckedSupplier(cb, Retry.decorateCheckedSupplier(retry, dep::call))
                    : Retry.decorateCheckedSupplier(retry, CircuitBreaker.decorateCheckedSupplier(cb, dep::call));
            Row row = new Row(variant, dep.calls, new ConcurrentLinkedQueue<>(), new AtomicLong());
            rows.add(row);
            tasks.add(Lab.unchecked(() -> Lab.openLoop(start, 50, 12, at -> {
                boolean openAtStart = cb.getState() == CircuitBreaker.State.OPEN;
                long t0 = System.nanoTime();
                try {
                    pipeline.get();
                } catch (Throwable ignored) {
                    // expected
                }
                row.users().incrementAndGet();
                if (openAtStart) {
                    row.openLatency().add((System.nanoTime() - t0) / 1_000_000);
                }
            })));
        }
        Lab.parallel(tasks);
        System.out.printf("%-66s %16s %16s %24s%n", "order", "calls to the", "calls per user", "user wait while open");
        System.out.printf("%-66s %16s %16s %24s%n", "", "dead dependency", "", "(p50 / p99)");
        for (Row r : rows) {
            List<Long> l = new ArrayList<>(r.openLatency());
            System.out.printf("%-66s %16d %16.2f %15d / %d ms%n", r.name(), r.depCalls().get(),
                    (double) r.depCalls().get() / r.users().get(), Lab.percentile(l, 50), Lab.percentile(l, 99));
        }

        System.out.println();
        System.out.println("B) 30% of calls fail at random (transient); each user gets a menu or the cached fallback");
        record Fb(String name, AtomicLong fresh, AtomicLong stale, AtomicLong users, AtomicLong depCalls) {
        }
        List<Fb> fbs = new ArrayList<>();
        tasks.clear();
        long start2 = System.nanoTime() + 200_000_000L;
        for (String variant : List.of("fallback outside the retry (on @Retry)", "fallback on @CircuitBreaker, inside the retry")) {
            CircuitBreaker cb = CircuitBreaker.of("fb" + variant, ResiliencePolicies.circuitBreaker());
            Retry retry = Retry.of("fb" + variant, RetryConfig.custom().maxAttempts(3)
                    .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(50), 2, 1.0)).build());
            Lab.Dependency dep = new Lab.Dependency(start2, t -> new Lab.Behaviour(0.30, 2));
            Fb fb = new Fb(variant, new AtomicLong(), new AtomicLong(), new AtomicLong(), dep.calls);
            fbs.add(fb);
            CheckedSupplier<String> pipeline;
            if (variant.startsWith("fallback outside")) {
                CheckedSupplier<String> inner = Retry.decorateCheckedSupplier(retry, CircuitBreaker.decorateCheckedSupplier(cb, dep::call));
                pipeline = () -> {
                    try {
                        return inner.get();
                    } catch (Exception e) {
                        return "stale";
                    }
                };
            } else {
                CheckedSupplier<String> withFallback = () -> {
                    try {
                        return cb.executeCheckedSupplier(dep::call);
                    } catch (Exception e) {
                        return "stale";
                    }
                };
                pipeline = Retry.decorateCheckedSupplier(retry, withFallback);
            }
            tasks.add(Lab.unchecked(() -> Lab.openLoop(start2, 50, 10, at -> {
                try {
                    String r = pipeline.get();
                    (r.equals("stale") ? fb.stale() : fb.fresh()).incrementAndGet();
                } catch (Throwable t) {
                    fb.stale().incrementAndGet();
                }
                fb.users().incrementAndGet();
            })));
        }
        Lab.parallel(tasks);
        System.out.printf("%-52s %18s %18s %16s%n", "fallback placement", "users: fresh menu", "users: cached menu", "calls per user");
        for (Fb f : fbs) {
            System.out.printf("%-52s %18s %18s %16.2f%n", f.name(), Lab.pct(f.fresh().get(), f.users().get()),
                    Lab.pct(f.stale().get(), f.users().get()), (double) f.depCalls().get() / f.users().get());
        }
    }

    // ============================================================================================ rate limiter

    static int maxInWindow(List<Long> sortedNanos, long windowNanos) {
        int best = 0;
        int j = 0;
        for (int i = 0; i < sortedNanos.size(); i++) {
            while (sortedNanos.get(i) - sortedNanos.get(j) >= windowNanos) {
                j++;
            }
            best = Math.max(best, i - j + 1);
        }
        return best;
    }

    static void rateLimiter() throws Exception {
        {
            RateLimiter rl = RateLimiter.ofDefaults("defaults");
            AtomicLong permitted = new AtomicLong();
            long end = System.nanoTime() + 1_000_000_000L;
            List<Runnable> ts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ts.add(() -> {
                    while (System.nanoTime() < end) {
                        if (rl.acquirePermission()) {
                            permitted.incrementAndGet();
                        }
                    }
                });
            }
            Lab.parallel(ts);
            System.out.printf("library defaults (50 permits per 500 ns, wait up to 5 s): 4 threads were permitted %,d calls in 1 s%n", permitted.get());
        }
        System.out.println("offered: 1,000 calls/s (Poisson) for 10 s; wait for a permit: 0");
        System.out.printf("%-34s %14s %20s %22s %18s%n", "limit", "permitted", "max in any 1 s", "max in any 100 ms", "intended per s");
        for (int[] cfg : new int[][] {{100, 1000}, {10, 100}}) {
            RateLimiter rl = RateLimiter.of("l" + cfg[0], RateLimiterConfig.custom().limitForPeriod(cfg[0])
                    .limitRefreshPeriod(Duration.ofMillis(cfg[1])).timeoutDuration(Duration.ZERO).build());
            ConcurrentLinkedQueue<Long> at = new ConcurrentLinkedQueue<>();
            long start = System.nanoTime() + 100_000_000L;
            Lab.openLoop(start, 1000, 10, t -> {
                if (rl.acquirePermission()) {
                    at.add(System.nanoTime());
                }
            });
            List<Long> s = new ArrayList<>(at);
            Collections.sort(s);
            System.out.printf("%-34s %14d %20d %22d %18d%n", cfg[0] + " per " + cfg[1] + " ms", s.size(),
                    maxInWindow(s, 1_000_000_000L), maxInWindow(s, 100_000_000L), cfg[0] * 1000 / cfg[1]);
        }
        System.out.println();
        System.out.println("offered: 200 calls/s for 10 s against 100 per second; callers wait for a permit");
        System.out.printf("%-26s %12s %12s %18s %22s%n", "wait for a permit", "permitted", "refused", "max callers waiting", "p99 wait (permitted)");
        for (Duration wait : List.of(Duration.ofSeconds(5), Duration.ofMillis(100), Duration.ZERO)) {
            RateLimiter rl = RateLimiter.of("w" + wait, RateLimiterConfig.custom().limitForPeriod(100)
                    .limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(wait).build());
            AtomicInteger waiting = new AtomicInteger();
            AtomicInteger maxWaiting = new AtomicInteger();
            AtomicLong refused = new AtomicLong();
            ConcurrentLinkedQueue<Long> waits = new ConcurrentLinkedQueue<>();
            long start = System.nanoTime() + 100_000_000L;
            Lab.openLoop(start, 200, 10, t -> {
                maxWaiting.accumulateAndGet(waiting.incrementAndGet(), Math::max);
                long t0 = System.nanoTime();
                boolean ok;
                try {
                    ok = rl.acquirePermission();
                } finally {
                    waiting.decrementAndGet();
                }
                if (ok) {
                    waits.add((System.nanoTime() - t0) / 1_000_000);
                } else {
                    refused.incrementAndGet();
                }
            });
            System.out.printf("%-26s %12d %12d %18d %19d ms%n", wait.toMillis() + " ms" + (wait.toSeconds() == 5 ? " (the default)" : ""),
                    waits.size(), refused.get(), maxWaiting.get(), Lab.percentile(new ArrayList<>(waits), 99));
        }
    }

    // ============================================================================================ retry schedules

    static void schedules() throws Exception {
        int clients = 1000;
        System.out.println(clients + " clients call a dependency at the same moment; every call fails; each client retries per its policy");
        System.out.println("peak = most retries arriving in any 10 ms (the dependency's worst moment); spread = time between the 5th and 95th percentile retry");
        record P(String name, java.util.function.Function<Lab.Dependency, Callable<String>> client) {
        }
        List<P> ps = List.of(
                new P("Spring @Retryable defaults (3 retries, 1 s, no jitter)", d -> spring(RetryPolicy.builder().build(), d)),
                new P("Spring delay 200 ms, x2, jitter 100 ms", d -> spring(RetryPolicy.builder().delay(Duration.ofMillis(200))
                        .multiplier(2).jitter(Duration.ofMillis(100)).build(), d)),
                new P("Spring delay 200 ms, x2, jitter 200 ms", d -> spring(RetryPolicy.builder().delay(Duration.ofMillis(200))
                        .multiplier(2).jitter(Duration.ofMillis(200)).build(), d)),
                new P("Resilience4j defaults (3 attempts, 500 ms, no jitter)", d -> r4j(RetryConfig.ofDefaults(), d)),
                new P("Resilience4j 200 ms, x2, randomization 0.5 (its default)", d -> r4j(RetryConfig.custom().maxAttempts(4)
                        .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(200, 2, 0.5)).build(), d)),
                new P("Resilience4j 200 ms, x2, randomization 1.0", d -> r4j(RetryConfig.custom().maxAttempts(4)
                        .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(200, 2, 1.0)).build(), d)));
        System.out.printf("%-58s %22s %22s %22s%n", "policy", "retry 1: peak / spread", "retry 2: peak / spread", "retry 3: peak / spread");
        for (P p : ps) {
            ConcurrentLinkedQueue<long[]> attempts = new ConcurrentLinkedQueue<>();
            long start = System.nanoTime();
            ThreadLocal<int[]> attempt = ThreadLocal.withInitial(() -> new int[1]);
            Lab.Dependency dep = new Lab.Dependency(start, t -> new Lab.Behaviour(1.0, 0)) {
                @Override
                String call() throws IOException, InterruptedException {
                    int n = attempt.get()[0]++;
                    attempts.add(new long[] {n, System.nanoTime()});
                    throw new IOException("down");
                }
            };
            Callable<String> client = p.client().apply(dep);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
            for (int i = 0; i < clients; i++) {
                vt.submit(() -> {
                    go.await();
                    try {
                        client.call();
                    } catch (Exception ignored) {
                        // all calls fail
                    }
                    return null;
                });
            }
            Thread.sleep(300);
            go.countDown();
            vt.shutdown();
            vt.awaitTermination(30, TimeUnit.SECONDS);
            StringBuilder line = new StringBuilder(String.format("%-58s", p.name()));
            for (int retry = 1; retry <= 3; retry++) {
                List<Long> ts = new ArrayList<>();
                for (long[] a : attempts) {
                    if (a[0] == retry) {
                        ts.add(a[1]);
                    }
                }
                if (ts.isEmpty()) {
                    line.append(String.format(" %22s", "-"));
                    continue;
                }
                Collections.sort(ts);
                long spread = (Lab.percentile(ts, 95) - Lab.percentile(ts, 5)) / 1_000_000;
                line.append(String.format(" %13d / %4d ms", maxInWindow(ts, 10_000_000L), spread));
            }
            System.out.println(line);
        }
    }

    static Callable<String> spring(RetryPolicy policy, Lab.Dependency dep) {
        RetryTemplate template = new RetryTemplate(policy);
        return () -> template.execute(dep::call);
    }

    static Callable<String> r4j(RetryConfig config, Lab.Dependency dep) {
        Retry retry = Retry.of("r", config);
        return () -> {
            try {
                return Retry.decorateCheckedSupplier(retry, dep::call).get();
            } catch (Exception e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        };
    }

    // ============================================================================================ overhead

    /** Spring Framework's concurrency throttle (the base of @ConcurrencyLimit), with its BLOCK or REJECT policy. */
    static final class Throttle extends ConcurrencyThrottleSupport {
        private static final long serialVersionUID = 1L;
        private final boolean reject;

        Throttle(int limit, boolean reject) {
            setConcurrencyLimit(limit);
            this.reject = reject;
        }

        @Override
        protected void onLimitReached() {
            if (reject) {
                onAccessRejected("Concurrency limit reached");
            } else {
                super.onLimitReached();
            }
        }

        <T> T run(Callable<T> c) throws Exception {
            beforeAccess();
            try {
                return c.call();
            } finally {
                afterAccess();
            }
        }
    }

    static void overhead() throws Exception {
        System.out.println("cost per call of each decorator around a call that does nothing (median of 5 rounds of 2 s)");
        CircuitBreaker cbSync = CircuitBreaker.of("s", CircuitBreakerConfig.custom().slidingWindowSynchronizationStrategy(SlidingWindowSynchronizationStrategy.SYNCHRONIZED).build());
        CircuitBreaker cbFree = CircuitBreaker.of("f", CircuitBreakerConfig.custom().slidingWindowSynchronizationStrategy(SlidingWindowSynchronizationStrategy.LOCK_FREE).build());
        CircuitBreaker cbTime = CircuitBreaker.of("t", ResiliencePolicies.circuitBreaker());
        Retry retry = Retry.of("r", ResiliencePolicies.retry());
        Bulkhead bh = Bulkhead.of("b", BulkheadConfig.custom().maxConcurrentCalls(1000).build());
        RateLimiter rl = RateLimiter.of("l", RateLimiterConfig.custom().limitForPeriod(Integer.MAX_VALUE).limitRefreshPeriod(Duration.ofSeconds(1)).build());
        RetryTemplate rt = new RetryTemplate(RetryPolicy.builder().build());
        Throttle throttle = new Throttle(1000, true);
        Resilient pipeline = Resilient.of(retry, cbTime, bh);
        record Op(String name, Callable<Object> op) {
        }
        Callable<Object> noop = () -> "x";
        List<Op> ops = List.of(
                new Op("no decorator", noop),
                new Op("CircuitBreaker, count window (SYNCHRONIZED, the default)", () -> cbSync.executeCallable(noop)),
                new Op("CircuitBreaker, count window, LOCK_FREE", () -> cbFree.executeCallable(noop)),
                new Op("CircuitBreaker, FoodFleet (5 s time window)", () -> cbTime.executeCallable(noop)),
                new Op("Retry (success on first attempt)", () -> retry.executeCallable(noop)),
                new Op("Bulkhead (semaphore)", () -> bh.executeCallable(noop)),
                new Op("RateLimiter (AtomicRateLimiter)", () -> rl.executeCallable(noop)),
                new Op("Retry + CircuitBreaker + Bulkhead (FoodFleet pipeline)", () -> pipeline.call(noop::call, t -> "fallback")),
                new Op("Spring RetryTemplate (success on first attempt)", () -> rt.execute(noop::call)),
                new Op("Spring concurrency throttle (@ConcurrencyLimit's base)", () -> throttle.run(noop)));
        System.out.printf("%-60s %14s %16s%n", "decorator", "1 thread", "2 threads");
        for (Op op : ops) {
            System.out.printf("%-60s %11.0f ns %13.0f ns%n", op.name(), nsPerCall(op.op(), 1), nsPerCall(op.op(), 2));
        }
    }

    /** Median over rounds of the time per call, per thread, with {@code threads} threads calling concurrently. */
    static double nsPerCall(Callable<Object> op, int threads) throws Exception {
        for (int i = 0; i < 200_000; i++) {
            op.call();   // warm up
        }
        List<Double> rounds = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            AtomicLong calls = new AtomicLong();
            long end = System.nanoTime() + 2_000_000_000L;
            long t0 = System.nanoTime();
            List<Runnable> ts = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                ts.add(Lab.unchecked(() -> {
                    long n = 0;
                    while (System.nanoTime() < end) {
                        for (int i = 0; i < 1000; i++) {
                            op.call();
                        }
                        n += 1000;
                    }
                    calls.addAndGet(n);
                }));
            }
            Lab.parallel(ts);
            double elapsed = System.nanoTime() - t0;
            rounds.add(elapsed * threads / calls.get());
        }
        Collections.sort(rounds);
        return rounds.get(2);
    }

    // ============================================================================================ overload

    static void overload() throws Exception {
        System.out.println("a dependency call takes 50 ms; at most 20 at a time (capacity 400/s); users arrive at 800/s for 10 s");
        record V(String name, Callable<Callable<String>> make) {
        }
        List<V> vs = List.of(
                new V("Resilience4j Bulkhead, maxWaitDuration 0 (default)", () -> {
                    Bulkhead b = Bulkhead.of("a", BulkheadConfig.custom().maxConcurrentCalls(20).build());
                    return () -> b.executeCallable(Measure::work50);
                }),
                new V("Resilience4j Bulkhead, maxWaitDuration 200 ms", () -> {
                    Bulkhead b = Bulkhead.of("b", BulkheadConfig.custom().maxConcurrentCalls(20).maxWaitDuration(Duration.ofMillis(200)).build());
                    return () -> b.executeCallable(Measure::work50);
                }),
                new V("Spring @ConcurrencyLimit(20), policy BLOCK (default)", () -> {
                    Throttle t = new Throttle(20, false);
                    return () -> t.run(Measure::work50);
                }),
                new V("Spring @ConcurrencyLimit(20), policy REJECT", () -> {
                    Throttle t = new Throttle(20, true);
                    return () -> t.run(Measure::work50);
                }));
        System.out.printf("%-54s %10s %16s %16s %18s %20s%n", "limit", "served", "served p50", "served p99", "refused: p99 wait", "users still waiting");
        System.out.printf("%-54s %10s %16s %16s %18s %20s%n", "", "", "", "", "", "when arrivals stop");
        for (V v : vs) {
            Callable<String> call = v.make().call();
            ConcurrentLinkedQueue<Long> served = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Long> refused = new ConcurrentLinkedQueue<>();
            AtomicInteger inside = new AtomicInteger();
            AtomicInteger atStop = new AtomicInteger(-1);
            long start = System.nanoTime() + 100_000_000L;
            Thread watcher = Thread.ofPlatform().start(() -> {
                try {
                    TimeUnit.NANOSECONDS.sleep(start + 10_000_000_000L - System.nanoTime());
                    atStop.set(inside.get());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            Lab.openLoop(start, 800, 10, at -> {
                inside.incrementAndGet();
                long t0 = System.nanoTime();
                try {
                    call.call();
                    served.add((System.nanoTime() - t0) / 1_000_000);
                } catch (Exception e) {
                    refused.add((System.nanoTime() - t0) / 1_000_000);
                } finally {
                    inside.decrementAndGet();
                }
            });
            watcher.join();
            List<Long> s = new ArrayList<>(served);
            List<Long> r = new ArrayList<>(refused);
            System.out.printf("%-54s %10s %13d ms %13d ms %15s %20d%n", v.name(), Lab.pct(s.size(), s.size() + r.size()),
                    Lab.percentile(s, 50), Lab.percentile(s, 99), r.isEmpty() ? "-" : Lab.percentile(r, 99) + " ms", atStop.get());
        }
    }

    static String work50() throws InterruptedException {
        Thread.sleep(50);
        return "ok";
    }
}
