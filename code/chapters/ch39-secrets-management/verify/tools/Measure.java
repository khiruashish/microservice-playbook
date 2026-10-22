import com.sun.management.HotSpotDiagnosticMXBean;
import dev.foodfleet.ch39.secret.MountedSecrets;
import dev.foodfleet.ch39.secret.Secret;
import dev.foodfleet.ch39.vault.DatabaseCredentials;
import dev.foodfleet.ch39.vault.VaultClient;
import dev.foodfleet.ch39.vault.VaultClient.DatabaseLease;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chapter 39 measurements.
 *   leaks        where a secret ends up: environment variables, child processes, heap dumps (JDK only)
 *   delivery     a Kubernetes-style mounted secret changing under three kinds of reader (JDK only)
 *   static       rotating a shared database password in place vs with two accounts (PostgreSQL)
 *   dynamic      OpenBao dynamic credentials: renew-only vs renew-then-rotate, cost of issuing (OpenBao + PostgreSQL)
 *   revocation   what revoking, rotating or disabling a credential does to sessions already open (OpenBao + PostgreSQL)
 * Environment for the database experiments (run.sh sets it up): BAO_ADDR, BAO_TOKEN, APP_PGHOST, APP_PGPORT,
 * C39_DB, and the static accounts' names in C39_STATIC_* (see run.sh).
 */
public class Measure {

    static final String DB = env("C39_DB", "c39");
    static final String HOST = env("APP_PGHOST", "127.0.0.1");
    static final int PORT = Integer.parseInt(env("APP_PGPORT", "5432"));
    static final String QUERY = "select count(*) from orders.orders";

    static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? d : v;
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "leaks" -> leaks(args.length > 1 ? args[1] : null);
            case "delivery" -> delivery();
            case "static" -> staticRotation();
            case "dynamic" -> dynamic();
            case "revocation" -> revocation();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    // ------------------------------------------------------------------ leaks

    /** Kept reachable so the heap dump sees them. */
    static volatile Object keep1;
    static volatile Object keep2;
    static volatile Object keep3;

    static void leaks(String child) throws Exception {
        if ("child".equals(child)) {
            // a child process (a shell-out, a crash reporter, a sidecar started by the app) prints its environment
            System.out.println(System.getenv().containsKey("DB_PASSWORD") ? "inherited" : "absent");
            return;
        }
        String marker = "Zk" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        System.out.println("one marker secret per row; searched for in each place it might end up");
        System.out.printf("%-58s %s%n", "where the secret was", "found?");

        // 1 - environment variable: visible to child processes and in /proc/<pid>/environ
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "Measure", "leaks", "child");
        pb.environment().put("DB_PASSWORD", marker + "env");
        Process p = pb.start();
        String childSaw = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        System.out.printf("%-58s %s%n", "environment variable -> a child process's environment", childSaw);

        ProcessBuilder pb2 = new ProcessBuilder("sleep", "2");
        pb2.environment().put("DB_PASSWORD", marker + "proc");
        Process sleeper = pb2.start();
        Thread.sleep(200);
        String procEnviron = Files.readString(Path.of("/proc/" + sleeper.pid() + "/environ"));
        System.out.printf("%-58s %s%n", "environment variable -> /proc/<pid>/environ (same user)",
                procEnviron.contains(marker + "proc") ? "readable" : "absent");
        sleeper.destroy();

        // 2 - mounted file read into a Secret: not in the environment of anything
        Path dir = Files.createTempDirectory("c39-file");
        Files.writeString(dir.resolve("password"), marker + "file");
        Secret fromFile = Secret.of(Files.readAllBytes(dir.resolve("password")));
        ProcessBuilder pb3 = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "Measure", "leaks", "child");
        Process p3 = pb3.start();
        String childSaw3 = new String(p3.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p3.waitFor();
        System.out.printf("%-58s %s%n", "mounted file -> a child process's environment", childSaw3);
        System.out.printf("%-58s %s%n", "Secret wrapper -> toString / string concatenation", "\"" + fromFile + "\"");

        // 3 - heap dumps
        keep1 = new String(marker + "str");                           // a password kept as a String (a config field)
        String once = new String(marker + "once");                    // used once, then unreachable garbage
        once.length();
        once = null;
        // the Secret rows read their value from a file, as a mounted secret would be, and never make a String of it
        writeWiped(dir.resolve("wiped"), marker, "wiped");
        writeWiped(dir.resolve("live"), marker, "live");
        byte[] raw = Files.readAllBytes(dir.resolve("wiped"));
        Secret destroyed = Secret.of(raw);
        java.util.Arrays.fill(raw, (byte) 0);
        destroyed.destroy();
        keep2 = destroyed;
        raw = Files.readAllBytes(dir.resolve("live"));
        keep3 = Secret.of(raw);                                        // a Secret still in use
        java.util.Arrays.fill(raw, (byte) 0);
        HotSpotDiagnosticMXBean diag = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        Path all = dir.resolve("all.hprof");
        Path live = dir.resolve("live.hprof");
        diag.dumpHeap(all.toString(), false);
        diag.dumpHeap(live.toString(), true);
        byte[] allBytes = Files.readAllBytes(all);
        byte[] liveBytes = Files.readAllBytes(live);
        String[][] rows = {
            {"str", "String field (e.g. a bound @ConfigurationProperties value)"},
            {"once", "String used once, then unreachable"},
            {"live", "Secret (char[]) still in use"},
            {"wiped", "Secret (char[]) after destroy()"},
        };
        System.out.printf("%-58s %-22s %s%n", "heap dump contains the value?", "dump of all objects", "dump of live objects");
        for (String[] r : rows) {
            String s = marker + r[0];
            System.out.printf("%-58s %-22s %s%n", r[1], found(allBytes, s), found(liveBytes, s));
        }
        System.out.println("heap dump sizes: " + allBytes.length / 1024 / 1024 + " MB (all), " + liveBytes.length / 1024 / 1024 + " MB (live)");
        Files.deleteIfExists(all);
        Files.deleteIfExists(live);
    }

    /** Writes marker + suffix without ever building that String, and wipes the buffer. */
    static void writeWiped(Path file, String marker, String suffix) throws IOException {
        byte[] a = marker.getBytes(StandardCharsets.ISO_8859_1);
        byte[] b = suffix.getBytes(StandardCharsets.ISO_8859_1);
        byte[] all = new byte[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        Files.write(file, all);
        java.util.Arrays.fill(all, (byte) 0);
    }

    static String found(byte[] heap, String s) {
        int latin1 = count(heap, s.getBytes(StandardCharsets.ISO_8859_1));
        int utf16 = count(heap, s.getBytes(StandardCharsets.UTF_16BE));
        return latin1 + utf16 == 0 ? "no" : "yes (" + (latin1 + utf16) + "x)";
    }

    static int count(byte[] hay, byte[] needle) {
        int n = 0;
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ delivery

    /** Writes a new version the way the kubelet's AtomicWriter does: new timestamped dir, then swap ..data. */
    static void kubeletWrite(Path dir, String password) throws IOException {
        Path version = dir.resolve("..v" + System.nanoTime());
        Files.createDirectory(version);
        Files.writeString(version.resolve("password"), password);
        Path tmp = dir.resolve("..data_tmp");
        Files.deleteIfExists(tmp);
        Files.createSymbolicLink(tmp, version.getFileName());
        Path old = Files.isSymbolicLink(dir.resolve("..data")) ? dir.resolve(Files.readSymbolicLink(dir.resolve("..data"))) : null;
        Files.move(tmp, dir.resolve("..data"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        if (!Files.exists(dir.resolve("password"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.createSymbolicLink(dir.resolve("password"), Path.of("..data/password"));
        }
        if (old != null) {
            Files.delete(old.resolve("password"));
            Files.delete(old);
        }
    }

    static void delivery() throws Exception {
        Path dir = Files.createTempDirectory("c39-mount");
        kubeletWrite(dir, "v0");
        // reader 1: Spring Boot's configtree import reads once at start-up (no ALWAYS_READ option)
        String readOnce = Files.readString(dir.resolve("password"));
        // reader 2: pinned to one version's file, as a subPath mount is (a hard link keeps that file alive)
        Path pinnedDir = Files.createTempDirectory("c39-subpath");
        Path resolved = pinnedDir.resolve("password");
        Files.createLink(resolved, dir.resolve("password").toRealPath());
        // reader 3: MountedSecrets following the volume
        AtomicReference<String> followed = new AtomicReference<>("v0");
        Map<String, Long> seenAt = new ConcurrentHashMap<>();
        MountedSecrets mounted = new MountedSecrets(dir);
        mounted.follow(Duration.ofSeconds(1), snap -> {
            String v = snap.get("password").revealAsString();
            followed.set(v);
            seenAt.put(v, System.nanoTime());
        });

        System.out.println("a mounted secret updated 5 times, 1.5 s apart, the way the kubelet swaps ..data");
        System.out.printf("%-10s %-30s %-34s %s%n", "version", "read once at start-up", "pinned file (like a subPath mount)",
                "MountedSecrets (watch + 1 s poll)");
        List<Double> latencies = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String v = "v" + i;
            long written = System.nanoTime();
            kubeletWrite(dir, v);
            Thread.sleep(1500);
            String r2;
            try {
                r2 = Files.readString(resolved);
            } catch (IOException e) {
                r2 = "error: " + e.getClass().getSimpleName();
            }
            Long at = seenAt.get(v);
            String r3 = followed.get() + (at == null ? " (not seen)" : String.format(" after %.0f ms", (at - written) / 1e6));
            if (at != null) {
                latencies.add((at - written) / 1e6);
            }
            System.out.printf("%-10s %-30s %-34s %s%n", v, readOnce, r2, r3);
        }
        mounted.close();
    }

    // ------------------------------------------------------------------ a tiny connection pool

    /** A pool that replaces each connection after maxLifetime, like Hikari's max-lifetime, using current credentials. */
    static final class Pool {
        record Conn(Pg pg, long createdNanos) {
        }

        final List<Conn> conns = new ArrayList<>();
        final int size;
        final Duration maxLifetime;
        volatile String user;
        volatile String password;
        final AtomicLong ok = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final AtomicLong loginFailures = new AtomicLong();
        final AtomicLong queryFailures = new AtomicLong();
        volatile double firstFailure = -1;
        final long start;

        Pool(int size, Duration maxLifetime, String user, String password, long start) {
            this.size = size;
            this.maxLifetime = maxLifetime;
            this.user = user;
            this.password = password;
            this.start = start;
        }

        synchronized void credentials(String u, String p) {
            user = u;
            password = p;
        }

        /** One unit of work on some connection; opens or replaces connections as needed. */
        void work(int i) {
            Conn c;
            synchronized (this) {
                while (conns.size() < size) {
                    conns.add(null);
                }
                c = conns.get(i % size);
                if (c == null || System.nanoTime() - c.createdNanos() > maxLifetime.toNanos()) {
                    if (c != null) {
                        c.pg().close();
                    }
                    try {
                        c = new Conn(new Pg(HOST, PORT, user, password, DB), System.nanoTime());
                        conns.set(i % size, c);
                    } catch (IOException e) {
                        conns.set(i % size, null);
                        loginFailures.incrementAndGet();
                        fail();
                        return;
                    }
                }
            }
            try {
                c.pg().query(QUERY);
                ok.incrementAndGet();
            } catch (IOException e) {
                queryFailures.incrementAndGet();
                synchronized (this) {
                    c.pg().close();
                    if (conns.get(i % size) == c) {
                        conns.set(i % size, null);
                    }
                }
                fail();
            }
        }

        void fail() {
            failed.incrementAndGet();
            if (firstFailure < 0) {
                firstFailure = (System.nanoTime() - start) / 1e9;
            }
        }

        synchronized void close() {
            for (Conn c : conns) {
                if (c != null) {
                    c.pg().close();
                }
            }
        }
    }

    static void drive(List<Pool> pools, Duration length) throws InterruptedException {
        long end = System.nanoTime() + length.toNanos();
        List<Thread> threads = new ArrayList<>();
        for (Pool pool : pools) {
            threads.add(Thread.ofPlatform().start(() -> {
                int i = 0;
                while (System.nanoTime() < end) {
                    pool.work(i++);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }));
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    static Pg admin() throws IOException {
        return new Pg(HOST, PORT, env("C39_ADMIN_USER", "c39_admin"), env("C39_ADMIN_PASSWORD", ""), DB);
    }

    // ------------------------------------------------------------------ static rotation

    static void staticRotation() throws Exception {
        int pods = 6;
        Duration run = Duration.ofSeconds(16);
        Duration rotateAt = Duration.ofSeconds(4);
        Duration propagation = Duration.ofSeconds(6);
        System.out.println(pods + " pods, a pool of 3 per pod, connections replaced every 2 s (max lifetime), a query every "
                + "20 ms per pod; the new secret reaches each pod at a random moment within " + propagation.toSeconds()
                + " s of the change (ESO refresh + kubelet sync, scaled down); " + run.toSeconds() + " s");
        System.out.printf("%-56s %8s %8s %16s %14s%n", "rotation", "ok", "failed", "first failure", "login errors");
        Random random = new Random(39);
        for (String strategy : List.of("in-place", "two-accounts")) {
            String blue = "c39_app_blue";
            String green = "c39_app_green";
            String p0 = "pw-" + UUID.randomUUID();
            String p1 = "pw-" + UUID.randomUUID();
            try (Pg admin = admin()) {
                admin.query("alter role " + blue + " password '" + p0 + "'");
                admin.query("alter role " + green + " password '" + "pw-" + UUID.randomUUID() + "'");
            }
            long start = System.nanoTime();
            List<Pool> pools = new ArrayList<>();
            for (int i = 0; i < pods; i++) {
                pools.add(new Pool(3, Duration.ofSeconds(2), blue, p0, start));
            }
            ScheduledExecutorService ops = Executors.newScheduledThreadPool(pods + 1);
            ops.schedule(() -> {
                try (Pg admin = admin()) {
                    // in place: the only account gets the new password. two accounts: the idle one does
                    admin.query("alter role " + (strategy.equals("in-place") ? blue : green) + " password '" + p1 + "'");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                for (Pool pool : pools) {
                    long delay = (long) (random.nextDouble() * propagation.toMillis());
                    ops.schedule(() -> pool.credentials(strategy.equals("in-place") ? blue : green, p1), delay,
                            java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }, rotateAt.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            drive(pools, run);
            ops.shutdownNow();
            long ok = pools.stream().mapToLong(p -> p.ok.get()).sum();
            long failed = pools.stream().mapToLong(p -> p.failed.get()).sum();
            long logins = pools.stream().mapToLong(p -> p.loginFailures.get()).sum();
            double first = pools.stream().mapToDouble(p -> p.firstFailure).filter(d -> d >= 0).min().orElse(-1);
            pools.forEach(Pool::close);
            System.out.printf("%-56s %8d %8d %16s %14d%n",
                    strategy.equals("in-place") ? "one account, password changed in place at 4 s"
                            : "two accounts: idle one changed at 4 s, pods switch to it",
                    ok, failed, first < 0 ? "-" : String.format("%.1f s", first), logins);
        }
    }

    // ------------------------------------------------------------------ dynamic credentials

    static VaultClient bao() {
        return new VaultClient(URI.create(env("BAO_ADDR", "http://127.0.0.1:8200")),
                Secret.of(env("BAO_TOKEN", "").toCharArray()));
    }

    static int liveRoles() throws IOException {
        try (Pg admin = admin()) {
            return Integer.parseInt(admin.query("select count(*) from pg_roles where rolname like 'v-%'").get(0).get(0));
        }
    }

    static void dynamic() throws Exception {
        VaultClient bao = bao();
        // cost of issuing: OpenBao creates a PostgreSQL role per request
        List<Double> issue = new ArrayList<>();
        List<Double> revoke = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long t0 = System.nanoTime();
            DatabaseLease l = bao.databaseCredentials("database", "order-service");
            issue.add((System.nanoTime() - t0) / 1e6);
            long t1 = System.nanoTime();
            bao.revoke(l.leaseId());
            revoke.add((System.nanoTime() - t1) / 1e6);
        }
        Collections.sort(issue);
        Collections.sort(revoke);
        System.out.printf("issue credentials (CREATE ROLE + grants): p50 %.1f ms, p99 %.1f ms; revoke: p50 %.1f ms, p99 %.1f ms (100 each)%n",
                issue.get(50), issue.get(98), revoke.get(50), revoke.get(98));
        System.out.println();

        int pods = 4;
        Duration run = Duration.ofSeconds(40);
        System.out.println("role order-service-short: default_ttl 6 s, max_ttl 18 s (scaled down from 1 h / 24 h); " + pods
                + " pods, pool of 3, connections replaced every 2 s, a query every 20 ms per pod; " + run.toSeconds() + " s");
        System.out.printf("%-58s %8s %8s %16s %10s %10s%n", "client", "ok", "failed", "first failure", "renewals", "new creds");
        for (String strategy : List.of("renew-only", "renew-then-rotate")) {
            int before = liveRoles();
            long start = System.nanoTime();
            List<Pool> pools = new ArrayList<>();
            List<DatabaseCredentials> managers = new ArrayList<>();
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
            AtomicLong renewals = new AtomicLong();
            for (int i = 0; i < pods; i++) {
                if (strategy.equals("renew-only")) {
                    // what Spring Cloud Vault 5.0.2 does for spring.cloud.vault.database: a renewable lease, renewed until
                    // max_ttl caps it; no new credentials are requested
                    DatabaseLease l = bao.databaseCredentials("database", "order-service-short");
                    Pool pool = new Pool(3, Duration.ofSeconds(2), l.username(), l.password().revealAsString(), start);
                    pools.add(pool);
                    scheduler.scheduleAtFixedRate(() -> {
                        try {
                            bao.renew(l.leaseId(), l.ttl());
                            renewals.incrementAndGet();
                        } catch (Exception ignored) {
                            // expired: nothing left to renew
                        }
                    }, 2, 2, java.util.concurrent.TimeUnit.SECONDS);
                } else {
                    Pool pool = new Pool(3, Duration.ofSeconds(2), null, null, start);
                    pools.add(pool);
                    DatabaseCredentials.Source source = new DatabaseCredentials.Source() {
                        public DatabaseLease issue() throws Exception {
                            return bao.databaseCredentials("database", "order-service-short");
                        }

                        public Duration renew(String leaseId, Duration increment) throws Exception {
                            return bao.renew(leaseId, increment);
                        }

                        public void revoke(String leaseId) throws Exception {
                            bao.revoke(leaseId);
                        }
                    };
                    managers.add(new DatabaseCredentials(source, Clock.systemUTC(), scheduler, Duration.ofSeconds(3),
                            (next, previous) -> pool.credentials(next.username(), next.password().revealAsString())).start());
                }
            }
            drive(pools, run);
            long ok = pools.stream().mapToLong(p -> p.ok.get()).sum();
            long failed = pools.stream().mapToLong(p -> p.failed.get()).sum();
            double first = pools.stream().mapToDouble(p -> p.firstFailure).filter(d -> d >= 0).min().orElse(-1);
            long ren = strategy.equals("renew-only") ? renewals.get() : managers.stream().mapToLong(DatabaseCredentials::renewals).sum();
            long rot = strategy.equals("renew-only") ? pods : managers.stream().mapToLong(DatabaseCredentials::rotations).sum() + pods;
            pools.forEach(Pool::close);
            managers.forEach(DatabaseCredentials::close);
            scheduler.shutdownNow();
            System.out.printf("%-58s %8d %8d %16s %10d %10d%n",
                    strategy.equals("renew-only") ? "renew-only (Spring Cloud Vault's mode for database creds)"
                            : "DatabaseCredentials: renew, then rotate before max_ttl",
                    ok, failed, first < 0 ? "-" : String.format("%.1f s", first), ren, rot);
            Thread.sleep(1000);
            System.out.println("    database roles left behind afterwards: " + (liveRoles() - before));
        }
    }

    // ------------------------------------------------------------------ revocation

    static void revocation() throws Exception {
        VaultClient bao = bao();
        System.out.println("a session is open and working; then the credential is revoked, rotated or disabled");
        System.out.printf("%-60s %-24s %s%n", "action", "new login", "the open session, next query");

        record Case(String name, String role) {
        }
        for (Case c : List.of(new Case("OpenBao lease revoked (default revocation SQL)", "order-service"),
                new Case("OpenBao lease revoked (revocation SQL + pg_terminate_backend)", "order-service-strict"))) {
            DatabaseLease l = bao.databaseCredentials("database", c.role());
            String pw = l.password().revealAsString();
            try (Pg session = new Pg(HOST, PORT, l.username(), pw, DB)) {
                session.query(QUERY);
                bao.revoke(l.leaseId());
                System.out.printf("%-60s %-24s %s%n", c.name(), login(l.username(), pw), next(session));
            }
        }

        String user = "c39_app_blue";
        String pw = "pw-" + UUID.randomUUID();
        try (Pg admin = admin()) {
            admin.query("alter role " + user + " login password '" + pw + "' valid until 'infinity'");
        }
        try (Pg session = new Pg(HOST, PORT, user, pw, DB)) {
            session.query(QUERY);
            try (Pg admin = admin()) {
                admin.query("alter role " + user + " password 'pw-" + UUID.randomUUID() + "'");
            }
            System.out.printf("%-60s %-24s %s%n", "static password changed (ALTER ROLE ... PASSWORD)", login(user, pw), next(session));
        }
        try (Pg admin = admin()) {
            admin.query("alter role " + user + " password '" + pw + "'");
        }
        try (Pg session = new Pg(HOST, PORT, user, pw, DB)) {
            session.query(QUERY);
            try (Pg admin = admin()) {
                admin.query("alter role " + user + " valid until '2000-01-01'");
            }
            System.out.printf("%-60s %-24s %s%n", "password expired (VALID UNTIL in the past)", login(user, pw), next(session));
        }
        try (Pg admin = admin()) {
            admin.query("alter role " + user + " valid until 'infinity'");
        }
        try (Pg session = new Pg(HOST, PORT, user, pw, DB)) {
            session.query(QUERY);
            try (Pg admin = admin()) {
                admin.query("alter role " + user + " nologin");
            }
            System.out.printf("%-60s %-24s %s%n", "login disabled (ALTER ROLE ... NOLOGIN)", login(user, pw), next(session));
        }
        try (Pg admin = admin()) {
            admin.query("alter role " + user + " login");
        }

        // revoking everything a role ever issued: the incident-response move
        List<DatabaseLease> leases = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            leases.add(bao.databaseCredentials("database", "order-service"));
        }
        long t0 = System.nanoTime();
        bao.revokePrefix("database/creds/order-service");
        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        int left = 0;
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            left = 0;
            for (DatabaseLease l : leases) {
                if (login(l.username(), l.password().revealAsString()).equals("works")) {
                    left++;
                }
            }
            if (left > 0) {
                Thread.sleep(200);
            }
        } while (left > 0 && System.currentTimeMillis() < deadline);
        System.out.printf("%nrevoke-prefix of 50 leases: API call %d ms; logins still working afterwards: %d of 50%n", tookMs, left);
    }

    static String login(String user, String pw) {
        try (Pg pg = new Pg(HOST, PORT, user, pw, DB)) {
            return "works";
        } catch (Pg.PgException e) {
            return "refused (" + e.sqlState + ")";
        } catch (IOException e) {
            return "refused";
        }
    }

    static String next(Pg session) {
        try {
            session.query(QUERY);
            return "query works";
        } catch (Pg.PgException e) {
            if (e.sqlState.equals("57P01")) {
                return "session terminated (57P01)";
            }
            String m = e.getMessage();
            return "still connected; query fails: " + (m.length() > 60 ? m.substring(0, 60) : m);
        } catch (IOException e) {
            return "session terminated by the server";
        }
    }
}
