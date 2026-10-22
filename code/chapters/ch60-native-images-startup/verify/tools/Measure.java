import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chapter 60's experiment: the same menu-service started five ways, each in a container limited to 1 CPU and then to
 * 0.25 CPU (512 MB): on the JVM; on the JVM warmed up before it opens its port; with an AppCDS archive; with a JDK 25
 * AOT cache (trained through HTTP, and trained without it); as a GraalVM native image. For each: time from {@code docker run} to the first 200 (10 starts), memory,
 * the first 1,000 requests, and throughput over the first minute.
 * System properties: c60.images (prefix of the images built by build-libs.sh).
 */
public final class Measure {

    static final String PREFIX = System.getProperty("c60.images", "foodfleet/menu-service:ch60-");
    static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build();
    static final URI MENU = URI.create("http://127.0.0.1:18060/menu?r=7");

    record Variant(String name, String image, String env) {
    }

    public static void main(String[] args) throws Exception {
        List<Variant> variants = List.of(
                new Variant("JVM", "jvm", null),
                new Variant("JVM, warmed up before opening its port", "jvm", "WARMUP=3000"),
                new Variant("JVM + AppCDS archive", "cds", null),
                new Variant("JVM + AOT cache (JDK 25)", "aot", null),
                new Variant("JVM + AOT cache, trained without HTTP", "aot-core", null),
                new Variant("GraalVM native image", "native", null));
        System.out.println("image sizes: " + String.join(", ", variants.stream().map(Variant::image).distinct()
                .map(i -> i + " " + sh("docker", "image", "ls", PREFIX + i, "--format", "{{.Size}}").strip()).toList()));
        for (String cpus : args.length > 0 ? args : new String[] {"1", "0.25"}) {
            System.out.println();
            System.out.printf("== %s CPU, 512 MB%n", cpus);
            System.out.println("variant                                   first 200 after (median of 10, min-max)  memory idle / after load  "
                    + "next request / p50 of the next 99 / p50 of 900 more  requests/s at 0-10 s / 50-60 s");
            for (Variant v : variants) {
                List<Double> starts = new ArrayList<>();
                String first = "";
                for (int i = 0; i < 10; i++) {
                    long t = System.nanoTime();
                    String id = run(v, cpus);
                    waitUp(30);
                    starts.add((System.nanoTime() - t) / 1e6);
                    if (i == 0) {
                        first = profile(id);
                    }
                    sh("docker", "rm", "-f", id);
                }
                Collections.sort(starts);
                System.out.printf("%-41s %6.0f ms (%.0f-%.0f)%s%n", v.name(), starts.get(4) / 2 + starts.get(5) / 2, starts.getFirst(), starts.getLast(), first);
            }
        }
    }

    static String run(Variant v, String cpus) {
        List<String> cmd = new ArrayList<>(List.of("docker", "run", "-d", "--rm", "--cpus=" + cpus, "--memory=512m", "-p", "18060:8080"));
        if (v.env() != null) {
            cmd.addAll(List.of("-e", v.env()));
        }
        cmd.add(PREFIX + v.image());
        return sh(cmd.toArray(String[]::new)).strip();
    }

    static void waitUp(int seconds) throws InterruptedException {
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        while (System.nanoTime() < end) {
            try {
                if (HTTP.send(HttpRequest.newBuilder(MENU).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                Thread.sleep(10);
            }
        }
        throw new IllegalStateException("not up in " + seconds + " s");
    }

    /** Memory, then 1,000 sequential requests, then a minute of 4 concurrent clients, on a container that just started. */
    static String profile(String id) throws Exception {
        String idle = mem(id);
        List<Double> ms = new ArrayList<>();
        HttpClient one = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        int dropped = 0;
        for (int i = 0; i < 1000; i++) {
            long t = System.nanoTime();
            try {
                one.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18060/menu?r=" + (i % 500))).build(), HttpResponse.BodyHandlers.discarding());
            } catch (java.io.IOException e) {
                dropped++; // a keep-alive connection closed under us (docker's port proxy); the time still counts
            }
            ms.add((System.nanoTime() - t) / 1e6);
        }
        double firstReq = ms.getFirst();
        List<Double> early = new ArrayList<>(ms.subList(1, 100));
        List<Double> later = new ArrayList<>(ms.subList(100, 1000));
        Collections.sort(early);
        Collections.sort(later);
        long[] windows = new long[6];
        long t0 = System.nanoTime();
        long end = t0 + 60_000_000_000L;
        CountDownLatch done = new CountDownLatch(4);
        AtomicLong failures = new AtomicLong();
        for (int c = 0; c < 4; c++) {
            Thread.ofPlatform().start(() -> {
                HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
                int i = 0;
                while (System.nanoTime() < end) {
                    try {
                        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18060/menu?r=" + (i++ % 500))).build(), HttpResponse.BodyHandlers.discarding());
                        int w = (int) ((System.nanoTime() - t0) / 10_000_000_000L);
                        if (w < 6) {
                            synchronized (windows) {
                                windows[w]++;
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }
                done.countDown();
            });
        }
        done.await();
        String loaded = mem(id);
        return String.format("  %8s / %-14s %7.1f / %5.2f / %5.2f ms               %5.0f / %.0f%s", idle, loaded, firstReq, early.get(early.size() / 2),
                later.get(later.size() / 2), windows[0] / 10.0, windows[5] / 10.0, failures.get() > 0 ? "  (" + failures.get() + " failed)" : "") + (dropped > 0 ? "  (" + dropped + " dropped connections)" : "");
    }

    static String mem(String id) {
        return sh("docker", "stats", "--no-stream", "--format", "{{.MemUsage}}", id).strip().replaceAll(" / .*", "");
    }

    static String sh(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
