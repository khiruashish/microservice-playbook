import dev.foodfleet.ch68.partition.Analysis;
import dev.foodfleet.ch68.partition.Placement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Chapter 68's experiments.
 * <ul>
 *   <li>{@code sessions}: carts in memory or in PostgreSQL, behind a Service with and without ClientIP affinity, and
 *       what happens to them when a copy is replaced (cluster)</li>
 *   <li>{@code connections}: how a Service spreads requests that arrive on long-lived connections, and what a
 *       scale-out does for them (cluster)</li>
 *   <li>{@code scaleout}: throughput with 1, 2 and 4 copies, alone and behind a shared dependency (cluster)</li>
 *   <li>{@code partitioning}: hash mod n, consistent hashing, rendezvous and jump hashing; skewed keys (no cluster)</li>
 * </ul>
 * System properties: c68.kubectl, c68.manifest.
 */
public final class Lab {

    static final String KUBECTL = System.getProperty("c68.kubectl", "kubectl");
    static final String MANIFEST = System.getProperty("c68.manifest");

    public static void main(String[] args) throws Exception {
        try {
            for (String mode : args) {
                System.out.println("== " + mode);
                switch (mode) {
                    case "sessions" -> sessions();
                    case "connections" -> connections();
                    case "scaleout" -> scaleout();
                    case "partitioning" -> partitioning();
                    default -> throw new IllegalArgumentException(mode);
                }
                System.out.println();
            }
        } finally {
            if (Arrays.stream(args).anyMatch(a -> !a.equals("partitioning"))) {
                kubectl("delete", "namespace", "c68", "--wait=true", "--timeout=120s");
            }
        }
    }

    // ------------------------------------------------------------------ cluster helpers

    /** Applies the manifest with these settings and waits for every copy to be ready; returns the Service's address. */
    static String deploy(int replicas, String carts, String affinity, String ledger) throws Exception {
        String yaml = Files.readString(Path.of(MANIFEST)).replace("REPLICAS", String.valueOf(replicas)).replace("\"CARTS\"", "\"" + carts + "\"")
                .replace("AFFINITY", affinity).replace("\"LEDGER_URL\"", "\"" + ledger + "\"");
        Process p = new ProcessBuilder(cmd("apply", "-f", "-")).redirectErrorStream(true).start();
        p.getOutputStream().write(yaml.getBytes());
        p.getOutputStream().close();
        p.getInputStream().readAllBytes();
        p.waitFor();
        for (String d : List.of("postgres", "ledger", "order")) {
            kubectl("-n", "c68", "rollout", "status", "deploy/" + d, "--timeout=180s");
        }
        String ip = kubectl("-n", "c68", "get", "svc", "order", "-o", "jsonpath={.spec.clusterIP}").strip();
        String url = "http://" + ip;
        // wait until every copy has answered once
        HttpClient http = HttpClient.newHttpClient();
        for (int i = 0; i < 600 && pods().size() != replicas; i++) {
            Thread.sleep(200);
        }
        for (int i = 0; i < 200; i++) {
            try {
                if (http.send(fresh(url + "/ready"), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    break;
                }
            } catch (Exception e) {
                Thread.sleep(250);
            }
        }
        return url;
    }

    static List<String> pods() throws Exception {
        String out = kubectl("-n", "c68", "get", "pods", "-l", "app=order", "--field-selector=status.phase=Running", "-o",
                "jsonpath={range .items[*]}{.metadata.name} {.status.containerStatuses[0].ready} {.metadata.deletionTimestamp}{'\\n'}{end}");
        return out.lines().map(String::strip).filter(l -> l.endsWith("true")).map(l -> l.split(" ")[0]).toList();
    }

    static List<String> cmd(String... args) {
        List<String> c = new ArrayList<>(List.of(KUBECTL.split(" ")));
        c.addAll(List.of(args));
        return c;
    }

    static String kubectl(String... args) throws Exception {
        Process p = new ProcessBuilder(cmd(args)).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        return out;
    }

    /** A request on a connection of its own: the Service picks a copy for every request, as an ingress or a mesh would. */
    static HttpRequest fresh(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("Connection", "close").timeout(Duration.ofSeconds(10)).build();
    }

    static HttpRequest pooled(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build();
    }

    static String shares(Map<String, AtomicLong> perPod, List<String> order) {
        long total = perPod.values().stream().mapToLong(AtomicLong::get).sum();
        return order.stream().map(p -> String.format("%.0f%%", 100.0 * perPod.getOrDefault(p, new AtomicLong()).get() / Math.max(1, total)))
                .collect(Collectors.joining(" / "));
    }

    // ------------------------------------------------------------------ sessions

    static void sessions() throws Exception {
        System.out.println("3 copies of order-service; 200 users each add 5 items to their cart, then read it; every request on a new connection");
        System.out.printf("%-42s %-44s %16s %22s%n", "carts kept", "event", "carts intact", "requests per copy");
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        String[][] rows = {{"memory", "None", "no"}, {"memory", "ClientIP", "no"}, {"memory", "ClientIP", "yes"}, {"postgres", "None", "no"}, {"postgres", "None", "yes"}};
        for (String[] row : rows) {
            kubectl("delete", "namespace", "c68", "--wait=true", "--timeout=120s");
            String url = deploy(3, row[0], row[1], "");
            List<String> pods = pods();
            Map<String, AtomicLong> perPod = new ConcurrentHashMap<>();
            Map<String, AtomicLong> cartHome = new ConcurrentHashMap<>();
            List<String> users = IntStream.range(0, 200).mapToObj(i -> "user-" + i).toList();
            try (ExecutorService ex = Executors.newFixedThreadPool(16)) {
                List<Future<?>> fs = new ArrayList<>();
                for (String u : users) {
                    fs.add(ex.submit(() -> {
                        for (int k = 0; k < 5; k++) {
                            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url + "/cart/" + u)).POST(HttpRequest.BodyPublishers.noBody())
                                    .header("Connection", "close").timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                            String pod = r.headers().firstValue("X-Pod").orElse("?");
                            perPod.computeIfAbsent(pod, x -> new AtomicLong()).incrementAndGet();
                            cartHome.computeIfAbsent(pod, x -> new AtomicLong()).incrementAndGet();
                        }
                        return null;
                    }));
                }
                for (Future<?> f : fs) {
                    f.get();
                }
            }
            String event = "-";
            if (row[2].equals("yes")) {
                String busiest = cartHome.entrySet().stream().max(Map.Entry.comparingByValue((a, b) -> Long.compare(a.get(), b.get()))).orElseThrow().getKey();
                kubectl("-n", "c68", "delete", "pod", busiest, "--wait=true");
                for (int i = 0; i < 600 && pods().size() != 3; i++) {
                    Thread.sleep(200);
                }
                Thread.sleep(2000);
                event = "the copy that served most requests replaced";
            }
            AtomicInteger intact = new AtomicInteger();
            try (ExecutorService ex = Executors.newFixedThreadPool(16)) {
                List<Future<?>> fs = new ArrayList<>();
                for (String u : users) {
                    fs.add(ex.submit(() -> {
                        HttpResponse<String> r = http.send(fresh(url + "/cart/" + u), HttpResponse.BodyHandlers.ofString());
                        if (r.statusCode() == 200 && r.body().equals("5")) {
                            intact.incrementAndGet();
                        }
                        return null;
                    }));
                }
                for (Future<?> f : fs) {
                    f.get();
                }
            }
            String label = (row[0].equals("memory") ? "in memory" : "in PostgreSQL") + (row[1].equals("ClientIP") ? ", ClientIP affinity" : ", no affinity");
            System.out.printf("%-42s %-44s %8d of 200 %22s%n", label, event, intact.get(), shares(perPod, pods.stream().sorted().toList()));
        }
    }

    // ------------------------------------------------------------------ connections

    static void connections() throws Exception {
        System.out.println("order-service behind a Service (kube-proxy); 8 requests at a time, 4,000 per row; requests counted per copy");
        System.out.printf("%-58s %-12s %-28s %s%n", "client", "copies", "requests per copy", "busiest / quietest");
        for (boolean keepAlive : List.of(true, false)) {
            kubectl("delete", "namespace", "c68", "--wait=true", "--timeout=120s");
            String url = deploy(4, "memory", "None", "");
            Map<String, AtomicLong> perPod = drive(url, keepAlive, 4000, null);
            row(keepAlive ? "one HTTP client, keep-alive (connections reused)" : "a new connection per request", "4", perPod, pods());
        }
        for (boolean keepAlive : List.of(true, false)) {
            kubectl("delete", "namespace", "c68", "--wait=true", "--timeout=120s");
            String url = deploy(2, "memory", "None", "");
            HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
            drive(url, keepAlive, 2000, http);
            List<String> before = pods();
            kubectl("-n", "c68", "scale", "deploy/order", "--replicas=4");
            kubectl("-n", "c68", "rollout", "status", "deploy/order", "--timeout=180s");
            for (int i = 0; i < 600 && pods().size() != 4; i++) {
                Thread.sleep(200);
            }
            Map<String, AtomicLong> after = drive(url, keepAlive, 4000, http);
            List<String> all = new ArrayList<>(before);
            pods().stream().filter(p -> !before.contains(p)).sorted().forEach(all::add);
            row((keepAlive ? "keep-alive" : "new connection per request") + ", after scaling 2 -> 4 (old copies first)", "2 -> 4", after, all);
        }
    }

    static void row(String label, String copies, Map<String, AtomicLong> perPod, List<String> pods) {
        long max = pods.stream().mapToLong(p -> perPod.getOrDefault(p, new AtomicLong()).get()).max().orElse(0);
        long min = pods.stream().mapToLong(p -> perPod.getOrDefault(p, new AtomicLong()).get()).min().orElse(0);
        System.out.printf("%-58s %-12s %-28s %s%n", label, copies, shares(perPod, pods), min == 0 ? "a copy got nothing" : String.format("%.1fx", (double) max / min));
    }

    static Map<String, AtomicLong> drive(String url, boolean keepAlive, int requests, HttpClient shared) throws Exception {
        HttpClient http = shared != null ? shared : HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        Map<String, AtomicLong> perPod = new ConcurrentHashMap<>();
        AtomicInteger next = new AtomicInteger();
        try (ExecutorService ex = Executors.newFixedThreadPool(8)) {
            List<Future<?>> fs = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                fs.add(ex.submit(() -> {
                    while (next.getAndIncrement() < requests) {
                        HttpResponse<Void> r = http.send(keepAlive ? pooled(url + "/cart/x") : fresh(url + "/cart/x"), HttpResponse.BodyHandlers.discarding());
                        perPod.computeIfAbsent(r.headers().firstValue("X-Pod").orElse("?"), x -> new AtomicLong()).incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> f : fs) {
                f.get();
            }
        }
        return perPod;
    }

    // ------------------------------------------------------------------ scale-out

    static void scaleout() throws Exception {
        System.out.println("GET /work: about 5 ms of CPU per request (7 with HTTP on a new connection); each copy limited to 0.25 CPU;");
        System.out.println("32 requests at a time for 20 s after 5 s of warm-up; the ledger: 2 calls at a time, 40 ms each (at most 50 a second), shared by every copy");
        System.out.printf("%-26s %7s %12s %10s %10s %14s%n", "dependency", "copies", "requests/s", "p50 ms", "p99 ms", "per copy/s");
        for (String ledger : List.of("", "http://ledger.c68")) {
            for (int replicas : List.of(1, 2, 4)) {
                kubectl("delete", "namespace", "c68", "--wait=true", "--timeout=120s");
                String url = deploy(replicas, "memory", "None", ledger);
                closed(url + "/work", 32, 5);
                long[] lat = closed(url + "/work", 32, 20);
                Arrays.sort(lat);
                double rps = lat.length / 20.0;
                System.out.printf("%-26s %7d %12.0f %10.1f %10.1f %14.0f%n", ledger.isEmpty() ? "none (CPU only)" : "the shared ledger", replicas, rps,
                        lat[lat.length / 2] / 1000.0, lat[(int) (lat.length * 0.99)] / 1000.0, rps / replicas);
            }
        }
    }

    /** {@code workers} requests always in flight for {@code seconds}; returns each latency in microseconds. */
    static long[] closed(String url, int workers, int seconds) throws Exception {
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        List<long[]> all = new ArrayList<>();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<long[]>> fs = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                fs.add(ex.submit(() -> {
                    long[] mine = new long[100_000];
                    int n = 0;
                    while (System.nanoTime() < end) {
                        long t = System.nanoTime();
                        try {
                            http.send(fresh(url), HttpResponse.BodyHandlers.discarding());
                            mine[n++] = (System.nanoTime() - t) / 1000;
                        } catch (Exception e) {
                            // counted by absence
                        }
                    }
                    return Arrays.copyOf(mine, n);
                }));
            }
            for (Future<long[]> f : fs) {
                all.add(f.get());
            }
        }
        return all.stream().flatMapToLong(Arrays::stream).toArray();
    }

    // ------------------------------------------------------------------ partitioning

    static void partitioning() {
        List<String> keys = IntStream.range(0, 100_000).mapToObj(i -> "order-" + i).toList();
        List<Placement> placements = List.of(new Placement.Modulo(), new Placement.Ring(1), new Placement.Ring(16), new Placement.Ring(256),
                new Placement.Rendezvous(), new Placement.Jump());
        System.out.println("100,000 order IDs; keys that move when nodes are added or removed (the ideal is 1/new count when adding), and spread over 10 nodes");
        System.out.printf("%-42s %10s %10s %10s %16s %14s%n", "placement", "4 -> 5", "10 -> 11", "10 -> 9", "busiest / mean", "lookup ns");
        for (Placement p : placements) {
            double a = Analysis.moved(p, keys, 4, 5);
            double b = Analysis.moved(p, keys, 10, 11);
            double c = Analysis.moved(p, keys, 10, 9);
            double spread = Analysis.maxOverMean(Analysis.spread(p, keys, 10));
            for (int i = 0; i < 3; i++) {
                lookupNanos(p, keys, 10);
            }
            System.out.printf("%-42s %9.1f%% %9.1f%% %9.1f%% %16.2f %14.0f%n", p.name(), 100 * a, 100 * b, 100 * c, spread, lookupNanos(p, keys, 10));
        }
        System.out.println("(ideal: 20.0% for 4 -> 5, 9.1% for 10 -> 11, 10.0% for 10 -> 9; jump hash removes only the last node)");
        System.out.println();

        System.out.println("1,000,000 orders from 2,000 restaurants whose popularity follows Zipf's law; 12 partitions (a Kafka topic, say); ideal share 8.3%");
        System.out.printf("%-48s %-6s %14s %22s%n", "partition key", "Zipf s", "busiest share", "busiest / mean");
        for (double s : new double[] {0.8, 1.0, 1.2}) {
            Analysis.Zipf zipf = new Analysis.Zipf(2000, s);
            SplittableRandom r = new SplittableRandom(7);
            long[] byRestaurant = new long[12];
            long[] byOrder = new long[12];
            long[] byRestaurantSalted = new long[12];
            Placement mod = new Placement.Modulo();
            for (int i = 0; i < 1_000_000; i++) {
                int restaurant = zipf.next(r);
                byRestaurant[mod.owner("restaurant-" + restaurant, 12)]++;
                byOrder[mod.owner("order-" + i, 12)]++;
                // hot restaurants (the top 10) are spread over 4 sub-keys; the rest keep one partition each
                String salted = restaurant < 10 ? "restaurant-" + restaurant + "#" + (i % 4) : "restaurant-" + restaurant;
                byRestaurantSalted[mod.owner(salted, 12)]++;
            }
            for (Object[] row : new Object[][] {{"restaurant ID (orders of a restaurant in order)", byRestaurant}, {"order ID (no order per restaurant)", byOrder},
                    {"restaurant ID, top 10 salted over 4 sub-keys", byRestaurantSalted}}) {
                long[] counts = (long[]) row[1];
                System.out.printf("%-48s %-6.1f %13.1f%% %22.2f%n", row[0], s, 100.0 * Arrays.stream(counts).max().orElse(0) / 1_000_000, Analysis.maxOverMean(counts));
            }
            System.out.printf("%-48s %-6.1f %13.1f%%%n", "  (the single most popular restaurant's share)", s, 100 * zipf.top());
        }
    }

    static double lookupNanos(Placement p, List<String> keys, int n) {
        long t = System.nanoTime();
        long sink = 0;
        for (String k : keys) {
            sink += p.owner(k, n);
        }
        if (sink == 42) {
            System.out.print("");
        }
        return (System.nanoTime() - t) / (double) keys.size();
    }
}
