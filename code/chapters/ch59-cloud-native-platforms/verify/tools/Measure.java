import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch59.identity.TokenVerifier;
import dev.foodfleet.ch59.identity.TrustPolicy;
import dev.foodfleet.ch59.retry.RetryStrategy;
import dev.foodfleet.ch59.retry.Strategies;
import dev.foodfleet.ch59.serverless.CostModel;
import dev.foodfleet.ch59.serverless.FunctionPool;
import dev.foodfleet.ch59.serverless.Traffic;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chapter 59's experiments.
 * <ul>
 *   <li>{@code identity}: workload identity federation with k3s 1.37's service account tokens, checked offline the way a
 *       cloud token service does, against TokenReview; token lifetimes and the kubelet's refresh</li>
 *   <li>{@code throttling}: SDK clients against a rate-limited cloud API, with each AWS SDK retry mode: overload, then an
 *       outage</li>
 *   <li>{@code serverless}: a JVM service's cold start in a fresh container, measured; then a day of traffic replayed
 *       against a function platform and against containers, with list prices</li>
 * </ul>
 * System properties: c59.kubectl, c59.k8s (resources/k8s), c59.image (the menu-service image for cold starts).
 */
public final class Measure {

    static final String[] KUBECTL = System.getProperty("c59.kubectl", "kubectl").split(" ");
    static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "identity" -> identity();
                case "throttling" -> throttling();
                case "serverless" -> serverless();
                default -> throw new IllegalArgumentException(mode);
            }
        }
        System.exit(0);
    }

    // ===================================================================================================== helpers

    record Out(int code, String text) {
    }

    static Out sh(String stdin, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        if (stdin != null) {
            p.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Out(p.waitFor(), out.lines().filter(l -> !l.contains("DEPRECATION")).reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    static String kubectl(String stdin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(KUBECTL));
        cmd.addAll(Arrays.asList(args));
        Out o = sh(stdin, cmd.toArray(String[]::new));
        if (o.code() != 0 && !Arrays.asList(args).contains("--ignore-not-found")) {
            throw new IllegalStateException(String.join(" ", args) + ": " + o.text());
        }
        return o.text();
    }

    static String get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    static long now() {
        return System.currentTimeMillis() / 1000;
    }

    static String outcome(Supplier<String> s) {
        try {
            return s.get();
        } catch (SecurityException e) {
            return "REFUSED: " + e.getMessage();
        }
    }

    // ==================================================================================================== identity

    static void identity() throws Exception {
        String ns = "identity";
        kubectl(null, "delete", "namespace", ns, "--ignore-not-found", "--wait=true", "--timeout=120s");
        kubectl(null, "create", "namespace", ns);
        String yaml = Files.readString(Path.of(System.getProperty("c59.k8s")).resolve("workload-identity.yaml"));
        kubectl(yaml, "apply", "-n", ns, "-f", "-");
        kubectl(yaml.replace("name: menu-service", "name: reporting").replace("serviceAccountName: menu-service", "serviceAccountName: reporting")
                .replace("name: identity-menu", "name: identity-reporting"), "apply", "-n", ns, "-f", "-");
        kubectl(null, "-n", ns, "wait", "--for=condition=Ready", "pod/identity-menu", "pod/identity-reporting", "--timeout=120s");
        String issuer = extract(kubectl(null, "get", "--raw", "/.well-known/openid-configuration"), "\"issuer\":\"([^\"]+)\"");
        String jwks = kubectl(null, "get", "--raw", "/openid/v1/jwks");
        TokenVerifier verifier = new TokenVerifier(issuer, jwks);
        TrustPolicy policy = new TrustPolicy("menu-reader", "sts.foodfleet.local", Set.of(TrustPolicy.serviceAccount(ns, "menu-service")), Duration.ofHours(1));
        String menuIp = podIp(ns, "identity-menu");
        String reportingIp = podIp(ns, "identity-reporting");
        String menuSts = get("http://" + menuIp + ":8080/token?which=sts");
        String menuDefault = get("http://" + menuIp + ":8080/token?which=default");
        String reportingSts = get("http://" + reportingIp + ":8080/token?which=sts");
        System.out.println("issuer " + issuer + "; JWKS from the API server; role menu-reader trusts audience sts.foodfleet.local and " + policy.subjects());
        System.out.println("token presented                                          token service's answer");
        String tampered = menuSts.substring(0, menuSts.indexOf('.') + 1)
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(TokenVerifierAccess.payload(menuSts)
                .replace("\"sub\":\"system:serviceaccount:identity:menu-service\"", "\"sub\":\"system:serviceaccount:identity:menu-service\",\"x\":1")
                .getBytes(StandardCharsets.UTF_8)) + menuSts.substring(menuSts.lastIndexOf('.'));
        Object[][] cases = {
                {"menu-service, projected token for the token service", menuSts},
                {"menu-service, default token (audience: the API server)", menuDefault},
                {"reporting, projected token for the token service", reportingSts},
                {"menu-service's token with one claim edited", tampered}};
        for (Object[] c : cases) {
            System.out.printf("%-56s %s%n", c[0], outcome(() -> {
                TrustPolicy.Credentials cr = policy.assume(verifier, (String) c[1], now());
                return "credentials for " + cr.role() + ", valid " + (cr.expiresAt() - now()) / 60 + " min";
            }));
        }
        System.out.println();
        System.out.println("token lifetimes (exp - iat)");
        System.out.printf("   projected, expirationSeconds 600:  %d s%n", life(menuSts));
        System.out.printf("   default service account token:      %d s (%.0f days)%n", life(menuDefault), life(menuDefault) / 86400.0);
        System.out.println();
        // the pod goes away; its token doesn't
        String review = """
                {"apiVersion":"authentication.k8s.io/v1","kind":"TokenReview","spec":{"token":"%s","audiences":["sts.foodfleet.local"]}}
                """.formatted(menuSts);
        System.out.println("after the pod is deleted                                  offline check (cloud STS)        TokenReview (API server)");
        System.out.printf("%-56s %-32s %s%n", "while the pod runs", offline(verifier, menuSts), tokenReview(review));
        kubectl(null, "-n", ns, "delete", "pod", "identity-menu", "--wait=true", "--timeout=60s");
        long deleted = System.nanoTime();
        for (int at : new int[] {3, 30, 120}) {
            Thread.sleep(Math.max(0, at * 1000L - (System.nanoTime() - deleted) / 1_000_000));
            System.out.printf("%-56s %-32s %s%n", at + " s after the pod was deleted", offline(verifier, menuSts), tokenReview(review));
        }
        System.out.println();
        // rotation: a fresh pod, watched until the kubelet replaces its token
        kubectl(yaml, "apply", "-n", ns, "-f", "-");
        kubectl(null, "-n", ns, "wait", "--for=condition=Ready", "pod/identity-menu", "--timeout=120s");
        String ip = podIp(ns, "identity-menu");
        String first = get("http://" + ip + ":8080/token?which=sts");
        long firstExp = TokenVerifierAccess.exp(first);
        String rotations = "";
        for (int i = 0; i < 660 && rotations.isEmpty(); i += 5) {
            Thread.sleep(5000);
            rotations = get("http://" + ip + ":8080/rotations").trim();
        }
        String second = get("http://" + ip + ":8080/token?which=sts");
        System.out.printf("kubelet refresh of a 600 s token: replaced %s s after the pod started; the old token then had %d s left and still verified: %s%n",
                rotations.isEmpty() ? "(not within 11 min)" : rotations.split(" ")[0], firstExp - now(), offline(verifier, first));
        System.out.printf("new token: valid for %d s, and %s%n", life(second), offline(verifier, second));
        kubectl(null, "delete", "namespace", ns, "--wait=false");
        System.out.println();
    }

    static String offline(TokenVerifier v, String jwt) {
        return outcome(() -> {
            TokenVerifier.Claims c = v.verify(jwt, "sts.foodfleet.local", now());
            return "accepted (" + (c.expiresAt() - now()) + " s left)";
        });
    }

    static String tokenReview(String review) throws Exception {
        String r = kubectl(review, "create", "-o", "json", "-f", "-");
        Matcher m = Pattern.compile("\"authenticated\":\\s*(true|false)").matcher(r);
        Matcher e = Pattern.compile("\"error\":\\s*\"([^\"]+)\"").matcher(r);
        return (m.find() ? "authenticated: " + m.group(1) : "not authenticated") + (e.find() ? " (" + e.group(1) + ")" : "");
    }

    static long life(String jwt) {
        return TokenVerifierAccess.exp(jwt) - TokenVerifierAccess.iat(jwt);
    }

    static String podIp(String ns, String pod) throws Exception {
        return kubectl(null, "-n", ns, "get", "pod", pod, "-o", "jsonpath={.status.podIP}").trim();
    }

    static String extract(String s, String regex) {
        Matcher m = Pattern.compile(regex).matcher(s);
        if (!m.find()) {
            throw new IllegalStateException("no " + regex + " in " + s);
        }
        return m.group(1);
    }

    /** Reads claims without verifying, for reporting only. */
    static final class TokenVerifierAccess {
        static String payload(String jwt) {
            return new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
        }

        static long exp(String jwt) {
            return Long.parseLong(extract(payload(jwt), "\"exp\":(\\d+)"));
        }

        static long iat(String jwt) {
            return Long.parseLong(extract(payload(jwt), "\"iat\":(\\d+)"));
        }
    }

    // ================================================================================================== throttling

    /** A cloud API with a per-account limit: a token bucket, and 400 ThrottlingException when it's empty; or an outage. */
    static final class CloudApi {
        final double rate;
        double tokens;
        long last = System.nanoTime();
        volatile long outageFrom = Long.MAX_VALUE;
        volatile long outageTo = Long.MIN_VALUE;
        final AtomicLong accepted = new AtomicLong();
        final AtomicLong throttled = new AtomicLong();
        final AtomicLong unavailable = new AtomicLong();
        final ConcurrentLinkedQueue<Long> arrivals = new ConcurrentLinkedQueue<>();
        HttpServer server;

        CloudApi(double rate) {
            this.rate = rate;
            this.tokens = rate;
        }

        synchronized boolean take() {
            long n = System.nanoTime();
            tokens = Math.min(rate, tokens + (n - last) / 1e9 * rate);
            last = n;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        void start(int port) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 1024);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ex -> {
                long n = System.nanoTime();
                arrivals.add(n);
                int status;
                String body;
                if (n >= outageFrom && n < outageTo) {
                    status = 503;
                    body = "{\"__type\":\"ServiceUnavailable\"}";
                    unavailable.incrementAndGet();
                } else if (take()) {
                    status = 200;
                    body = "{\"SecretString\":\"...\"}";
                    accepted.incrementAndGet();
                } else {
                    status = 400;
                    body = "{\"__type\":\"ThrottlingException\",\"message\":\"Rate exceeded\"}";
                    throttled.incrementAndGet();
                }
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            server.start();
        }
    }

    record Call(double startS, double ms, boolean ok, int attempts) {
    }

    /** {@code clients} SDK clients (one per pod), each with {@code threads} threads calling at {@code perThread} calls/s. */
    static List<Call> drive(String url, Supplier<RetryStrategy> strategy, int clients, int threads, double perThread, int seconds) throws Exception {
        ConcurrentLinkedQueue<Call> calls = new ConcurrentLinkedQueue<>();
        long t0 = System.nanoTime();
        long end = t0 + seconds * 1_000_000_000L;
        CountDownLatch done = new CountDownLatch(clients * threads);
        for (int c = 0; c < clients; c++) {
            RetryStrategy s = strategy.get();
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            for (int th = 0; th < threads; th++) {
                long offset = (long) (Math.random() * 1e9 / perThread);
                Thread.ofVirtual().start(() -> {
                    try {
                        long next = t0 + offset;
                        while (true) {
                            long wait = next - System.nanoTime();
                            if (wait > 0) {
                                Thread.sleep(Duration.ofNanos(wait));
                            }
                            if (System.nanoTime() >= end) {
                                break;
                            }
                            long st = System.nanoTime();
                            int attempts = 0;
                            boolean ok = false;
                            boolean retried = false;
                            while (true) {
                                Duration hold = s.beforeAttempt((System.nanoTime() - t0) / 1e9);
                                if (!hold.isZero()) {
                                    Thread.sleep(hold);
                                }
                                attempts++;
                                int status;
                                try {
                                    status = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build(),
                                            HttpResponse.BodyHandlers.discarding()).statusCode();
                                } catch (Exception e) {
                                    status = 0;
                                }
                                double nowS = (System.nanoTime() - t0) / 1e9;
                                if (status == 200) {
                                    s.onSuccess(retried, nowS);
                                    ok = true;
                                    break;
                                }
                                Optional<Duration> backoff = s.onFailure(attempts, status == 400, nowS);
                                if (backoff.isEmpty()) {
                                    break;
                                }
                                retried = true;
                                Thread.sleep(backoff.get());
                            }
                            calls.add(new Call((st - t0) / 1e9, (System.nanoTime() - st) / 1e6, ok, attempts));
                            next += (long) (1e9 / perThread);
                            next = Math.max(next, System.nanoTime());          // a caller doesn't make up for lost time
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
        }
        done.await();
        return new ArrayList<>(calls);
    }

    static double pct(List<Double> sorted, double q) {
        return sorted.isEmpty() ? Double.NaN : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(q * sorted.size()) - 1));
    }

    static void throttling() throws Exception {
        List<Supplier<RetryStrategy>> strategies = List.of(Strategies::none, Strategies::legacy, Strategies::standard, Strategies::adaptive);
        System.out.println("a cloud API limited to 100 requests/s per account (400 ThrottlingException above it); 40 pods, each one SDK client with");
        System.out.println("2 threads calling 2.5 times a second: 200 calls/s wanted, for 60 s");
        System.out.println("retry mode    calls made  succeeded  failed   attempts/call  throttled responses  p50 / p99 call (ms)   API work, successes/s");
        int port = 18059;
        for (Supplier<RetryStrategy> s : strategies) {
            CloudApi api = new CloudApi(100);
            api.start(port);
            List<Call> calls = drive("http://127.0.0.1:" + port + "/secret", s, 40, 2, 2.5, 60);
            api.server.stop(0);
            port++;
            report(s.get().name(), calls, api.throttled.get(), api.accepted.get() / 60.0);
        }
        System.out.println();
        System.out.println("the same API, 4 pods, each one SDK client with 5 threads calling 3 times a second (60 calls/s, within the limit);");
        System.out.println("it answers 503 to everything from 20 s to 40 s");
        System.out.println("retry mode    attempts during the outage  failed calls  attempts/s: first 2 s, last 10 s");
        for (Supplier<RetryStrategy> s : strategies) {
            CloudApi api = new CloudApi(100);
            api.start(port);
            long t0 = System.nanoTime();
            api.outageFrom = t0 + 20_000_000_000L;
            api.outageTo = t0 + 40_000_000_000L;
            List<Call> calls = drive("http://127.0.0.1:" + port + "/secret", s, 4, 5, 3, 60);
            api.server.stop(0);
            port++;
            long during = api.arrivals.stream().filter(n -> n >= api.outageFrom && n < api.outageTo).count();
            long early = api.arrivals.stream().filter(n -> n >= api.outageFrom && n < api.outageFrom + 2_000_000_000L).count();
            long late = api.arrivals.stream().filter(n -> n >= api.outageTo - 10_000_000_000L && n < api.outageTo).count();
            System.out.printf("%-13s %-27d %-13d %-29s %s%n", s.get().name(), during, calls.stream().filter(c -> !c.ok()).count(),
                    String.format("%.0f, then %.0f", early / 2.0, late / 10.0), "");
        }
        System.out.println();
    }

    static void report(String name, List<Call> calls, long throttled, double acceptedPerSecond) {
        long ok = calls.stream().filter(Call::ok).count();
        double attempts = calls.stream().mapToInt(Call::attempts).average().orElse(0);
        List<Double> ms = calls.stream().filter(Call::ok).map(Call::ms).sorted().toList();
        System.out.printf("%-13s %-11d %-10d %-8d %-14.2f %-20d %6.0f / %-12.0f %.0f%n", name, calls.size(), ok, calls.size() - ok, attempts, throttled,
                pct(ms, 0.5), pct(ms, 0.99), acceptedPerSecond);
    }

    // ================================================================================================== serverless

    static void serverless() throws Exception {
        String image = System.getProperty("c59.image", "foodfleet/menu-service:ch54");
        System.out.println("cold start of menu-service (" + image + ": Java 25, Logback, Micrometer) in a fresh container, 1 CPU, 10 times:");
        List<Double> cold = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long t = System.nanoTime();
            String id = sh(null, "docker", "run", "-d", "--rm", "--cpus=1", "--memory=512m", "-p", "18080:8080", image).text().trim();
            boolean up = false;
            while (!up && System.nanoTime() - t < 30_000_000_000L) {
                try {
                    up = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/menu")).timeout(Duration.ofSeconds(1)).build(),
                            HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
                } catch (Exception e) {
                    Thread.sleep(20);
                }
            }
            cold.add((System.nanoTime() - t) / 1e6);
            List<Double> warm = new ArrayList<>();
            if (i == 0) {
                for (int k = 0; k < 200; k++) {
                    long w = System.nanoTime();
                    get("http://127.0.0.1:18080/menu");
                    warm.add((System.nanoTime() - w) / 1e6);
                }
                Collections.sort(warm);
                System.out.printf("   warm requests on the same container: p50 %.1f ms%n", pct(warm, 0.5));
            }
            sh(null, "docker", "rm", "-f", id);
        }
        Collections.sort(cold);
        double coldS = pct(cold, 0.5) / 1000;
        System.out.printf("   container start to first 200: median %.0f ms, min %.0f, max %.0f%n%n", pct(cold, 0.5), cold.getFirst(), cold.getLast());

        System.out.println("a day of traffic (night, lunch and dinner peaks) replayed against a function platform: one request per instance, 30 ms each,");
        System.out.printf("the measured %.2f s cold start, 1,769 MB functions (1 vCPU); and against Fargate tasks of 1 vCPU / 2 GB (list prices, us-east-1, 2026-10-15)%n", coldS);
        System.out.println("service (dinner peak)      requests/day  keep-warm  cold starts      peak instances  Lambda $/day  Fargate $/day: sized for the peak, always on / following load");
        for (double peak : new double[] {0.2, 5, 50, 200, 1000}) {
            double[] day = Traffic.day(peak, 59);
            double taskSeconds = taskSeconds(peak, 0.03, 2);
            int peakTasks = (int) Math.max(2, Math.ceil(peak * 0.03 / 0.5));
            for (double keep : new double[] {300, 900}) {
                FunctionPool.Result r = FunctionPool.replay(day, 0.03, coldS, keep, 0);
                System.out.printf("%-26s %-13d %-10s %6d (%5.2f%%)  %-15d %-13.2f %.2f (%d tasks) / %.2f%n", String.format("%.1f requests/s", peak), r.requests(),
                        (int) keep / 60 + " min", r.coldStarts(), r.coldStartShare() * 100, r.peakInstances(), CostModel.lambdaDay(r, 1.769),
                        CostModel.fargateDay(peakTasks, 1, 2), peakTasks, CostModel.fargateScaled(taskSeconds, 1, 2));
            }
        }
        System.out.println();
    }

    /** Task-seconds in a day for tasks kept at 50% CPU, never fewer than {@code min}. */
    static double taskSeconds(double peak, double cpuPerRequest, int min) {
        double s = 0;
        for (int m = 0; m < 1440; m++) {
            double rps = peak * Traffic.shape(m) / 1.02;
            s += Math.max(min, Math.ceil(rps * cpuPerRequest / 0.5)) * 60;
        }
        return s;
    }
}
