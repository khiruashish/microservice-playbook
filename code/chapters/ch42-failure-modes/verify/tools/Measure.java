import dev.foodfleet.ch42.resilience.Backoff;
import dev.foodfleet.ch42.resilience.Deadline;
import dev.foodfleet.ch42.resilience.P2cBalancer;
import dev.foodfleet.ch42.resilience.QueueAgeShedder;
import dev.foodfleet.ch42.resilience.RetryBudget;
import dev.foodfleet.ch42.resilience.RetryPolicy;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Chapter 42 measurements. Modes: queueing, amplification, metastable, herd, tail. JDK 21+ only. */
public final class Measure {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "queueing" -> queueing();
            case "amplification" -> amplification();
            case "metastable" -> metastable();
            case "herd" -> herd();
            case "tail" -> tail();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static long pct(long[] sorted, double q) {
        if (sorted.length == 0) {
            return -1;
        }
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))];
    }

    static long[] sorted(ConcurrentLinkedQueue<Long> q) {
        long[] a = q.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(a);
        return a;
    }

    // ------------------------------------------------------------------------------------------------ queueing

    /** Erlang C: probability that an arrival waits, for c servers at offered load a = lambda/mu. */
    static double erlangC(int c, double a) {
        double sum = 0;
        double term = 1;
        for (int k = 0; k < c; k++) {
            if (k > 0) {
                term *= a / k;
            }
            sum += term;
        }
        double last = term * a / c;   // a^c / c!
        double top = last * c / (c - a);
        return top / (sum + top);
    }

    static double lastServiceMs;
    static double lastRate;
    static double lastUtil;

    static long[] run(int workers, Duration mean, double rate, int seconds) throws Exception {
        Lab.Series series = new Lab.Series(System.currentTimeMillis() + 200, 1000, 60);
        ConcurrentLinkedQueue<Long> lat = new ConcurrentLinkedQueue<>();
        try (Lab.Service svc = new Lab.Service("s", workers, series, Lab.sleeping(mean, () -> 1.0))) {
            URI uri = svc.uri();
            Lab.openLoop(x -> rate, seconds, series, "u", at -> {
                Lab.Outcome o = Lab.call(uri, RetryPolicy.none(), Duration.ofSeconds(30),
                        Deadline.after(Duration.ofSeconds(30), at), series, "u");
                if (at > series.start + 3000) {   // skip the warm-up
                    lat.add(o.latencyMillis());
                }
                return o;
            });
            lastServiceMs = svc.busyNanos.get() / 1e6 / Math.max(1, svc.served.get());
            lastRate = series.total("u.offered") / (double) seconds;
            lastUtil = svc.busyNanos.get() / 1e9 / (workers * (double) seconds);
        }
        return sorted(lat);
    }

    static void queueing() throws Exception {
        int workers = 8;
        Duration mean = Duration.ofMillis(25);
        System.out.printf("one service: %d workers; work is a sleep, exponential with mean %d ms; users arrive as a Poisson process%n",
                workers, mean.toMillis());
        System.out.println("(utilisation and service time measured inside the server; M/M/c is the textbook prediction for them)");
        System.out.printf("%-14s %-10s %-14s %-16s %-16s %-10s %-10s %s%n", "utilisation", "rate/s", "service time",
                "mean (M/M/c)", "mean (measured)", "p50", "p99", "p99 / service time");
        for (double u : new double[] {0.5, 0.7, 0.8, 0.9, 0.95, 0.98}) {
            double rate = u * workers * 1000.0 / 25.6;   // 25.6 ms: the sleep plus the handler's own time, under load
            long[] a = run(workers, mean, rate, 25);
            double measuredMean = Arrays.stream(a).average().orElse(0);
            double mu = 1000.0 / lastServiceMs;
            double rho = lastRate / (workers * mu);
            String theory = rho < 1 ? String.format("%.0f ms", lastServiceMs + erlangC(workers, lastRate / mu) / (workers * mu - lastRate) * 1000) : "unbounded";
            System.out.printf("%-14s %-10.0f %-14s %-16s %-16s %-10s %-10s %.1fx%n", String.format("%.0f%%", lastUtil * 100), lastRate,
                    String.format("%.1f ms", lastServiceMs), theory, String.format("%.0f ms", measuredMean), pct(a, 0.5) + " ms",
                    pct(a, 0.99) + " ms", pct(a, 0.99) / lastServiceMs);
        }
    }

    // ------------------------------------------------------------------------------------------------ amplification

    record Layered(String name, Supplier<RetryPolicy> user, Supplier<RetryPolicy> inner) {
    }

    static RetryPolicy threeAttempts(RetryBudget budget) {
        return new RetryPolicy(3, Backoff.fullJitter(Duration.ofMillis(10), Duration.ofMillis(40)), budget);
    }

    static void amplification() throws Exception {
        System.out.println("edge -> order -> restaurant; 50 user requests/s for 10 s; 2 s deadline, 500 ms per attempt");
        List<Layered> strategies = List.of(
                new Layered("no retries", RetryPolicy::none, RetryPolicy::none),
                new Layered("3 attempts at every layer", () -> threeAttempts(null), () -> threeAttempts(null)),
                new Layered("3 attempts at the edge only", () -> threeAttempts(null), RetryPolicy::none),
                new Layered("3 attempts at every layer, 10% budget", null, null));
        for (double failRate : new double[] {1.0, 0.3}) {
            System.out.println();
            System.out.println(failRate == 1.0 ? "restaurant-service down (every call fails fast with 503)"
                    : "restaurant-service failing 30% of calls at random");
            System.out.printf("%-42s %-16s %-26s %-26s %s%n", "retry policy", "users served", "calls to order per user",
                    "calls to restaurant per user", "upper bound");
            for (Layered l : strategies) {
                Lab.Series series = new Lab.Series(System.currentTimeMillis() + 200, 1000, 40);
                boolean budgeted = l.user() == null;
                RetryBudget userBudget = new RetryBudget(0.1, 10);
                RetryBudget edgeBudget = new RetryBudget(0.1, 10);
                RetryBudget orderBudget = new RetryBudget(0.1, 10);
                Supplier<RetryPolicy> userPolicy = budgeted ? () -> threeAttempts(userBudget) : l.user();
                Supplier<RetryPolicy> edgePolicy = budgeted ? () -> threeAttempts(edgeBudget) : l.inner();
                Supplier<RetryPolicy> orderPolicy = budgeted ? () -> threeAttempts(orderBudget) : l.inner();
                try (Lab.Service restaurant = new Lab.Service("restaurant", 64, series, d -> {
                        if (ThreadLocalRandom.current().nextDouble() < failRate) {
                            return 503;
                        }
                        Thread.sleep(5);
                        return 200;
                    });
                     Lab.Service order = new Lab.Service("order", 256, series, null);
                     Lab.Service edge = new Lab.Service("edge", 256, series, null)) {
                    order.work = d -> {
                        if (budgeted) {
                            orderBudget.onRequest();
                        }
                        return Lab.call(restaurant.uri(), orderPolicy.get(), Duration.ofMillis(500), d, series, "order-out").ok() ? 200 : 503;
                    };
                    edge.work = d -> {
                        if (budgeted) {
                            edgeBudget.onRequest();
                        }
                        return Lab.call(order.uri(), edgePolicy.get(), Duration.ofMillis(500), d, series, "edge-out").ok() ? 200 : 503;
                    };
                    Lab.openLoop(x -> 50, 10, series, "user", at -> {
                        if (budgeted) {
                            userBudget.onRequest();
                        }
                        return Lab.call(edge.uri(), userPolicy.get(), Duration.ofMillis(500),
                                Deadline.after(Duration.ofSeconds(2), at), series, "user");
                    });
                    Thread.sleep(500);
                }
                double users = series.total("user.offered");
                int worst = l.name().equals("3 attempts at every layer") ? 27 : l.name().startsWith("3 attempts at the edge") ? 3
                        : l.name().equals("no retries") ? 1 : 27;
                System.out.printf("%-42s %-16s %-26.2f %-26.2f %s%n", l.name(),
                        String.format("%.1f%%", 100 * series.total("user.ok") / users),
                        series.total("order.arrived") / users, series.total("restaurant.arrived") / users,
                        worst + "x" + (budgeted ? " (budget-limited)" : ""));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ metastable

    record Strategy(String name, Supplier<RetryPolicy> policy, QueueAgeShedder shedder) {
    }

    static void metastable() throws Exception {
        int workers = 8;
        Duration mean = Duration.ofMillis(25);
        int seconds = 45;
        double rate = 220;
        System.out.printf("one service, capacity 320 requests/s; users arrive at %.0f/s (69%%) for %d s; each attempt times out "
                + "at 300 ms, each user gives up after 1 s%n", rate, seconds);
        System.out.println("trigger: from 10 s to 15 s the service runs 3x slower (capacity 107/s), then recovers fully");
        System.out.println("share of users served, per 5-second window (the trigger is in the third window)");
        RetryBudget budget = new RetryBudget(0.1, 10);
        List<Strategy> strategies = List.of(
                new Strategy("no retries", RetryPolicy::none, null),
                new Strategy("3 attempts, no backoff", () -> new RetryPolicy(3, Backoff.fixed(Duration.ZERO), null), null),
                new Strategy("3 attempts, jittered backoff", () -> new RetryPolicy(3,
                        Backoff.fullJitter(Duration.ofMillis(100), Duration.ofMillis(400)), null), null),
                new Strategy("3 attempts, 10% retry budget", () -> new RetryPolicy(3, Backoff.fixed(Duration.ZERO), budget), null),
                new Strategy("3 attempts, no backoff; server sheds work queued > 300 ms", () -> new RetryPolicy(3, Backoff.fixed(Duration.ZERO), null),
                        new QueueAgeShedder(300)),
                new Strategy("3 attempts, no backoff; server sheds work queued > 150 ms", () -> new RetryPolicy(3, Backoff.fixed(Duration.ZERO), null),
                        new QueueAgeShedder(150)),
                new Strategy("3 attempts, 10% budget; server sheds > 150 ms", () -> new RetryPolicy(3, Backoff.fixed(Duration.ZERO), budget),
                        new QueueAgeShedder(150)));
        StringBuilder head = new StringBuilder(String.format("%-58s", "client / server"));
        for (int w = 0; w < seconds / 5; w++) {
            head.append(String.format(" %-6s", (w * 5) + "-" + (w * 5 + 5)));
        }
        head.append("  wasted work");
        System.out.println(head);
        String only = System.getenv("ONLY");
        for (Strategy st : strategies) {
            if (only != null && !st.name().contains(only)) {
                continue;
            }
            Lab.Series series = new Lab.Series(System.currentTimeMillis() + 200, 5000, seconds / 5);
            long t0 = series.start;
            try (Lab.Service svc = new Lab.Service("s", workers, series,
                    Lab.sleeping(mean, () -> {
                        long t = System.currentTimeMillis() - t0;
                        return t >= 10_000 && t < 15_000 ? 3.0 : 1.0;
                    }))) {
                svc.shedder = st.shedder();
                URI uri = svc.uri();
                Lab.openLoop(x -> rate, seconds, series, "u", at -> {
                    budget.onRequest();
                    return Lab.call(uri, st.policy().get(), Duration.ofMillis(300), Deadline.after(Duration.ofSeconds(1), at),
                            series, "u");
                });
            }
            StringBuilder row = new StringBuilder(String.format("%-58s", st.name()));
            for (int w = 0; w < seconds / 5; w++) {
                long offered = series.get("u.offered", w);
                row.append(String.format(" %-6s", offered == 0 ? "-" : Math.round(100.0 * series.get("u.ok", w) / offered) + "%"));
            }
            long served = series.total("s.served");
            row.append(String.format("  %d of %d served too late", series.total("s.late"), served));
            System.out.println(row);
        }
    }

    // ------------------------------------------------------------------------------------------------ herd

    record Reconnect(String name, Backoff backoff) {
    }

    static void herd() throws Exception {
        int clients = 600;
        System.out.printf("%d clients lose their connections at the same moment (a gateway restart) and reconnect;%n", clients);
        System.out.println("a reconnect costs 20 ms of a worker (TLS, authentication, resync); 4 workers: 200 reconnects/s;");
        System.out.println("the gateway sheds reconnects that waited over 250 ms; each attempt times out at 1 s");
        System.out.printf("%-34s %-18s %-20s %-20s %-16s %s%n", "reconnect policy", "attempts", "peak per 100 ms later",
                "50% reconnected", "99%", "all");
        for (Reconnect rc : List.of(
                new Reconnect("immediately", Backoff.fixed(Duration.ZERO)),
                new Reconnect("after a fixed 1 s", Backoff.fixed(Duration.ofSeconds(1))),
                new Reconnect("exponential 0.5 s .. 8 s", Backoff.exponential(Duration.ofMillis(500), Duration.ofSeconds(8))),
                new Reconnect("exponential, full jitter", Backoff.fullJitter(Duration.ofMillis(500), Duration.ofSeconds(8))))) {
            Lab.Series series = new Lab.Series(System.currentTimeMillis() + 100, 100, 1200);
            ConcurrentLinkedQueue<Long> connectedAt = new ConcurrentLinkedQueue<>();
            AtomicInteger attempts = new AtomicInteger();
            try (Lab.Service gw = new Lab.Service("gw", 4, series, Lab.sleeping(Duration.ofMillis(20), () -> 1.0))) {
                gw.shedder = new QueueAgeShedder(250);
                URI uri = gw.uri();
                Thread.sleep(100);
                long start = System.currentTimeMillis();
                ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor();
                for (int c = 0; c < clients; c++) {
                    ex.submit(() -> {
                        int n = 0;
                        while (System.currentTimeMillis() - start < 110_000) {
                            n++;
                            attempts.incrementAndGet();
                            series.add("attempt", System.currentTimeMillis());
                            try {
                                HttpResponse<Void> r = Lab.HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(1)).GET().build(),
                                        HttpResponse.BodyHandlers.discarding());
                                if (r.statusCode() == 200) {
                                    connectedAt.add(System.currentTimeMillis() - start);
                                    return null;
                                }
                            } catch (java.io.IOException e) {
                                // timed out: try again
                            }
                            Thread.sleep(rc.backoff().delay(n, ThreadLocalRandom.current()));
                        }
                        return null;
                    });
                }
                ex.shutdown();
                ex.awaitTermination(2, TimeUnit.MINUTES);
            }
            long[] t = sorted(connectedAt);
            long peak = 0;
            for (int b = 10; b < series.buckets; b++) {   // after the first second's initial burst of 600
                peak = Math.max(peak, series.get("attempt", b));
            }
            System.out.printf("%-34s %-18s %-20s %-20s %-16s %s%n", rc.name(), attempts.get() + " (" + String.format("%.1f", attempts.get() / (double) clients) + " each)",
                    peak, String.format("%.1f s", pct(t, 0.5) / 1000.0), String.format("%.1f s", pct(t, 0.99) / 1000.0),
                    t.length == clients ? String.format("%.1f s", t[t.length - 1] / 1000.0) : t.length + " of " + clients);
        }
    }

    // ------------------------------------------------------------------------------------------------ tail

    static void tail() throws Exception {
        Lab.Series series = new Lab.Series(System.currentTimeMillis(), 1000, 600);
        List<Lab.Service> instances = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            boolean gray = i == 3;
            instances.add(new Lab.Service("i" + i, 64, series, d -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                long ms = (long) (-Math.log(1 - r.nextDouble()) * 10);
                if (gray && r.nextDouble() < 0.2) {
                    ms += 300;   // a disk going bad, a noisy neighbour: some requests, not all
                }
                Thread.sleep(ms);
                return 200;
            }));
        }
        try {
            StringBuilder health = new StringBuilder();
            for (Lab.Service s : instances) {
                int code = Lab.HTTP.send(HttpRequest.newBuilder(s.health()).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                health.append(code).append(' ');
            }
            System.out.println("4 instances of menu-service; instance 4 adds 300 ms to 20% of its requests; health checks: " + health.toString().strip());
            System.out.println("each user request fans out to k calls and waits for all of them; 30 user requests/s for 15 s");
            System.out.printf("%-6s %-24s %-10s %-10s %-24s %s%n", "k", "balancing", "p50", "p99", "users waiting > 250 ms", "calls sent to instance 4");
            List<URI> uris = instances.stream().map(Lab.Service::uri).toList();
            for (int k : new int[] {1, 5, 10}) {
                for (String mode : new String[] {"round robin", "power of two choices"}) {
                    ConcurrentLinkedQueue<Long> lat = new ConcurrentLinkedQueue<>();
                    AtomicInteger rr = new AtomicInteger();
                    P2cBalancer<URI> p2c = new P2cBalancer<>(uris);
                    long before = instances.get(3).served.get();
                    long beforeAll = instances.stream().mapToLong(s -> s.served.get()).sum();
                    Lab.Series s = new Lab.Series(System.currentTimeMillis() + 100, 1000, 60);
                    ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();
                    Lab.openLoop(x -> 30, 15, s, "u", at -> {
                        long start = System.currentTimeMillis();
                        List<Future<?>> fs = new ArrayList<>();
                        for (int j = 0; j < k; j++) {
                            fs.add(calls.submit(() -> {
                                int idx = mode.equals("round robin") ? Math.floorMod(rr.getAndIncrement(), uris.size())
                                        : p2c.acquire(ThreadLocalRandom.current());
                                try {
                                    Lab.HTTP.send(HttpRequest.newBuilder(uris.get(idx)).timeout(Duration.ofSeconds(5)).GET().build(),
                                            HttpResponse.BodyHandlers.discarding());
                                } finally {
                                    if (!mode.equals("round robin")) {
                                        p2c.done(idx);
                                    }
                                }
                                return null;
                            }));
                        }
                        for (Future<?> f : fs) {
                            try {
                                f.get();
                            } catch (Exception e) {
                                // counted by latency
                            }
                        }
                        long l = System.currentTimeMillis() - start;
                        if (at > s.start + 2000) {
                            lat.add(l);
                        }
                        return new Lab.Outcome(true, 1, l);
                    });
                    calls.shutdown();
                    long[] a = sorted(lat);
                    long slow = Arrays.stream(a).filter(x -> x > 250).count();
                    long toGray = instances.get(3).served.get() - before;
                    long all = instances.stream().mapToLong(x -> x.served.get()).sum() - beforeAll;
                    System.out.printf("%-6d %-24s %-10s %-10s %-24s %s%n", k, mode, pct(a, 0.5) + " ms", pct(a, 0.99) + " ms",
                            String.format("%.1f%%", 100.0 * slow / a.length), String.format("%.0f%%", 100.0 * toGray / all));
                }
            }
        } finally {
            instances.forEach(Lab.Service::close);
        }
    }
}
