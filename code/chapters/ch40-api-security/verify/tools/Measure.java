import dev.foodfleet.ch40.cors.CorsPolicy;
import dev.foodfleet.ch40.ratelimit.FixedWindowLimiter;
import dev.foodfleet.ch40.ratelimit.GcraLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Caller;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Layer;
import dev.foodfleet.ch40.ratelimit.RateLimiter;
import dev.foodfleet.ch40.ratelimit.RedisGcraLimiter;
import dev.foodfleet.ch40.ratelimit.SlidingWindowLimiter;
import dev.foodfleet.ch40.validation.EmailAddress;
import dev.foodfleet.ch40.validation.InvalidInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Chapter 40 measurements. Modes: limiters, fairness, redos, cors (JDK only); redis (a Redis server at
 * 127.0.0.1:$REDIS_PORT, default 6390); tenancy (PostgreSQL, see run.sh).
 */
public final class Measure {

    static final long S = 1_000_000_000L;
    static final long MS = 1_000_000L;

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "limiters" -> limiters();
            case "fairness" -> fairness();
            case "redos" -> redos();
            case "cors" -> cors();
            case "redis" -> redis();
            case "tenancy" -> tenancy();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    // ---------------------------------------------------------------------------------------------- limiters

    /** Largest number of timestamps (sorted, nanos) inside any interval of the given length. */
    static int maxInAnyWindow(long[] t, int n, long window) {
        int best = 0;
        int lo = 0;
        for (int hi = 0; hi < n; hi++) {
            while (t[hi] - t[lo] >= window) {
                lo++;
            }
            best = Math.max(best, hi - lo + 1);
        }
        return best;
    }

    record Run(int allowed, int max60, int max1) {
    }

    static Run attack(RateLimiter limiter, long[] attempts) {
        long[] ok = new long[attempts.length];
        int n = 0;
        for (long t : attempts) {
            if (limiter.tryAcquire("client", t).allowed()) {
                ok[n++] = t;
            }
        }
        return new Run(n, maxInAnyWindow(ok, n, 60 * S), maxInAnyWindow(ok, n, S));
    }

    static void limiters() {
        Map<String, Supplier<RateLimiter>> strategies = new LinkedHashMap<>();
        strategies.put("fixed window", () -> new FixedWindowLimiter("p", 100, Duration.ofMinutes(1)));
        strategies.put("sliding window counter", () -> new SlidingWindowLimiter("p", 100, Duration.ofMinutes(1)));
        strategies.put("token bucket (GCRA), burst 100", () -> new GcraLimiter("p", 100, Duration.ofMinutes(1), 100));
        strategies.put("token bucket (GCRA), burst 10", () -> new GcraLimiter("p", 100, Duration.ofMinutes(1), 10));

        // a client retrying every 10 ms for 10 minutes, starting 30 s into a minute
        long start = 1_000 * 60 * S + 30 * S;
        long[] continuous = new long[60_000];
        for (int i = 0; i < continuous.length; i++) {
            continuous[i] = start + i * 10 * MS;
        }
        // a client that knows the windows: 200 requests in the 200 ms around each minute boundary, every 2 minutes
        List<Long> b = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            long boundary = 1_000 * 60 * S + (2L * k + 1) * 60 * S;
            for (int i = 0; i < 200; i++) {
                b.add(boundary - 100 * MS + i * MS);
            }
        }
        long[] boundary = b.stream().mapToLong(Long::longValue).toArray();

        System.out.println("policy: 100 requests per minute, one client");
        System.out.printf("%-32s %-34s %s%n", "", "retrying every 10 ms for 10 min", "bursts of 200 around minute boundaries");
        System.out.printf("%-32s %-10s %-12s %-12s %s%n", "limiter", "allowed", "max in 60 s", "max in 1 s", "max in 1 s");
        for (var e : strategies.entrySet()) {
            Run c = attack(e.getValue().get(), continuous);
            Run bd = attack(e.getValue().get(), boundary);
            System.out.printf("%-32s %-10d %-12d %-12d %d%n", e.getKey(), c.allowed(), c.max60(), c.max1(), bd.max1());
        }

        // cost of a decision, and memory per tracked key
        System.out.println();
        System.out.printf("%-32s %-22s %s%n", "limiter", "ns per decision", "bytes per tracked key");
        for (var e : strategies.entrySet()) {
            RateLimiter l = e.getValue().get();
            String[] keys = new String[10_000];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = "tenant/client-" + i;
            }
            for (int warm = 0; warm < 3; warm++) {
                long t0 = System.nanoTime();
                for (int i = 0; i < 5_000_000; i++) {
                    l.tryAcquire(keys[i % keys.length], i * 1_000L);
                }
                if (warm == 2) {
                    double ns = (System.nanoTime() - t0) / 5_000_000.0;
                    System.out.printf("%-32s %-22.0f %d%n", e.getKey(), ns, bytesPerKey(e.getValue()));
                }
            }
        }
    }

    static long bytesPerKey(Supplier<RateLimiter> factory) {
        RateLimiter l = factory.get();
        String[] keys = new String[500_000];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = ("tenant/client-" + i).intern();
        }
        long before = used();
        for (int i = 0; i < keys.length; i++) {
            l.tryAcquire(keys[i], 0);
        }
        long after = used();
        if (l.tryAcquire(keys[0], 0) == null) {
            throw new AssertionError();       // keep l reachable
        }
        return (after - before) / keys.length;
    }

    static long used() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    // ---------------------------------------------------------------------------------------------- fairness

    static void fairness() {
        // 10 simulated seconds. The platform can serve 1,000 requests a second. One tenant's POS integration
        // loops at 5,000 a second; four other tenants send 100 a second each, evenly.
        String[] quiet = {"dosa-co", "tiffin-box", "biryani-bros", "chai-point"};
        Map<String, String> strategies = new LinkedHashMap<>();
        strategies.put("global limit only", "global");
        strategies.put("per-tenant (250/s) + global", "tenant");
        strategies.put("per-client (100/s) + per-tenant + global", "client");
        System.out.println("capacity 1,000 req/s; spice-route's POS sync loops at 5,000 req/s; 4 tenants at 100 req/s each");
        System.out.printf("%-44s %-24s %s%n", "limits", "quiet tenants served", "spice-route served");
        for (var e : strategies.entrySet()) {
            List<Layer> layers = new ArrayList<>();
            if (!e.getValue().equals("global")) {
                if (e.getValue().equals("client")) {
                    layers.add(new Layer(new GcraLimiter("client", 100, Duration.ofSeconds(1), 20), c -> c.tenant() + "/" + c.client()));
                }
                layers.add(new Layer(new GcraLimiter("tenant", 250, Duration.ofSeconds(1), 50), Caller::tenant));
            }
            layers.add(new Layer(new GcraLimiter("global", 1000, Duration.ofSeconds(1), 100), c -> "platform"));
            LayeredLimiter l = new LayeredLimiter(layers);
            int quietSent = 0, quietOk = 0, noisySent = 0, noisyOk = 0;
            for (long us = 0; us < 10_000_000; us += 100) {          // 100 µs steps
                long t = us * 1000;
                if (us % 200 == 0) {                                   // 5,000/s
                    noisySent++;
                    noisyOk += l.tryAcquire(new Caller("spice-route", "pos-sync", "-"), t).decision().allowed() ? 1 : 0;
                }
                for (int q = 0; q < quiet.length; q++) {
                    if ((us + q * 2_500) % 10_000 == 0) {              // 100/s each, staggered
                        quietSent++;
                        quietOk += l.tryAcquire(new Caller(quiet[q], "web", "-"), t).decision().allowed() ? 1 : 0;
                    }
                }
            }
            System.out.printf("%-44s %-24s %s%n", e.getKey(), pct(quietOk, quietSent), pct(noisyOk, noisySent));
        }
    }

    static String pct(int ok, int sent) {
        return String.format("%.1f%% (%d of %d)", 100.0 * ok / sent, ok, sent);
    }

    // ---------------------------------------------------------------------------------------------- redos

    /** A CharSequence that gives up after a deadline, so a runaway regex can be stopped. */
    record Deadline(CharSequence s, long until) implements CharSequence {
        public int length() {
            return s.length();
        }

        public char charAt(int i) {
            if (System.nanoTime() > until) {
                throw new IllegalStateException("timeout");
            }
            return s.charAt(i);
        }

        public CharSequence subSequence(int a, int b) {
            return new Deadline(s.subSequence(a, b), until);
        }

        @Override
        public String toString() {
            return s.toString();
        }
    }

    static String time(Predicate<CharSequence> check, String input) {
        long limit = 10 * S;
        long t0 = System.nanoTime();
        try {
            check.test(new Deadline(input, t0 + limit));
        } catch (IllegalStateException e) {
            return "> 10 s";
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        return ms < 0.1 ? String.format("%.3f ms", ms) : ms < 10 ? String.format("%.2f ms", ms) : String.format("%.0f ms", ms);
    }

    static void redos() {
        System.out.println("java.util.regex on JDK " + Runtime.version().feature() + ": time to reject one input (cut off at 10 s)");
        System.out.println("input: n word characters, then '!' (after an opening quote for the quoted patterns)");
        record Case(String name, String regex, String prefix, boolean find) {
        }
        List<Case> exp = List.of(
                new Case("name: ^(\\w+\\s?)*$", "^(\\w+\\s?)*$", "", false),
                new Case("name, lazy: ^(\\w+?\\s?)*?$", "^(\\w+?\\s?)*?$", "", false),
                new Case("quoted name, backreference: ^(['\"])(?:\\w+\\s?)*\\1$", "^(['\"])(?:\\w+\\s?)*\\1$", "\"", false),
                new Case("quoted name, no backreference: ^['\"](?:\\w+\\s?)*['\"]$", "^['\"](?:\\w+\\s?)*['\"]$", "\"", false),
                new Case("a widely copied email regex", "^([a-zA-Z0-9])(([\\-.]|[_]+)?([a-zA-Z0-9]+))*(@){1}[a-z0-9]+[.]{1}(([a-z]{2,3})|([a-z]{2,3}[.]{1}[a-z]{2,3}))$", "", false));
        int[] ns = {16, 20, 24, 28};
        System.out.printf("%-58s %-10s %-10s %-10s %s%n", "pattern", "n=16", "n=20", "n=24", "n=28");
        for (Case c : exp) {
            Pattern p = Pattern.compile(c.regex());
            StringBuilder row = new StringBuilder(String.format("%-58s", c.name()));
            boolean gaveUp = false;
            for (int n : ns) {
                String r = gaveUp ? "-" : time(x -> p.matcher(x).matches(), c.prefix() + "a".repeat(n) + "!");
                gaveUp |= r.startsWith(">");
                row.append(String.format(" %-10s", r));
            }
            System.out.println(row);
        }
        StringBuilder row = new StringBuilder(String.format("%-58s", "EmailAddress.parse (length check, split, possessive)"));
        for (int n : ns) {
            row.append(String.format(" %-10s", time(x -> {
                try {
                    EmailAddress.parse("email", x.toString());
                    return true;
                } catch (InvalidInput e) {
                    return false;
                }
            }, "a".repeat(n) + "!")));
        }
        System.out.println(row);

        System.out.println();
        System.out.println("polynomial cases, input of n characters:");
        System.out.printf("%-58s %-10s %-10s %-10s %s%n", "pattern", "n=1,000", "n=5,000", "n=10,000", "n=20,000");
        record Poly(String name, Predicate<CharSequence> check, java.util.function.IntFunction<String> input) {
        }
        Pattern trailing = Pattern.compile("[\\s\\u200c]+$");
        Pattern csv = Pattern.compile("^(.*?,){11}P");
        for (Poly c : List.of(
                new Poly("trailing whitespace: [\\s\\u200c]+$ (find)", x -> trailing.matcher(x).find(), n -> "x" + " ".repeat(n - 2) + "x"),
                new Poly("String.strip()", x -> !x.toString().strip().isEmpty(), n -> "x" + " ".repeat(n - 2) + "x"),
                new Poly("CSV with 12 fields: ^(.*?,){11}P", x -> csv.matcher(x).find(), n -> "1,".repeat(n / 2)))) {
            StringBuilder r = new StringBuilder(String.format("%-58s", c.name()));
            boolean gaveUp = false;
            for (int n : new int[] {1_000, 5_000, 10_000, 20_000}) {
                String t = gaveUp ? "-" : time(c.check(), c.input().apply(n));
                gaveUp |= t.startsWith(">");
                r.append(String.format(" %-10s", t));
            }
            System.out.println(r);
        }
    }

    // ---------------------------------------------------------------------------------------------- cors

    /** How Spring's CorsConfiguration turns an allowedOriginPatterns entry into a regex (OriginPattern.initPattern). */
    static Pattern springOriginPattern(String p) {
        return Pattern.compile(("\\Q" + p + "\\E").replace("*", "\\E.*\\Q"));
    }

    static void cors() {
        CorsPolicy exact = CorsPolicy.of(Set.of("https://www.foodfleet.in", "https://partners.foodfleet.in"), Set.of("GET"),
                Set.of(), List.of(), true, Duration.ofMinutes(10));
        Map<String, Predicate<String>> matchers = new LinkedHashMap<>();
        matchers.put("reflect any Origin", o -> true);
        matchers.put("o.endsWith(\"foodfleet.in\")", o -> o.endsWith("foodfleet.in"));
        matchers.put("o.contains(\"foodfleet.in\")", o -> o.contains("foodfleet.in"));
        matchers.put("regex https://.*.foodfleet.in (dot unescaped)", o -> Pattern.matches("https://.*.foodfleet.in", o));
        matchers.put("Spring pattern https://*foodfleet.in", o -> springOriginPattern("https://*foodfleet.in").matcher(o).matches());
        matchers.put("Spring pattern https://*.foodfleet.in", o -> springOriginPattern("https://*.foodfleet.in").matcher(o).matches());
        matchers.put("allow-list incl. \"null\"", o -> o.equals("null") || exact.allowsOrigin(o));
        matchers.put("CorsPolicy (exact allow-list)", exact::allowsOrigin);
        Map<String, String> origins = new LinkedHashMap<>();
        origins.put("https://www.foodfleet.in", "ours");
        origins.put("https://partners.foodfleet.in", "ours");
        origins.put("https://evilfoodfleet.in", "attacker registers a domain");
        origins.put("https://www.foodfleet.in.attacker.example", "attacker's subdomain");
        origins.put("https://foodfleet-in.attacker.example", "attacker registers a domain");
        origins.put("https://wwwxfoodfleet.in", "attacker registers a domain");
        origins.put("https://reviews.foodfleet.in", "our subdomain hosting user content");
        origins.put("http://www.foodfleet.in", "downgraded to http (network attacker)");
        origins.put("null", "sandboxed iframe, file:, some redirects");
        System.out.println("which Origin values each matcher accepts (✔ = gets the response, with the user's cookies)");
        StringBuilder head = new StringBuilder(String.format("%-46s", "matcher"));
        int col = 1;
        for (String o : origins.keySet()) {
            head.append(String.format(" %-3s", col++));
        }
        System.out.println(head);
        for (var m : matchers.entrySet()) {
            StringBuilder row = new StringBuilder(String.format("%-46s", m.getKey()));
            int attacker = 0;
            for (var o : origins.entrySet()) {
                boolean ok = m.getValue().test(o.getKey());
                row.append(String.format(" %-3s", ok ? "✔" : "·"));
                if (ok && !o.getValue().equals("ours")) {
                    attacker++;
                }
            }
            row.append("  ").append(attacker).append(" not ours");
            System.out.println(row);
        }
        col = 1;
        for (var o : origins.entrySet()) {
            System.out.printf("  %d %-44s %s%n", col++, o.getKey(), o.getValue());
        }
    }

    // ---------------------------------------------------------------------------------------------- redis

    static int redisPort() {
        return Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6390"));
    }

    record Shot(int allowed, long[] latencies) {
    }

    /**
     * {@code instances} threads, each one gateway instance with its own limiter (and its own Redis connection
     * when shared), receiving its share of a client's {@code rate} requests per second for {@code seconds}.
     */
    static Shot drive(int instances, double[] share, int rate, int seconds, Supplier<RateLimiter> perInstance) throws Exception {
        AtomicInteger allowed = new AtomicInteger();
        ConcurrentLinkedQueue<Long> lat = new ConcurrentLinkedQueue<>();
        CountDownLatch done = new CountDownLatch(instances);
        long start = System.nanoTime() + 50 * MS;
        for (int i = 0; i < instances; i++) {
            RateLimiter limiter = perInstance.get();
            double r = rate * share[i];
            Thread.ofPlatform().start(() -> {
                try {
                    long interval = (long) (S / r);
                    long end = start + seconds * S;
                    for (long next = start; next < end; next += interval) {
                        long wait = next - System.nanoTime();
                        if (wait > 0) {
                            Thread.sleep(wait / MS, (int) (wait % MS));
                        }
                        long t0 = System.nanoTime();
                        boolean ok = limiter.tryAcquire("foodfleet-in/pos-sync", t0).allowed();
                        lat.add(System.nanoTime() - t0);
                        if (ok) {
                            allowed.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        return new Shot(allowed.get(), lat.stream().mapToLong(Long::longValue).sorted().toArray());
    }

    static Supplier<RateLimiter> shared(int limit, int burst, String name) {
        return () -> {
            try {
                Resp r = new Resp("127.0.0.1", redisPort());
                return new RedisGcraLimiter(name, limit, Duration.ofSeconds(1), burst, r::eval);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    static String us(long[] sorted, double q) {
        long v = sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))];
        return v < 10_000 ? String.format("%.1f µs", v / 1e3) : String.format("%.0f µs", v / 1e3);
    }

    static void redis() throws Exception {
        try (Resp r = new Resp("127.0.0.1", redisPort())) {
            System.out.println("Redis " + ((String) r.call("INFO", "server")).lines()
                    .filter(l -> l.startsWith("redis_version")).findFirst().orElse("?").split(":")[1]);
            r.call("FLUSHALL");
        }
        int seconds = 5;
        double[] even = {0.25, 0.25, 0.25, 0.25};
        double[] skewed = {0.70, 0.10, 0.10, 0.10};
        System.out.println("policy: 50 requests/s per client, burst 10; the client sends 400 requests/s for " + seconds
                + " s through 4 instances; at most " + (50 * seconds + 10) + " should get through");
        System.out.printf("%-46s %-16s %-16s %-12s %s%n", "where the limit lives", "allowed (even)", "allowed (70/10/10/10)", "p50", "p99");
        record Strategy(String name, Supplier<RateLimiter> factory) {
        }
        int k = 0;
        for (Strategy s : List.of(
                new Strategy("in each instance (50/s each)", () -> new GcraLimiter("l", 50, Duration.ofSeconds(1), 10)),
                new Strategy("in each instance, divided by 4 (fallback)", () -> new GcraLimiter("l", 12, Duration.ofSeconds(1), 2)),
                new Strategy("Redis, shared (gcra.lua)", null))) {
            k++;
            Supplier<RateLimiter> f = s.factory() != null ? s.factory() : shared(50, 10, "even" + k);
            Shot a = drive(4, even, 400, seconds, f);
            Supplier<RateLimiter> g = s.factory() != null ? s.factory() : shared(50, 10, "skew" + k);
            Shot b = drive(4, skewed, 400, seconds, g);
            System.out.printf("%-46s %-16d %-16d %-12s %s%n", s.name(), a.allowed(), b.allowed(), us(a.latencies(), 0.5), us(a.latencies(), 0.99));
        }

        // the same script back to back on one connection, no pacing: the cost of the round trip itself
        try (Resp r = new Resp("127.0.0.1", redisPort())) {
            RedisGcraLimiter l = new RedisGcraLimiter("b2b", 1_000_000, Duration.ofSeconds(1), 1000, r::eval);
            long[] t = new long[20_000];
            for (int round = 0; round < 2; round++) {
                for (int i = 0; i < t.length; i++) {
                    long t0 = System.nanoTime();
                    l.tryAcquire("client-" + (i % 1000), t0);
                    t[i] = System.nanoTime() - t0;
                }
            }
            Arrays.sort(t);
            System.out.printf("%-46s %-16s %-16s %-12s %s%n", "Redis, back to back on one connection", "", "", us(t, 0.5), us(t, 0.99));
        }

        // Redis unreachable: refused at once (process down) vs. a timeout (network partition)
        System.out.println();
        System.out.println("when Redis cannot be reached, time per rate-limit decision:");
        for (String[] target : new String[][] {{"connection refused (Redis down)", "127.0.0.1", "6399"},
                {"no answer (partition), 50 ms connect timeout", "10.255.255.1", "6379"}}) {
            long[] times = new long[20];
            for (int i = 0; i < times.length; i++) {
                long t0 = System.nanoTime();
                try (java.net.Socket sock = new java.net.Socket()) {
                    sock.connect(new java.net.InetSocketAddress(target[1], Integer.parseInt(target[2])), 50);
                } catch (IOException expected) {
                    // the limiter gets an exception: fall back, fail open, or fail closed
                }
                times[i] = System.nanoTime() - t0;
            }
            Arrays.sort(times);
            System.out.printf("  %-48s median %s%n", target[0], us(times, 0.5));
        }

        // Spring Cloud Gateway's RedisRateLimiter script, as shipped: Redis TIME in whole seconds
        String scgPath = System.getenv("SCG_SCRIPT");
        if (scgPath != null) {
            String scg = Files.readString(Path.of(scgPath));
            System.out.println();
            System.out.println("10 requests/s, burst 10; a client sending 100 requests/s for 5 s (after its first second):");
            System.out.printf("%-44s %-10s %-26s %s%n", "limiter", "allowed", "most allowed in any 100 ms", "allowed in a second's first 100 ms");
            try (Resp r = new Resp("127.0.0.1", redisPort())) {
                RateLimiter scgLimiter = new RateLimiter() {
                    public Decision tryAcquire(String key, long now) {
                        try {
                            List<Long> res = r.eval(scg, List.of("request_rate_limiter.{" + key + "}.tokens",
                                    "request_rate_limiter.{" + key + "}.timestamp"), List.of("10", "10", "", "1"));
                            return res.get(0) == 1 ? Decision.allow(res.get(1), Duration.ZERO) : Decision.deny(Duration.ZERO, Duration.ZERO);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    public Policy policy() {
                        return new Policy("scg", 10, Duration.ofSeconds(1));
                    }
                };
                pace("Spring Cloud Gateway RedisRateLimiter", scgLimiter);
                pace("gcra.lua (this chapter)", new RedisGcraLimiter("g", 10, Duration.ofSeconds(1), 10, r::eval));
            }
        }
    }

    static void pace(String name, RateLimiter limiter) throws InterruptedException {
        // align to a wall-clock second, as Redis TIME is
        long wallNs = System.currentTimeMillis() * MS;
        Thread.sleep((S - wallNs % S) / MS + 1);
        long offset = System.currentTimeMillis() * MS - System.nanoTime();
        List<Long> ok = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 0; i < 600; i++) {
            long next = start + i * 10 * MS;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                Thread.sleep(wait / MS, (int) (wait % MS));
            }
            long now = System.nanoTime();
            if (limiter.tryAcquire("pos-sync-" + name.length(), now).allowed() && now - start >= S) {
                ok.add(now + offset);
            }
        }
        long[] t = ok.stream().mapToLong(Long::longValue).toArray();
        int firstTenth = 0;
        for (long x : t) {
            if (x % S < 100 * MS) {
                firstTenth++;
            }
        }
        System.out.printf("%-44s %-10d %-26d %d of %d%n", name, t.length, maxInAnyWindow(t, t.length, 100 * MS), firstTenth, t.length);
    }

    // ---------------------------------------------------------------------------------------------- tenancy

    static Pg pg(String user) throws IOException {
        return new Pg(System.getenv().getOrDefault("APP_PGHOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("APP_PGPORT", "5432")), user,
                System.getenv(user.equals("c40_owner") ? "C40_OWNER_PASSWORD" : "C40_APP_PASSWORD"), "c40");
    }

    static String one(Pg c, String sql) throws IOException {
        try {
            List<List<String>> r = c.query(sql);
            return r.isEmpty() ? "(no rows)" : r.get(0).get(0);
        } catch (Pg.PgException e) {
            return "ERROR " + e.sqlState + (e.getMessage().contains("row-level security") ? " (row-level security)" : "");
        }
    }

    static void tenancy() throws Exception {
        // 50 tenants x 4,000 orders; customer ids repeat across tenants (cust-1 exists in every tenant)
        String countCust1 = "SELECT count(*) FROM %s WHERE customer_id = 'cust-1'";
        try (Pg owner = pg("c40_owner"); Pg app = pg("c40_app")) {
            System.out.println("orders: " + one(owner, "SELECT count(*) FROM orders_plain") + " rows, "
                    + one(owner, "SELECT count(DISTINCT tenant_id) FROM orders_plain") + " tenants; cust-1 has "
                    + one(owner, "SELECT count(*) FROM orders_plain WHERE customer_id = 'cust-1' AND tenant_id = 'tenant-07'")
                    + " orders in tenant-07");
            System.out.println();
            System.out.println("a query that forgets \"AND tenant_id = ?\", run for tenant-07 (rows returned for cust-1):");
            app.query("BEGIN");
            app.query("SELECT set_config('app.tenant_id', 'tenant-07', true)");
            System.out.printf("  %-58s %s%n", "no row-level security, connected as the app", one(app, countCust1.formatted("orders_plain")));
            System.out.printf("  %-58s %s%n", "row-level security, connected as the app", one(app, countCust1.formatted("orders")));
            app.query("COMMIT");
            owner.query("BEGIN");
            owner.query("SELECT set_config('app.tenant_id', 'tenant-07', true)");
            System.out.printf("  %-58s %s%n", "row-level security, as the table owner, not FORCEd", one(owner, countCust1.formatted("orders_noforce")));
            System.out.printf("  %-58s %s%n", "row-level security, as the table owner, FORCEd", one(owner, countCust1.formatted("orders")));
            owner.query("COMMIT");

            System.out.println();
            System.out.println("the tenant setting never set on this connection (a code path that skips it):");
            try (Pg fresh = pg("c40_app")) {
                System.out.printf("  %-58s %s%n", "policy uses current_setting('app.tenant_id')", one(fresh, "SELECT count(*) FROM orders_strict"));
                System.out.printf("  %-58s %s%n", "policy uses nullif(current_setting(.., true), '')", one(fresh, "SELECT count(*) FROM orders"));
            }
            System.out.println("the same, on a connection where an earlier transaction had set it locally (value now ''):");
            System.out.printf("  %-58s %s%n", "policy uses current_setting('app.tenant_id')", one(app, "SELECT count(*) FROM orders_strict"));
            System.out.printf("  %-58s %s%n", "policy uses nullif(current_setting(.., true), '')", one(app, "SELECT count(*) FROM orders"));

            System.out.println();
            System.out.println("one pooled connection, two requests: tenant-07's, then tenant-12's on a code path that skips setting the tenant");
            for (String[] how : new String[][] {{"session SET app.tenant_id (stays on the connection)", "SET app.tenant_id = 'tenant-07'"},
                    {"set_config(.., true) in the transaction (TenantTransactions)", "SELECT set_config('app.tenant_id', 'tenant-07', true)"}}) {
                try (Pg pooled = pg("c40_app")) {
                    pooled.query("BEGIN");
                    pooled.query(how[1]);
                    pooled.query("SELECT count(*) FROM orders");          // tenant-07's request
                    pooled.query("COMMIT");
                    // returned to the pool; the next borrower is tenant-12's request, which forgets the tenant
                    pooled.query("BEGIN");
                    String seen = one(pooled, "SELECT count(*) || ' rows of ' || coalesce(string_agg(DISTINCT tenant_id, ','), 'no tenant') FROM orders");
                    pooled.query("COMMIT");
                    System.out.printf("  %-62s tenant-12's request sees %s%n", how[0], seen);
                }
            }

            System.out.println();
            System.out.println("writing another tenant's row while scoped to tenant-07:");
            app.query("BEGIN");
            app.query("SELECT set_config('app.tenant_id', 'tenant-07', true)");
            System.out.printf("  %-58s %s%n", "INSERT ... tenant_id = 'tenant-12'",
                    one(app, "INSERT INTO orders (tenant_id, id, customer_id, total_paise) VALUES ('tenant-12', 999999, 'x', 1) RETURNING 'inserted'"));
            app.query("ROLLBACK");
            app.query("BEGIN");
            app.query("SELECT set_config('app.tenant_id', 'tenant-07', true)");
            System.out.printf("  %-58s %s%n", "UPDATE orders SET tenant_id = 'tenant-12' (moving a row out)",
                    one(app, "UPDATE orders SET tenant_id = 'tenant-12' WHERE id = 1 RETURNING 'updated'"));
            app.query("ROLLBACK");

            // cost: a single-order lookup and a per-customer list, with RLS vs an explicit predicate without RLS
            System.out.println();
            System.out.println("cost per query (client round trip, 3,000 queries each, after warm-up):");
            System.out.printf("  %-44s %-26s %s%n", "query", "explicit tenant predicate", "row-level security");
            String[][] queries = {
                    {"one order by id", "SELECT total_paise FROM orders_plain WHERE tenant_id = 'tenant-07' AND id = %d",
                            "SELECT total_paise FROM orders WHERE id = %d"},
                    {"a customer's orders (about 40 rows)", "SELECT id, total_paise FROM orders_plain WHERE tenant_id = 'tenant-07' AND customer_id = 'cust-%d' ORDER BY created_at DESC",
                            "SELECT id, total_paise FROM orders WHERE customer_id = 'cust-%d' ORDER BY created_at DESC"},
                    {"count a tenant's orders", "SELECT count(*) FROM orders_plain WHERE tenant_id = 'tenant-07' AND %d > 0",
                            "SELECT count(*) FROM orders WHERE %d > 0"}};
            for (String[] q : queries) {
                String a = timeQueries(app, q[1], false);
                String b = timeQueries(app, q[2], true);
                System.out.printf("  %-44s %-26s %s%n", q[0], a, b);
            }
            System.out.println();
            System.out.println("plans (EXPLAIN, row-level security):");
            app.query("BEGIN");
            app.query("SELECT set_config('app.tenant_id', 'tenant-07', true)");
            for (List<String> line : app.query("EXPLAIN SELECT total_paise FROM orders WHERE id = 42")) {
                System.out.println("  " + line.get(0));
            }
            app.query("COMMIT");
        }
    }

    static String timeQueries(Pg c, String sql, boolean rls) throws IOException {
        long[] t = new long[3000];
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < t.length; i++) {
                int arg = 1 + (i * 37) % 100;
                long t0 = System.nanoTime();
                if (rls) {
                    c.query("BEGIN; SELECT set_config('app.tenant_id', 'tenant-07', true); " + sql.formatted(arg) + "; COMMIT");
                } else {
                    c.query("BEGIN; " + sql.formatted(arg) + "; COMMIT");
                }
                t[i] = System.nanoTime() - t0;
            }
        }
        Arrays.sort(t);
        return "p50 " + us(t, 0.5) + ", p99 " + us(t, 0.99);
    }
}
