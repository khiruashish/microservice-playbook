import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch45.chaos.Experiment;
import dev.foodfleet.ch45.chaos.Hypothesis;
import dev.foodfleet.ch45.chaos.Toxic;
import dev.foodfleet.ch45.chaos.Toxiproxy;
import dev.foodfleet.ch45.chaos.Window;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Chapter 45's experiments: real faults from Toxiproxy 2.12.0 against real clients and Resilience4j 2.4.0. */
public final class Measure {

    static Path toxiproxyBinary;

    public static void main(String[] args) throws Exception {
        toxiproxyBinary = Path.of(System.getProperty("toxiproxy.bin"));
        switch (args[0]) {
            case "faults" -> faults();
            case "blast" -> blast();
            case "breaker" -> breaker();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    // ============================================================================================ clients

    interface Client {
        int get(URI uri) throws Exception;   // returns the number of body bytes read
    }

    static HttpClient.Builder jdk() {
        return HttpClient.newBuilder().proxy(ProxySelector.of(null));
    }

    static Client spring(ClientHttpRequestFactory factory) {
        return uri -> {
            try (ClientHttpResponse r = factory.createRequest(uri, HttpMethod.GET).execute(); InputStream in = r.getBody()) {
                return in.readAllBytes().length;
            }
        };
    }

    static JdkClientHttpRequestFactory springJdk(Duration readTimeout) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(jdk().connectTimeout(Duration.ofMillis(500)).build());
        if (readTimeout != null) {
            f.setReadTimeout(readTimeout);
        }
        return f;
    }

    record NamedClient(String name, java.util.function.Supplier<Client> make) {
    }

    static final List<NamedClient> CLIENTS = List.of(
            new NamedClient("JDK HttpClient, no timeouts (its default)", () -> {
                HttpClient c = jdk().build();
                return uri -> c.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray()).body().length;
            }),
            new NamedClient("JDK HttpClient, connect 500 ms, request timeout 1 s", () -> {
                HttpClient c = jdk().connectTimeout(Duration.ofMillis(500)).build();
                return uri -> c.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(1)).build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body().length;
            }),
            new NamedClient("Spring JdkClientHttpRequestFactory, read timeout 1 s", () -> spring(springJdk(Duration.ofSeconds(1)))),
            new NamedClient("Spring SimpleClientHttpRequestFactory, read timeout 1 s", () -> {
                SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
                f.setProxy(Proxy.NO_PROXY);
                f.setConnectTimeout(500);
                f.setReadTimeout(1000);
                return spring(f);
            }));

    // ============================================================================================ faults

    record Fault(String name, Toxic toxic, boolean disable) {
    }

    static void faults() throws Exception {
        System.out.println("restaurant-service returns a 20 KiB menu in about 5 ms; each cell: 3 calls, observed for up to 12 s");
        List<Fault> faults = List.of(
                new Fault("no fault", null, false),
                new Fault("latency +2 s", Toxic.latency(2000, 0), false),
                new Fault("bandwidth 1 KB/s", Toxic.bandwidth(1), false),
                new Fault("black hole (requests vanish, connection held)", Toxic.blackHole(), false),
                new Fault("no data, connection closed after 3 s", Toxic.dropAfter(3000), false),
                new Fault("TCP reset", Toxic.resetPeer(0), false),
                new Fault("connection closed after 10 KiB", Toxic.limitData(10 * 1024), false),
                new Fault("proxy disabled (connection refused)", null, true));
        try (Lab.ToxiproxyServer tp = new Lab.ToxiproxyServer(toxiproxyBinary)) {
            HttpServer upstream = Lab.restaurantService();
            Toxiproxy api = new Toxiproxy(tp.api());
            System.out.printf("%-48s %-58s %s%n", "fault", "client", "outcome (3 calls)");
            int n = 0;
            for (Fault f : faults) {
                for (NamedClient c : CLIENTS) {
                    String proxy = "menu" + (n++);
                    int port = Lab.freePort();
                    api.createProxy(proxy, "127.0.0.1:" + port, "127.0.0.1:" + upstream.getAddress().getPort());
                    if (f.toxic() != null) {
                        api.add(proxy, f.toxic());
                    }
                    if (f.disable()) {
                        api.enabled(proxy, false);
                    }
                    URI uri = URI.create("http://127.0.0.1:" + port + "/menu");
                    System.out.printf("%-48s %-58s %s%n", c == CLIENTS.get(0) ? f.name() : "", c.name(), cell(c.make().get(), uri));
                    api.reset();
                }
            }
            upstream.stop(0);
        }
    }

    static String cell(Client client, URI uri) throws Exception {
        ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<String>> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                long t0 = System.nanoTime();
                try {
                    int bytes = client.get(uri);
                    double s = (System.nanoTime() - t0) / 1e9;
                    return bytes == Lab.MENU.length ? String.format("ok %.1f s", s)
                            : String.format("TRUNCATED: %d of %d bytes accepted %.1f s", bytes, Lab.MENU.length, s);
                } catch (Exception e) {
                    Throwable t = e;
                    while (t.getCause() != null && (t instanceof java.io.UncheckedIOException || t.getClass() == RuntimeException.class)) {
                        t = t.getCause();
                    }
                    return String.format("%s %.1f s", t.getClass().getSimpleName(), (System.nanoTime() - t0) / 1e9);
                }
            }, vt));
        }
        List<String> outcomes = new ArrayList<>();
        long deadline = System.nanoTime() + 12_000_000_000L;
        for (CompletableFuture<String> f : calls) {
            try {
                outcomes.add(f.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            } catch (TimeoutException e) {
                outcomes.add("hung (no answer in 12 s)");
            }
        }
        vt.shutdownNow();
        // summarise: same kind of outcome -> one entry with the time range
        String kind0 = outcomes.get(0).replaceAll(" [0-9.]+ s$", "");
        boolean same = outcomes.stream().allMatch(o -> o.replaceAll(" [0-9.]+ s$", "").equals(kind0));
        if (!same) {
            return String.join(" | ", outcomes);
        }
        if (kind0.startsWith("hung")) {
            return kind0;
        }
        List<Double> ts = outcomes.stream().map(o -> Double.parseDouble(o.replaceAll(".* ([0-9.]+) s$", "$1"))).sorted().toList();
        return ts.get(0).equals(ts.get(2)) ? String.format("%s %.1f s", kind0, ts.get(0))
                : String.format("%s %.1f-%.1f s", kind0, ts.get(0), ts.get(2));
    }

    // ============================================================================================ blast radius and the guard

    static final Hypothesis SLO = new Hypothesis("99% of menu requests succeed, p99 <= 300 ms", 0.99, 300, 20);

    record Scenario(String name, boolean timeouts, boolean both, int abortAfter) {
    }

    static void blast() throws Exception {
        System.out.println("two restaurant-service instances behind Toxiproxy; order-service alternates between them; 100 users/s");
        System.out.println("each user gives up after 1 s; fault: +3 s latency for 30 s; hypothesis: " + SLO.statement() + " (1 s windows)");
        List<Scenario> scenarios = List.of(
                new Scenario("no read timeout; fault on 1 of 2 instances; no guard", false, false, 0),
                new Scenario("no read timeout; fault on 1 of 2 instances; guard 3 windows", false, false, 3),
                new Scenario("no read timeout; fault on both instances; guard 3 windows", false, true, 3),
                new Scenario("read timeout 150 ms + retry the other instance; fault on 1 of 2; guard 3", true, false, 3));
        System.out.printf("%-76s %-9s %16s %22s %18s%n", "experiment", "verdict", "stopped after", "users hurt (> 300 ms", "recovered after");
        System.out.printf("%-76s %-9s %16s %22s %18s%n", "", "", "", "or failed) / all", "rollback");
        try (Lab.ToxiproxyServer tp = new Lab.ToxiproxyServer(toxiproxyBinary)) {
            Toxiproxy api = new Toxiproxy(tp.api());
            HttpServer a = Lab.restaurantService();
            HttpServer b = Lab.restaurantService();
            int n = 0;
            for (Scenario sc : scenarios) {
                String pa = "a" + n;
                String pb = "b" + n++;
                int portA = Lab.freePort();
                int portB = Lab.freePort();
                api.createProxy(pa, "127.0.0.1:" + portA, "127.0.0.1:" + a.getAddress().getPort());
                api.createProxy(pb, "127.0.0.1:" + portB, "127.0.0.1:" + b.getAddress().getPort());
                URI[] instances = {URI.create("http://127.0.0.1:" + portA + "/menu"), URI.create("http://127.0.0.1:" + portB + "/menu")};
                Client client = spring(springJdk(sc.timeouts() ? Duration.ofMillis(150) : null));
                Window.Recorder recorder = new Window.Recorder();
                AtomicLong injectedAt = new AtomicLong(Long.MAX_VALUE);
                AtomicLong rolledBackAt = new AtomicLong(Long.MAX_VALUE);
                AtomicLong hurt = new AtomicLong();
                AtomicLong inWindow = new AtomicLong();
                AtomicInteger next = new AtomicInteger();
                AtomicBoolean stop = new AtomicBoolean();
                ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();
                long start = System.nanoTime();
                Thread load = Lab.openLoop(start, 100, stop::get, at -> {
                    long t0 = System.nanoTime();
                    int first = Math.floorMod(next.getAndIncrement(), 2);
                    CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> {
                        try {
                            client.get(instances[first]);
                            return true;
                        } catch (Exception e) {
                            if (!sc.timeouts()) {
                                return false;
                            }
                            try {
                                client.get(instances[1 - first]);
                                return true;
                            } catch (Exception e2) {
                                return false;
                            }
                        }
                    }, calls);
                    boolean ok;
                    try {
                        ok = f.get(1, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        ok = false;   // the user gave up; the call is still open
                    }
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    recorder.record(ms, ok);
                    if (t0 >= injectedAt.get() && t0 < rolledBackAt.get()) {
                        inWindow.incrementAndGet();
                        if (!ok || ms > 300) {
                            hurt.incrementAndGet();
                        }
                    }
                });
                Experiment.Report r = Experiment.named(sc.name())
                        .hypothesis(SLO)
                        .method(() -> {
                            injectedAt.set(System.nanoTime());
                            api.add(pa, Toxic.latency(3000, 0));
                            if (sc.both()) {
                                api.add(pb, Toxic.latency(3000, 0));
                            }
                        }, () -> {
                            api.reset();
                            rolledBackAt.set(System.nanoTime());
                        })
                        .observe(() -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return recorder.close();
                        })
                        .baseline(5).duration(30).abortAfter(sc.abortAfter()).recoveryWithin(15)
                        .build().run();
                stop.set(true);
                load.join();
                Thread.sleep(1500);
                calls.shutdownNow();
                double stoppedAfter = (rolledBackAt.get() - injectedAt.get()) / 1e9;
                System.out.printf("%-76s %-9s %14.1f s %22s %15s%n", sc.name(), r.verdict(), stoppedAfter,
                        hurt.get() + " / " + inWindow.get(), r.recovered() ? r.windowsToRecover() + " s" : "no");
            }
            a.stop(0);
            b.stop(0);
        }
    }

    // ============================================================================================ breaker hypothesis

    static void breaker() throws Exception {
        System.out.println("menu requests at 100/s through a Resilience4j 2.4.0 circuit breaker (chapter 43's policy) with a cached-menu fallback");
        System.out.println("each user gives up after 1 s; fault for 20 s; hypothesis: 99% of users get a menu (fresh or cached), p99 <= 300 ms");
        record Case(String name, String fault, Duration readTimeout) {
        }
        List<Case> cases = List.of(
                new Case("black hole on responses only; no read timeout", "response black hole", null),
                new Case("black hole; no read timeout", "black hole", null),
                new Case("black hole; read timeout 250 ms", "black hole", Duration.ofMillis(250)),
                new Case("connection refused; no read timeout", "refused", null),
                new Case("connection refused; read timeout 250 ms", "refused", Duration.ofMillis(250)));
        System.out.printf("%-48s %-8s %16s %24s %22s %16s%n", "experiment", "verdict", "failing windows", "steady again after", "calls stuck open", "breaker opened");
        System.out.printf("%-48s %-8s %16s %24s %22s %16s%n", "", "", "(of 20)", "injection", "at rollback", "after");
        try (Lab.ToxiproxyServer tp = new Lab.ToxiproxyServer(toxiproxyBinary)) {
            Toxiproxy api = new Toxiproxy(tp.api());
            HttpServer up = Lab.restaurantService();
            int n = 0;
            for (Case c : cases) {
                String proxy = "m" + n++;
                int port = Lab.freePort();
                api.createProxy(proxy, "127.0.0.1:" + port, "127.0.0.1:" + up.getAddress().getPort());
                URI uri = URI.create("http://127.0.0.1:" + port + "/menu");
                Client client = spring(springJdk(c.readTimeout()));
                CircuitBreaker cb = CircuitBreaker.of(proxy, CircuitBreakerConfig.custom()
                        .slidingWindow(5, 20, SlidingWindowType.TIME_BASED).failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(1)).slowCallRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(5)).permittedNumberOfCallsInHalfOpenState(5).build());
                AtomicLong injectedAt = new AtomicLong(Long.MAX_VALUE);
                AtomicLong openedAt = new AtomicLong(-1);
                cb.getEventPublisher().onStateTransition(e -> {
                    if (e.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                        openedAt.compareAndSet(-1, System.nanoTime());
                    }
                });
                AtomicInteger open = new AtomicInteger();
                AtomicInteger openAtRollback = new AtomicInteger(-1);
                Window.Recorder recorder = new Window.Recorder();
                AtomicBoolean stop = new AtomicBoolean();
                ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();
                Thread load = Lab.openLoop(System.nanoTime(), 100, stop::get, at -> {
                    long t0 = System.nanoTime();
                    CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> {
                        open.incrementAndGet();
                        try {
                            cb.executeCheckedSupplier(() -> client.get(uri));
                            return true;
                        } catch (Throwable t) {
                            return true;   // the cached menu: still a menu for the user
                        } finally {
                            open.decrementAndGet();
                        }
                    }, calls);
                    boolean ok;
                    try {
                        ok = f.get(1, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        ok = false;
                    }
                    recorder.record((System.nanoTime() - t0) / 1_000_000, ok);
                });
                Experiment.Report r = Experiment.named(c.name())
                        .hypothesis(SLO)
                        .method(() -> {
                            injectedAt.set(System.nanoTime());
                            switch (c.fault()) {
                                case "refused" -> api.enabled(proxy, false);
                                case "response black hole" -> api.add(proxy, new Toxic("black-hole", "timeout", "downstream", 1.0, java.util.Map.of("timeout", 0L)));
                                default -> api.add(proxy, Toxic.blackHole());
                            }
                        }, () -> {
                            openAtRollback.set(open.get());
                            api.reset();
                        })
                        .observe(() -> {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return recorder.close();
                        })
                        .baseline(5).duration(20).abortAfter(0).recoveryWithin(15)
                        .build().run();
                stop.set(true);
                load.join();
                calls.shutdownNow();
                int steadyAgain = -1;
                List<Window> d = r.during();
                for (int i = 0; i < d.size(); i++) {
                    boolean restHolds = true;
                    for (int j = i; j < d.size(); j++) {
                        restHolds &= SLO.holds(d.get(j));
                    }
                    if (restHolds) {
                        steadyAgain = i;
                        break;
                    }
                }
                String opened = openedAt.get() < 0 ? "never" : String.format("%.1f s", (openedAt.get() - injectedAt.get()) / 1e9);
                System.out.printf("%-48s %-8s %16d %24s %22d %16s%n", c.name(), r.verdict(), r.failedWindows(),
                        steadyAgain < 0 ? "not within 20 s" : steadyAgain + " s", openAtRollback.get(), opened);
                api.reset();
            }
            up.stop(0);
        }
    }
}
