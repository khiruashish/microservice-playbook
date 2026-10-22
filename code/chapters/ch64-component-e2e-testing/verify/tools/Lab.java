import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch64.app.OrderApp;
import dev.foodfleet.ch64.app.Web;
import dev.foodfleet.ch64.e2e.Journeys;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chapter 64's experiments, on a real cluster.
 * <ul>
 *   <li>{@code environments}: complete test environments (PostgreSQL and three services) in namespaces: how long one
 *       takes to become ready and to delete, alone and four at once</li>
 *   <li>{@code staging}: four pipelines running the E2E suite at once against one shared staging environment, with
 *       fixed and with unique test data, and each in its own fresh environment</li>
 *   <li>{@code waits}: the asynchronous journey (an order gets a courier) with fixed sleeps and with polling</li>
 *   <li>{@code component}: the same journeys as component tests of order-service, against stubs, and as E2E</li>
 * </ul>
 * System properties: c64.kubectl, c64.manifest, c64.image (PostgreSQL image for component tests).
 */
public final class Lab {

    static final String KUBECTL = System.getProperty("c64.kubectl", "kubectl");
    static String manifest;

    public static void main(String[] args) throws Exception {
        // Deleted environments' cluster IPs are handed out again to new ones; a pooled keep-alive connection to an
        // old IP points at a pod that's gone. Drop idle connections after a second (the default is 30).
        System.setProperty("jdk.httpclient.keepalive.timeout", "1");
        manifest = Files.readString(Path.of(System.getProperty("c64.manifest")));
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "environments" -> environments();
                case "staging" -> staging();
                case "waits" -> waits();
                case "component" -> component();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------ environments

    record Timing(double readyS, double firstAnswerS) {
    }

    /** Applies the manifest into a new namespace and waits until order-service answers. */
    static Timing create(String ns) throws Exception {
        long t = System.nanoTime();
        kubectl(manifest.replace("NAMESPACE", ns), "apply", "-f", "-");
        kubectl(null, "-n", ns, "wait", "--for=condition=available", "deployment", "--all", "--timeout=300s");
        double ready = (System.nanoTime() - t) / 1e9;
        String url = url(ns);
        while (Web.call("GET", url + "/orders/0", null).status() != 404) {
            Thread.sleep(100);
            if ((System.nanoTime() - t) / 1e9 > 300) {
                throw new IllegalStateException(ns + " not answering");
            }
        }
        return new Timing(ready, (System.nanoTime() - t) / 1e9);
    }

    static double delete(String ns) throws Exception {
        long t = System.nanoTime();
        kubectl(null, "delete", "namespace", ns, "--wait=true", "--timeout=300s");
        return (System.nanoTime() - t) / 1e9;
    }

    static String url(String ns) throws Exception {
        return "http://" + kubectl(null, "-n", ns, "get", "svc", "order", "-o", "jsonpath={.spec.clusterIP}").strip();
    }

    static void environments() throws Exception {
        System.out.println("an environment: PostgreSQL 16, menu-, delivery- and order-service (Java 25), each a Deployment and Service, in a new namespace");
        System.out.println("environments created at once   all Deployments available   order-service answering   deleted (namespace gone)");
        for (int n : new int[] {1, 4}) {
            ExecutorService pool = Executors.newFixedThreadPool(n);
            List<Future<Timing>> created = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ns = "pr-" + n + "-" + i;
                created.add(pool.submit(() -> create(ns)));
            }
            List<Double> ready = new ArrayList<>();
            List<Double> answer = new ArrayList<>();
            for (Future<Timing> f : created) {
                ready.add(f.get().readyS());
                answer.add(f.get().firstAnswerS());
            }
            List<Future<Double>> deleted = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String ns = "pr-" + n + "-" + i;
                deleted.add(pool.submit(() -> delete(ns)));
            }
            List<Double> gone = new ArrayList<>();
            for (Future<Double> f : deleted) {
                gone.add(f.get());
            }
            pool.shutdown();
            System.out.printf("%-30d %-27s %-25s %s%n", n, range(ready), range(answer), range(gone));
        }
    }

    // ------------------------------------------------------------------ staging

    static void staging() throws Exception {
        create("staging");
        String staging = url("staging");
        System.out.println("four pipelines at once, each running the 5-journey E2E suite three times in a row (12 suite runs); polling for the courier");
        System.out.println("environment                                     suites passed   journeys failed   failures by journey");
        report("one shared staging, fixed test data", pipelines(4, 3, p -> staging, false));
        delete("staging");
        create("staging");
        String fresh = url("staging");
        report("one shared staging, unique test data per run", pipelines(4, 3, p -> fresh, true));
        delete("staging");
        // a fresh environment per pipeline run: 12 runs, 4 at a time
        List<List<Journeys.Outcome>> own = new ArrayList<>();
        List<Double> created = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Future<List<Journeys.Outcome>>> runs = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                String ns = "pr-" + round + "-" + p;
                runs.add(pool.submit(() -> {
                    long t = System.nanoTime();
                    create(ns);
                    synchronized (created) {
                        created.add((System.nanoTime() - t) / 1e9);
                    }
                    List<Journeys.Outcome> o = Journeys.run(url(ns), false, Journeys.Wait.poll(100, 10_000));
                    delete(ns);
                    return o;
                }));
            }
            for (Future<List<Journeys.Outcome>> f : runs) {
                own.add(f.get());
            }
            pool.shutdown();
        }
        report("a fresh environment per run, fixed test data", own);
        System.out.println("   (creating the fresh environments, 4 at a time: " + range(created) + " each)");
    }

    interface UrlFor {
        String url(int pipeline) throws Exception;
    }

    static List<List<Journeys.Outcome>> pipelines(int pipelines, int rounds, UrlFor url, boolean unique) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(pipelines);
        List<Future<List<List<Journeys.Outcome>>>> futures = new ArrayList<>();
        for (int p = 0; p < pipelines; p++) {
            int pipeline = p;
            futures.add(pool.submit(() -> {
                List<List<Journeys.Outcome>> mine = new ArrayList<>();
                for (int r = 0; r < rounds; r++) {
                    mine.add(Journeys.run(url.url(pipeline), unique, Journeys.Wait.poll(100, 10_000)));
                }
                return mine;
            }));
        }
        List<List<Journeys.Outcome>> all = new ArrayList<>();
        for (Future<List<List<Journeys.Outcome>>> f : futures) {
            all.addAll(f.get());
        }
        pool.shutdown();
        return all;
    }

    static void report(String name, List<List<Journeys.Outcome>> runs) {
        int passed = 0;
        int failed = 0;
        Map<String, Integer> byJourney = new LinkedHashMap<>();
        for (List<Journeys.Outcome> run : runs) {
            boolean ok = true;
            for (Journeys.Outcome o : run) {
                if (!o.passed()) {
                    ok = false;
                    failed++;
                    byJourney.merge(o.journey(), 1, Integer::sum);
                }
            }
            passed += ok ? 1 : 0;
        }
        System.out.printf("%-47s %-15s %-17s %s%n", name, passed + " of " + runs.size(), failed + " of " + runs.size() * 5, byJourney.isEmpty() ? "-" : byJourney);
    }

    // ------------------------------------------------------------------ waits

    static void waits() throws Exception {
        create("waits");
        String url = url("waits");
        int runs = 200;
        System.out.printf("'the order gets a courier', %d times per strategy, 8 at a time; courier assignment takes a log-normal delay, median 300 ms%n", runs);
        System.out.println("wait strategy                    failed           time per test (mean / max)");
        Map<String, Journeys.Wait> strategies = new LinkedHashMap<>();
        strategies.put("sleep 500 ms, then check", Journeys.Wait.sleep(500));
        strategies.put("sleep 1 s, then check", Journeys.Wait.sleep(1000));
        strategies.put("sleep 2 s, then check", Journeys.Wait.sleep(2000));
        strategies.put("poll every 100 ms, up to 10 s", Journeys.Wait.poll(100, 10_000));
        for (Map.Entry<String, Journeys.Wait> s : strategies.entrySet()) {
            ExecutorService pool = Executors.newFixedThreadPool(8);
            AtomicInteger failed = new AtomicInteger();
            List<Double> times = Collections.synchronizedList(new ArrayList<>());
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < runs; i++) {
                fs.add(pool.submit(() -> {
                    long t = System.nanoTime();
                    if (!Journeys.courier(url, s.getValue()).passed()) {
                        failed.incrementAndGet();
                    }
                    times.add((System.nanoTime() - t) / 1e6);
                    return null;
                }));
            }
            for (Future<?> f : fs) {
                f.get();
            }
            pool.shutdown();
            double mean = times.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double max = times.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            System.out.printf("%-32s %-16s %.0f / %.0f ms%n", s.getKey(), failed.get() + " of " + runs + String.format(" (%.1f%%)", 100.0 * failed.get() / runs), mean, max);
        }
        delete("waits");
    }

    // ------------------------------------------------------------------ component vs E2E

    /** The same five journeys: order-service in-process with stubs for menu and delivery and PostgreSQL in Docker, against a fresh environment. */
    static void component() throws Exception {
        System.out.println("the five journeys as component tests of order-service (stubbed menu- and delivery-service, PostgreSQL in Docker) and as E2E in a fresh environment");
        System.out.println("                          set-up          suite          total          journeys passed");
        for (int run = 0; run < 3; run++) {
            long t = System.nanoTime();
            String image = System.getProperty("c64.image", "c62/postgres:16");
            String id = sh("docker", "run", "-d", "--rm", "-p", "127.0.0.1::5432", image, "-c", "fsync=off").strip();
            String port = sh("docker", "port", id, "5432/tcp").strip().lines().findFirst().orElseThrow().replaceAll(".*:", "");
            String jdbc = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
            while (true) {
                try (Connection c = DriverManager.getConnection(jdbc, "postgres", "")) {
                    break;
                } catch (Exception e) {
                    Thread.sleep(50);
                }
            }
            HttpServer menu = menuStub();
            HttpServer delivery = deliveryStub();
            HttpServer order = OrderApp.start(0, "http://127.0.0.1:" + menu.getAddress().getPort(), "http://127.0.0.1:" + delivery.getAddress().getPort(),
                    OrderApp.jdbc(jdbc));
            double setup = (System.nanoTime() - t) / 1e9;
            long s = System.nanoTime();
            List<Journeys.Outcome> outcomes = Journeys.run("http://127.0.0.1:" + order.getAddress().getPort(), true, Journeys.Wait.poll(20, 10_000));
            double suite = (System.nanoTime() - s) / 1e9;
            order.stop(0);
            menu.stop(0);
            delivery.stop(0);
            sh("docker", "rm", "-f", id);
            System.out.printf("component, run %d          %6.2f s        %6.2f s        %6.2f s        %d of 5%n", run + 1, setup, suite, (System.nanoTime() - t) / 1e9,
                    outcomes.stream().filter(Journeys.Outcome::passed).count());
        }
        for (int run = 0; run < 3; run++) {
            long t = System.nanoTime();
            create("e2e-" + run);
            double setup = (System.nanoTime() - t) / 1e9;
            long s = System.nanoTime();
            List<Journeys.Outcome> outcomes = Journeys.run(url("e2e-" + run), true, Journeys.Wait.poll(100, 10_000));
            double suite = (System.nanoTime() - s) / 1e9;
            delete("e2e-" + run);
            System.out.printf("E2E, run %d                %6.2f s        %6.2f s        %6.2f s        %d of 5%n", run + 1, setup, suite, (System.nanoTime() - t) / 1e9,
                    outcomes.stream().filter(Journeys.Outcome::passed).count());
        }
    }

    /** menu-service as its contract describes it: two dishes, the biryani always available. */
    static HttpServer menuStub() throws Exception {
        return Web.serve(0, (method, path, body) -> {
            if (path.endsWith("/reserve")) {
                return Web.json(200, Map.of("reserved", true));
            }
            String price = path.endsWith("/paneer-tikka") ? "129.50" : path.endsWith("/dal-makhani") ? "189.00" : path.endsWith("/biryani-of-the-day") ? "349.00" : null;
            return price == null ? Web.json(404, Map.of("error", "no such item")) : Web.json(200, Map.of("price", price, "available", true));
        });
    }

    /** delivery-service as its contract describes it: accepted, then assigned at once. */
    static HttpServer deliveryStub() throws Exception {
        Map<String, String> seen = new java.util.concurrent.ConcurrentHashMap<>();
        return Web.serve(0, (method, path, body) -> {
            if (method.equals("POST")) {
                String order = String.valueOf(dev.foodfleet.ch64.json.Json.object(dev.foodfleet.ch64.json.Json.parse(body)).get("orderId"));
                seen.put(order, "COURIER_ASSIGNED");
                return Web.json(202, Map.of("orderId", order, "status", "PENDING"));
            }
            String order = path.substring(path.lastIndexOf('/') + 1);
            return seen.containsKey(order) ? Web.json(200, Map.of("orderId", order, "status", seen.get(order))) : Web.json(404, Map.of("error", "none"));
        });
    }

    // ------------------------------------------------------------------ helpers

    static String range(List<Double> v) {
        double min = Collections.min(v);
        double max = Collections.max(v);
        return v.size() == 1 ? String.format("%.1f s", min) : String.format("%.1f-%.1f s", min, max);
    }

    static String kubectl(String stdin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(KUBECTL.split(" ")));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(600, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException("kubectl " + String.join(" ", args) + ": " + err);
        }
        return out;
    }

    static String sh(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return out;
    }
}
