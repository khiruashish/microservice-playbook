import dev.foodfleet.ch48.metrics.MetricsPolicy;
import dev.foodfleet.ch48.observation.Checkout;
import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerHttpObservationDocumentation;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Chapter 48's experiments, on Micrometer 1.17.1 with its Prometheus registry (Prometheus client 1.7.0), Spring
 * Framework 7.0.9's HTTP observation conventions and Prometheus 3.15.0.
 * <ul>
 *   <li>{@code aggregate}: four pods, one of them slow; fleet p99 from client-side percentiles and from histograms</li>
 *   <li>{@code decay}: a 10-second latency spike, and how long each way of computing p99 keeps reporting it</li>
 *   <li>{@code cardinality}: Spring's uri tag from route templates, from raw paths and from concatenated client URIs</li>
 *   <li>{@code cost}: nanoseconds per recording for counters, timers, histograms, percentiles and Observations</li>
 *   <li>{@code gauge}: a gauge whose object is only weakly reachable</li>
 * </ul>
 */
public final class Measure {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        switch (args[0]) {
            case "aggregate" -> aggregate();
            case "decay" -> decay();
            case "cardinality" -> cardinality();
            case "cost" -> cost();
            case "gauge" -> gauge();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    /** Latency of one order: log-normal around {@code medianMs}. */
    static double latencyMs(Random r, double medianMs) {
        return medianMs * Math.exp(0.5 * r.nextGaussian());
    }

    static Timer summary(PrometheusMeterRegistry r, String name) {
        return Timer.builder(name).publishPercentiles(0.5, 0.99).register(r);
    }

    record Sample(long atMillis, int pod, double ms) {
    }

    // =========================================================================================================== aggregate

    static void aggregate() throws Exception {
        Lab.reset();
        String[] pods = {"order-a", "order-b", "order-c", "order-d"};
        double[] share = {0.3, 0.3, 0.3, 0.1};
        int n = pods.length;
        Timer[] summary = new Timer[n];
        Timer[] histogram = new Timer[n];
        Timer[] bounded = new Timer[n];
        Timer[] slo = new Timer[n];
        PrometheusMeterRegistry[] registries = new PrometheusMeterRegistry[n];
        Map<String, Integer> targets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            r.config().meterFilter(MetricsPolicy.boundedLatencyHistograms("orders.bounded"));
            registries[i] = r;
            summary[i] = summary(r, "orders.summary");
            histogram[i] = Timer.builder("orders.histogram").publishPercentileHistogram().register(r);
            bounded[i] = Timer.builder("orders.bounded").register(r);
            slo[i] = Timer.builder("orders.slo").serviceLevelObjectives(MetricsPolicy.HTTP_SLOS.toArray(Duration[]::new)).register(r);
            targets.put(pods[i], Lab.serve(r));
        }
        Process prom = Lab.prometheus(targets, "5s");
        List<Sample> samples = new ArrayList<>();
        Random rnd = new Random(48);
        int rate = 400;
        long runMs = 90_000;
        long start = System.currentTimeMillis();
        long next = System.nanoTime();
        while (System.currentTimeMillis() - start < runMs) {
            for (int k = 0; k < rate / 100; k++) {
                double u = rnd.nextDouble();
                int pod = u < 0.3 ? 0 : u < 0.6 ? 1 : u < 0.9 ? 2 : 3;
                double ms = latencyMs(rnd, 20);
                if (pod == 3 && rnd.nextDouble() < 0.2) {
                    ms += 300;                                       // the gray pod
                }
                Duration d = Duration.ofNanos((long) (ms * 1e6));
                summary[pod].record(d);
                histogram[pod].record(d);
                bounded[pod].record(d);
                slo[pod].record(d);
                samples.add(new Sample(System.currentTimeMillis(), pod, ms));
            }
            next += 10_000_000;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
        }
        long end = System.currentTimeMillis();
        Thread.sleep(6000);                                          // one more scrape
        double t = end / 1000.0;
        List<Double> all = new ArrayList<>();
        List<List<Double>> byPod = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byPod.add(new ArrayList<>());
        }
        for (Sample s : samples) {
            if (s.atMillis() >= end - 60_000) {
                all.add(s.ms());
                byPod.get(s.pod()).add(s.ms());
            }
        }
        System.out.printf("4 pods of order-service, %d orders/s for %d s; traffic 30/30/30/10%%; order-d adds 300 ms to 20%% of its orders%n",
                rate, runMs / 1000);
        System.out.println("each pod records every order in four timers; Prometheus scrapes every 5 s; queries over the last minute");
        System.out.println();
        System.out.println("pod        share   true p99   client p99 (summary)   p99 from its histogram");
        for (int i = 0; i < n; i++) {
            double clientP99 = Lab.query("orders_summary_seconds{quantile=\"0.99\",pod=\"" + pods[i] + "\"}", t);
            double histP99 = Lab.query("histogram_quantile(0.99, sum by (le) (rate(orders_bounded_seconds_bucket{pod=\""
                    + pods[i] + "\"}[1m])))", t);
            System.out.printf("%-10s %4.0f%% %9.0f ms %15.0f ms %21.0f ms%n", pods[i], 100.0 * byPod.get(i).size() / all.size(),
                    Lab.percentile(byPod.get(i), 99), clientP99 * 1000, histP99 * 1000);
        }
        System.out.println();
        System.out.println("fleet percentile computed as                                                     p99        p50");
        row("true (every order in the last minute)", Lab.percentile(all, 99), Lab.percentile(all, 50));
        row("avg of the pods' client-side percentiles", 1000 * Lab.query("avg(orders_summary_seconds{quantile=\"0.99\"})", t),
                1000 * Lab.query("avg(orders_summary_seconds{quantile=\"0.5\"})", t));
        row("max of the pods' client-side percentiles", 1000 * Lab.query("max(orders_summary_seconds{quantile=\"0.99\"})", t),
                1000 * Lab.query("max(orders_summary_seconds{quantile=\"0.5\"})", t));
        for (String[] h : new String[][] {{"orders_histogram", "default percentile histogram (1 ms - 30 s)"},
                {"orders_bounded", "bounded histogram (1 ms - 10 s + SLO buckets)"}, {"orders_slo", "SLO buckets only (100, 250, 500, 1000 ms)"}}) {
            row("histogram_quantile over summed buckets: " + h[1],
                    1000 * Lab.query("histogram_quantile(0.99, sum by (le) (rate(" + h[0] + "_seconds_bucket[1m])))", t),
                    1000 * Lab.query("histogram_quantile(0.5, sum by (le) (rate(" + h[0] + "_seconds_bucket[1m])))", t));
        }
        System.out.println();
        String scrape = registries[0].scrape();
        System.out.printf("series per pod: summary %d, default histogram %d, bounded histogram %d, SLO buckets only %d%n",
                count(scrape, "orders_summary_seconds"), count(scrape, "orders_histogram_seconds"),
                count(scrape, "orders_bounded_seconds"), count(scrape, "orders_slo_seconds"));
        double under = all.stream().filter(ms -> ms <= 250).count() / (double) all.size();
        double sloShare = Lab.query("sum(rate(orders_slo_seconds_bucket{le=\"0.25\"}[1m])) / sum(rate(orders_slo_seconds_count[1m]))", t);
        String[] around = boundsAround(scrape, "orders_histogram_seconds_bucket", 0.25);
        double lo = Lab.query("sum(rate(orders_histogram_seconds_bucket{le=\"" + around[0] + "\"}[1m])) / sum(rate(orders_histogram_seconds_count[1m]))", t);
        double hi = Lab.query("sum(rate(orders_histogram_seconds_bucket{le=\"" + around[1] + "\"}[1m])) / sum(rate(orders_histogram_seconds_count[1m]))", t);
        System.out.printf("orders within 250 ms: true %.2f%%; SLO bucket le=0.25: %.2f%%; the default histogram's nearest buckets: "
                        + "le=%s (%.2f%%) and le=%s (%.2f%%)%n", 100 * under, 100 * sloShare, around[0], 100 * lo, around[1], 100 * hi);
        Lab.stop(prom);
        System.out.println();
    }

    static void row(String label, double p99, double p50) {
        System.out.printf("%-76s %6.0f ms %7.1f ms%n", label, p99, p50);
    }

    static long count(String scrape, String prefix) {
        return scrape.lines().filter(l -> l.startsWith(prefix)).count();
    }

    static String[] boundsAround(String scrape, String bucket, double value) {
        String below = null;
        String above = null;
        for (String l : scrape.lines().filter(x -> x.startsWith(bucket)).toList()) {
            int i = l.indexOf("le=\"") + 4;
            String le = l.substring(i, l.indexOf('"', i));
            if (le.equals("+Inf")) {
                continue;
            }
            double v = Double.parseDouble(le);
            if (v <= value) {
                below = le;
            } else if (above == null) {
                above = le;
            }
        }
        return new String[] {below, above};
    }

    // =========================================================================================================== decay

    static void decay() throws Exception {
        Lab.reset();
        PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        r.config().meterFilter(MetricsPolicy.boundedLatencyHistograms("orders.bounded"));
        Timer summary = Timer.builder("orders.summary").publishPercentiles(0.99).register(r);
        Timer bounded = Timer.builder("orders.bounded").register(r);
        Process prom = Lab.prometheus(Map.of("order-a", Lab.serve(r)), "5s");
        Random rnd = new Random(49);
        int rate = 200;
        long runMs = 260_000;
        long spikeFrom = 30_000;
        long spikeTo = 40_000;
        List<Sample> samples = new ArrayList<>();
        List<double[]> client = new ArrayList<>();                    // t, client p99, client max
        long start = System.currentTimeMillis();
        long next = System.nanoTime();
        long nextSnapshot = 5_000;
        while (true) {
            long t = System.currentTimeMillis() - start;
            if (t >= runMs) {
                break;
            }
            for (int k = 0; k < rate / 100; k++) {
                double ms = t >= spikeFrom && t < spikeTo ? latencyMs(rnd, 600) : latencyMs(rnd, 20);
                Duration d = Duration.ofNanos((long) (ms * 1e6));
                summary.record(d);
                bounded.record(d);
                samples.add(new Sample(System.currentTimeMillis(), 0, ms));
            }
            if (t >= nextSnapshot) {
                double p99 = 0;
                for (ValueAtPercentile v : summary.takeSnapshot().percentileValues()) {
                    p99 = v.value(TimeUnit.MILLISECONDS);
                }
                client.add(new double[] {nextSnapshot, p99, summary.max(TimeUnit.MILLISECONDS)});
                nextSnapshot += 5_000;
            }
            next += 10_000_000;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
        }
        Thread.sleep(6000);
        System.out.printf("one pod, %d orders/s; every order takes ~600 ms from %d s to %d s, ~20 ms otherwise; Prometheus scrapes every 5 s%n",
                rate, spikeFrom / 1000, spikeTo / 1000);
        System.out.println("t (s)   true p99 (last 5 s)   client p99 (summary)   client max   rate[30s] histogram p99   rate[1m] histogram p99");
        Map<String, Double> backUnder = new LinkedHashMap<>();
        for (String k : List.of("true p99", "client p99 (summary)", "client max", "rate[30s] histogram p99", "rate[1m] histogram p99")) {
            backUnder.put(k, null);
        }
        for (double[] c : client) {
            long t = (long) c[0];
            double at = (start + t) / 1000.0;
            List<Double> recent = new ArrayList<>();
            for (Sample s : samples) {
                if (s.atMillis() >= start + t - 5_000 && s.atMillis() < start + t) {
                    recent.add(s.ms());
                }
            }
            double truth = Lab.percentile(recent, 99);
            double h30 = 1000 * Lab.query("histogram_quantile(0.99, rate(orders_bounded_seconds_bucket[30s]))", at);
            double h60 = 1000 * Lab.query("histogram_quantile(0.99, rate(orders_bounded_seconds_bucket[1m]))", at);
            if (t % 10_000 == 0) {
                System.out.printf("%5d %18.0f ms %19.0f ms %10.0f ms %22.0f ms %23.0f ms%n", t / 1000, truth, c[1], c[2], h30, h60);
            }
            if (t > spikeTo) {
                track(backUnder, "true p99", truth, t);
                track(backUnder, "client p99 (summary)", c[1], t);
                track(backUnder, "client max", c[2], t);
                track(backUnder, "rate[30s] histogram p99", h30, t);
                track(backUnder, "rate[1m] histogram p99", h60, t);
            }
        }
        System.out.println("-- first sample back under 100 ms after the spike ended at " + spikeTo / 1000 + " s:");
        backUnder.forEach((k, v) -> System.out.printf("   %-26s %s%n", k, v == null ? "not within the run"
                : String.format("%.0f s (%.0f s after)", v / 1000, (v - spikeTo) / 1000)));
        Lab.stop(prom);
        System.out.println();
    }

    static void track(Map<String, Double> backUnder, String key, double value, long t) {
        if (backUnder.get(key) == null && value < 100) {
            backUnder.put(key, (double) t);
        } else if (backUnder.get(key) != null && value >= 100) {
            backUnder.put(key, null);                                 // went back up: not settled yet
        }
    }

    // =========================================================================================================== cardinality

    static final String[][] ROUTES = {
            {"GET", "/orders/{id}", "/orders/%d", "40"},
            {"GET", "/restaurants/{id}/menu", "/restaurants/%d/menu", "30"},
            {"POST", "/orders", "/orders", "15"},
            {"GET", "/riders/{id}/location", "/riders/%d/location", "10"},
            {"GET", "/search", "/search", "5"}};

    /** A servlet request and response with just what Spring's convention reads, as a Spring MVC filter would see them. */
    static ServerRequestObservationContext serverContext(String method, String uri, int status) {
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(Measure.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getAttribute" -> null;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    default -> null;
                });
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(Measure.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (p, m, a) -> switch (m.getName()) {
                    case "getStatus" -> status;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    default -> null;
                });
        return new ServerRequestObservationContext(req, resp);
    }

    /** A team's "more detail" convention: the request path as the uri tag. */
    static final class RawPathConvention extends DefaultServerRequestObservationConvention {
        @Override
        protected KeyValue uri(ServerRequestObservationContext context) {
            return KeyValue.of("uri", context.getCarrier().getRequestURI());
        }
    }

    static final DefaultServerRequestObservationConvention SPRING_DEFAULT = new DefaultServerRequestObservationConvention();

    record Setup(String name, Consumer<PrometheusMeterRegistry> filters, Consumer<ObservationRegistry> traffic) {
    }

    static void cardinality() throws Exception {
        int requests = 10_000;
        List<Setup> setups = List.of(
                new Setup("server: Spring's convention (route template)", r -> { }, o -> server(o, requests, null, false)),
                new Setup("server: path as the uri tag", r -> { }, o -> server(o, requests, new RawPathConvention(), false)),
                new Setup("server: path as uri, capped at 100 values", r -> r.config().meterFilter(
                        MeterFilter.maximumAllowableTags("http.server.requests", "uri", 100, MeterFilter.deny())),
                        o -> server(o, requests, new RawPathConvention(), true)),
                new Setup("client: uri(\"/restaurants/\" + id + \"/menu\")", r -> { }, o -> client(o, requests, true)),
                new Setup("client: uri(\"/restaurants/{id}/menu\", id)", r -> { }, o -> client(o, requests, false)));
        System.out.printf("%,d requests through Spring Framework 7.0.9's HTTP observation conventions into a Micrometer Prometheus registry;%n", requests);
        System.out.println("routes: GET /orders/{id} 40%, GET /restaurants/{id}/menu 30%, POST /orders 15%, GET /riders/{id}/location 10%, "
                + "GET /search 5%; IDs random in 1..1,000,000");
        System.out.println();
        System.out.println("setup                                          histogram   meters      series   scrape size   scrape time   heap held");
        for (Setup s : setups) {
            for (boolean histograms : new boolean[] {false, true}) {
                long before = Lab.usedHeap();
                PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
                s.filters().accept(r);
                if (histograms) {
                    r.config().meterFilter(MetricsPolicy.boundedLatencyHistograms("http.server.requests", "http.client.requests"));
                }
                ObservationRegistry o = ObservationRegistry.create();
                o.observationConfig().observationHandler(new DefaultMeterObservationHandler(r));
                s.traffic().accept(o);
                String scrape = r.scrape();
                List<Double> times = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    long t0 = System.nanoTime();
                    scrape = r.scrape();
                    times.add((System.nanoTime() - t0) / 1e6);
                }
                long held = Lab.usedHeap() - before;
                System.out.printf("%-46s %9s %8d %11d %10.1f MB %10.0f ms %8.0f MB%n", s.name(), histograms ? "bounded" : "none",
                        r.getMeters().size(), Lab.series(scrape), scrape.length() / 1e6, Lab.percentile(times, 50), held / 1e6);
                if (s.name().contains("capped") && !histograms) {
                    long cancel = r.find("http.server.requests").tag("uri", "/orders/77/cancel").timers().size();
                    System.out.printf("   after the cap: a new endpoint POST /orders/{id}/cancel, 100 requests -> %d timers for it "
                            + "(denied once 100 uri values were seen)%n", cancel);
                }
                r.close();
            }
        }
        System.out.println();
    }

    static void server(ObservationRegistry o, int requests, DefaultServerRequestObservationConvention custom, boolean newRouteAfter) {
        Random rnd = new Random(50);
        for (int i = 0; i < requests; i++) {
            int pick = rnd.nextInt(100);
            int acc = 0;
            String[] route = ROUTES[0];
            for (String[] rt : ROUTES) {
                acc += Integer.parseInt(rt[3]);
                if (pick < acc) {
                    route = rt;
                    break;
                }
            }
            int status = rnd.nextInt(100) < 2 ? 404 : rnd.nextInt(100) < 1 ? 500 : 200;
            String path = String.format(route[2], 1 + rnd.nextInt(1_000_000));
            observeServer(o, custom, route[0], path, status == 404 ? null : route[1], status);
        }
        if (newRouteAfter) {
            for (int i = 0; i < 100; i++) {
                observeServer(o, custom, "POST", "/orders/77/cancel", "/orders/{id}/cancel", 200);
            }
        }
    }

    static void observeServer(ObservationRegistry o, DefaultServerRequestObservationConvention custom, String method, String path,
                              String pattern, int status) {
        ServerRequestObservationContext c = serverContext(method, path, status);
        c.setPathPattern(pattern);                                     // what Spring MVC sets for the matched handler
        Observation obs = ServerHttpObservationDocumentation.HTTP_SERVLET_SERVER_REQUESTS
                .observation(custom, SPRING_DEFAULT, () -> c, o).start();
        obs.stop();
    }

    static void client(ObservationRegistry o, int requests, boolean concatenated) {
        Random rnd = new Random(51);
        DefaultClientRequestObservationConvention convention = new DefaultClientRequestObservationConvention();
        for (int i = 0; i < requests; i++) {
            int id = 1 + rnd.nextInt(1_000_000);
            URI uri = URI.create("http://restaurant-service/restaurants/" + id + "/menu");
            ClientHttpRequest request = (ClientHttpRequest) Proxy.newProxyInstance(Measure.class.getClassLoader(),
                    new Class<?>[] {ClientHttpRequest.class}, (p, m, a) -> switch (m.getName()) {
                        case "getMethod" -> HttpMethod.GET;
                        case "getURI" -> uri;
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == a[0];
                        default -> null;
                    });
            ClientHttpResponse response = (ClientHttpResponse) Proxy.newProxyInstance(Measure.class.getClassLoader(),
                    new Class<?>[] {ClientHttpResponse.class}, (p, m, a) -> switch (m.getName()) {
                        case "getStatusCode" -> HttpStatusCode.valueOf(200);
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == a[0];
                        default -> null;
                    });
            ClientRequestObservationContext c = new ClientRequestObservationContext(request);
            // RestClient records the string given to uri(...) as the template (DefaultRestClient.uri)
            c.setUriTemplate(concatenated ? "http://restaurant-service/restaurants/" + id + "/menu"
                    : "http://restaurant-service/restaurants/{id}/menu");
            c.setResponse(response);
            ClientHttpObservationDocumentation.HTTP_CLIENT_EXCHANGES.observation(null, convention, () -> c, o).start().stop();
        }
    }

    // =========================================================================================================== cost

    interface Op {
        void run(int i);
    }

    static void cost() throws Exception {
        PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        r.config().meterFilter(MetricsPolicy.boundedLatencyHistograms("orders.bounded"));
        Counter counter = Counter.builder("orders.placed").tag("payment.method", "upi").register(r);
        Timer plain = Timer.builder("orders.plain").tag("route", "/orders").register(r);
        Timer bounded = Timer.builder("orders.bounded").tag("route", "/orders").register(r);
        Timer percentiles = Timer.builder("orders.percentiles").tag("route", "/orders").publishPercentiles(0.5, 0.95, 0.99).register(r);
        ObservationRegistry withLtt = ObservationRegistry.create();
        withLtt.observationConfig().observationHandler(new DefaultMeterObservationHandler(r));
        ObservationRegistry noLtt = ObservationRegistry.create();
        noLtt.observationConfig().observationHandler(new DefaultMeterObservationHandler(r,
                DefaultMeterObservationHandler.IgnoredMeters.LONG_TASK_TIMER));
        Checkout checkoutLtt = new Checkout(withLtt);
        Checkout checkoutNoLtt = new Checkout(noLtt);
        Duration d = Duration.ofMillis(12);
        Map<String, Op> ops = new LinkedHashMap<>();
        ops.put("Counter.increment()", i -> counter.increment());
        ops.put("Timer.record(), no histogram", i -> plain.record(d));
        ops.put("Timer.record(), bounded percentile histogram", i -> bounded.record(d));
        ops.put("Timer.record(), client-side percentiles 0.5/0.95/0.99", i -> percentiles.record(d));
        ops.put("Timer.builder(...).tags(3).register(r).record() per call", i -> Timer.builder("orders.lookup")
                .tags("route", "/orders", "method", "POST", "status", "201").register(r).record(d));
        ops.put("Observation (meter handler, long-task timer on)", i -> checkoutLtt.place("o" + (i & 1023), "r1", "upi", "metro",
                () -> Checkout.Result.PLACED));
        ops.put("Observation (meter handler, long-task timer off)", i -> checkoutNoLtt.place("o" + (i & 1023), "r1", "upi", "metro",
                () -> Checkout.Result.PLACED));
        System.out.println("nanoseconds per operation (median of 5 rounds after 2 warm-up rounds; 2 threads = one per vCPU)");
        System.out.println("operation                                                       1 thread     2 threads");
        for (Map.Entry<String, Op> e : ops.entrySet()) {
            System.out.printf("%-62s %9.0f %12.0f%n", e.getKey(), bench(e.getValue(), 1), bench(e.getValue(), 2));
        }
        System.out.println();
    }

    /** Wall-clock nanoseconds per operation per thread. */
    static double bench(Op op, int threads) throws InterruptedException {
        int perThread = 400_000;
        List<Double> rounds = new ArrayList<>();
        for (int round = 0; round < 7; round++) {
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                Thread.ofPlatform().start(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int i = 0; i < perThread; i++) {
                        op.run(i);
                    }
                    done.countDown();
                });
            }
            long t0 = System.nanoTime();
            go.countDown();
            done.await();
            double ns = (System.nanoTime() - t0) / (double) perThread;
            if (round >= 2) {
                rounds.add(ns);
            }
        }
        return Lab.percentile(rounds, 50);
    }

    // =========================================================================================================== gauge

    static void gauge() {
        PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AtomicInteger held = new AtomicInteger(42);
        r.gauge("queue.held", held);
        r.gauge("queue.weak", new AtomicInteger(42));                  // nothing else references this one
        Gauge.builder("queue.strong", new AtomicInteger(42), AtomicInteger::get).strongReference(true).register(r);
        System.out.println("gauge                                                   before GC    after GC");
        double[] before = {r.get("queue.held").gauge().value(), r.get("queue.weak").gauge().value(), r.get("queue.strong").gauge().value()};
        Lab.usedHeap();
        double[] after = {r.get("queue.held").gauge().value(), r.get("queue.weak").gauge().value(), r.get("queue.strong").gauge().value()};
        String[] names = {"registry.gauge(name, obj), obj kept in a field", "registry.gauge(name, new AtomicInteger(42))",
                "Gauge.builder(...).strongReference(true)"};
        for (int i = 0; i < 3; i++) {
            System.out.printf("%-54s %9.0f %11s%n", names[i], before[i], Double.isNaN(after[i]) ? "NaN" : String.format("%.0f", after[i]));
        }
        System.out.println("scrape after GC: " + r.scrape().lines().filter(l -> l.startsWith("queue_weak")).findFirst().orElse("(no line)"));
        java.lang.ref.Reference.reachabilityFence(held);
        System.out.println();
    }
}
