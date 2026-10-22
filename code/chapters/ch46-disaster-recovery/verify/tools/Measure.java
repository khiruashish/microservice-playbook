import dev.foodfleet.ch46.cell.CellRouter;
import dev.foodfleet.ch46.cell.ShuffleSharder;
import dev.foodfleet.ch46.failover.FailoverController;
import dev.foodfleet.ch46.failover.PrimaryLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Chapter 46's experiments against real PostgreSQL 16 primaries, standbys, archives and restores. */
public final class Measure {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "rpo" -> rpo();
            case "rto" -> rto();
            case "restore" -> restore();
            case "cells" -> cells();
            default -> throw new IllegalArgumentException(args[0]);
        }
        System.exit(0);
    }

    // ============================================================================================ writers

    record Ack(long id, long atNanos) {
    }

    /** {@code n} clients inserting orders as fast as each one's commits return, until stopped or the primary dies. */
    static final class Writers {
        final ConcurrentLinkedQueue<Ack> acked = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        final AtomicBoolean stop = new AtomicBoolean();
        final List<Thread> threads = new ArrayList<>();

        Writers(int n, Lab.Node primary, long pauseMillis) {
            for (int w = 0; w < n; w++) {
                int writer = w;
                threads.add(Thread.ofPlatform().start(() -> {
                    long seq = 0;
                    try (Pg c = primary.connect()) {
                        while (!stop.get()) {
                            long id = writer * 100_000_000L + seq++;
                            long t0 = System.nanoTime();
                            c.query("INSERT INTO orders(id, writer) VALUES (" + id + ", " + writer + ")");
                            long t1 = System.nanoTime();
                            acked.add(new Ack(id, t1));
                            latencies.add((t1 - t0) / 1_000);
                            if (pauseMillis > 0) {
                                Thread.sleep(pauseMillis);
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        // the primary is gone
                    }
                }));
            }
        }

        void join() throws InterruptedException {
            stop.set(true);
            for (Thread t : threads) {
                t.join(10_000);
            }
        }
    }

    static Set<Long> ids(Lab.Node node) throws IOException {
        Set<Long> s = new HashSet<>();
        try (Pg c = node.connect()) {
            for (List<String> row : c.query("SELECT id FROM orders")) {
                s.add(Long.parseLong(row.get(0)));
            }
        }
        return s;
    }

    static void waitFor(Lab.Node node, String query, String expected, Duration max) throws Exception {
        long end = System.nanoTime() + max.toNanos();
        while (System.nanoTime() < end) {
            try {
                if (expected.equals(node.value(query))) {
                    return;
                }
            } catch (IOException | IndexOutOfBoundsException e) {
                // not yet
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException(node.name() + ": '" + query + "' never returned " + expected);
    }

    // ============================================================================================ RPO by replication mode

    static void rpo() throws Exception {
        System.out.println("8 clients insert orders (one commit each) on the primary for 8 s; then the primary's region is lost: the replication");
        System.out.println("link is cut and the primary powered off (pg_ctl stop -m immediate); the standby is promoted, and every order a client");
        System.out.println("was told is committed is looked for. First row of each group: only the primary dies, the link stays up.");
        record Mode(String name, String conf) {
        }
        List<Mode> modes = List.of(
                new Mode("asynchronous; primary crashes, link stays up", "keep-link"),
                new Mode("asynchronous; region lost (link and primary)", null),
                new Mode("asynchronous; link lost 2 s before the primary", "partition"),
                new Mode("synchronous, remote_write", "remote_write"),
                new Mode("synchronous, on (flushed on the standby)", "on"),
                new Mode("synchronous, remote_apply", "remote_apply"));
        long[][] distances = {{0}, {10}, {30}};
        String[] where = {"another zone (no added latency)", "nearby region (20 ms round trip)", "far region (60 ms round trip)"};
        System.out.printf("%-34s %-46s %12s %12s %12s %18s %16s%n", "standby", "replication", "commits/s", "commit p50", "commit p99",
                "acknowledged, lost", "lost commits span");
        for (int d = 0; d < distances.length; d++) {
            for (Mode m : modes) {
                Lab.reset();
                try (Lab.Toxiproxy tp = new Lab.Toxiproxy()) {
                    Lab.Node primary = Lab.primary("primary", "");
                    primary.sql("CREATE TABLE orders (id bigint PRIMARY KEY, writer int NOT NULL, placed_at timestamptz NOT NULL DEFAULT now())");
                    int via = tp.link("replication", primary.port(), distances[d][0]);
                    Lab.Node standby = Lab.standby("standby", via, "replica1");
                    if (m.conf() != null && !m.conf().equals("keep-link") && !m.conf().equals("partition")) {   // synchronous once the standby exists, or commits would wait for it
                        primary.sql("ALTER SYSTEM SET synchronous_standby_names = 'replica1'",
                                "ALTER SYSTEM SET synchronous_commit = '" + m.conf() + "'", "SELECT pg_reload_conf()");
                    }
                    String expected = m.name().startsWith("synchronous") ? "sync" : "async";
                    waitFor(primary, "SELECT sync_state FROM pg_stat_replication", expected, Duration.ofSeconds(30));
                    Writers w = new Writers(8, primary, 0);
                    long start = System.nanoTime();
                    Thread.sleep(8000);
                    if ("partition".equals(m.conf())) {
                        tp.cut("replication");
                        Thread.sleep(2000);           // the primary keeps accepting writes it can't replicate
                    } else if (!"keep-link".equals(m.conf())) {
                        tp.cut("replication");
                    }
                    long crashAt = System.nanoTime();
                    primary.crash();
                    w.join();
                    Thread.sleep(1000);               // with the link up, whatever was on the wire still arrives
                    standby.sql("SELECT pg_promote(true, 60)");
                    Set<Long> present = ids(standby);
                    List<Ack> lost = w.acked.stream().filter(a -> !present.contains(a.id())).toList();
                    long oldestLost = lost.stream().mapToLong(Ack::atNanos).min().orElse(0);
                    long newestLost = lost.stream().mapToLong(Ack::atNanos).max().orElse(0);
                    List<Long> lat = new ArrayList<>(w.latencies);
                    double rate = w.acked.size() / ((crashAt - start) / 1e9);
                    System.out.printf("%-34s %-46s %12.0f %9.1f ms %9.1f ms %18s %16s%n", m == modes.get(0) ? where[d] : "", m.name(), rate,
                            Lab.percentile(lat, 50) / 1000.0, Lab.percentile(lat, 99) / 1000.0,
                            String.format("%,d / %,d", w.acked.size(), lost.size()),
                            lost.isEmpty() ? "-" : String.format("%.0f ms", (newestLost - oldestLost) / 1e6));
                }
            }
        }
        Lab.reset();
    }

    // ============================================================================================ RTO: failover timeline

    static void rto() throws Exception {
        System.out.println("4 clients write continuously; at 5 s the primary is powered off; a controller probes it (connect + SELECT 1, 1 s timeout)");
        System.out.println("and, after 3 failed probes in a row, promotes the standby; clients find the new primary by asking each host for its role,");
        System.out.println("or (last row) through a DNS name the controller updates, cached by each client for up to 30 s");
        record Variant(String name, Duration interval, boolean dns) {
        }
        List<Variant> variants = List.of(
                new Variant("probe every 1 s", Duration.ofSeconds(1), false),
                new Variant("probe every 5 s", Duration.ofSeconds(5), false),
                new Variant("probe every 10 s", Duration.ofSeconds(10), false),
                new Variant("probe every 1 s; clients follow DNS (TTL 30 s)", Duration.ofSeconds(1), true));
        System.out.printf("%-50s %12s %12s %24s %24s%n", "failover", "detection", "promotion", "writes resumed (median)", "writes resumed (slowest)");
        for (Variant v : variants) {
            Lab.reset();
            Lab.Node primary = Lab.primary("primary", "");
            primary.sql("CREATE TABLE orders (id bigint PRIMARY KEY, writer int NOT NULL, placed_at timestamptz NOT NULL DEFAULT now())");
            Lab.Node standby = Lab.standby("standby", primary.port(), "replica1");
            waitFor(primary, "SELECT state FROM pg_stat_replication", "streaming", Duration.ofSeconds(30));
            Map<String, Lab.Node> byHost = Map.of("pg-a", primary, "pg-b", standby);
            AtomicReference<String> dnsRecord = new AtomicReference<>("pg-a");
            AtomicLong crashAt = new AtomicLong(Long.MAX_VALUE);
            AtomicBoolean stop = new AtomicBoolean();
            ConcurrentHashMap<Integer, Long> resumedAt = new ConcurrentHashMap<>();
            List<Thread> clients = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int writer = i;
                clients.add(Thread.ofPlatform().start(() -> {
                    PrimaryLocator locator = new PrimaryLocator(List.of("pg-a", "pg-b"),
                            host -> "f".equals(byHost.get(host).value("SELECT pg_is_in_recovery()")));
                    String cached = "pg-a";
                    long cachedAt = System.nanoTime() - ThreadLocalRandom.current().nextLong(30_000_000_000L);
                    long seq = 0;
                    Pg conn = null;
                    String connHost = null;
                    while (!stop.get()) {
                        String host = v.dns() ? cached : locator.current();
                        try {
                            if (conn == null || !host.equals(connHost)) {
                                if (conn != null) {
                                    conn.close();
                                }
                                conn = byHost.get(host).connect();
                                connHost = host;
                            }
                            conn.query("INSERT INTO orders(id, writer) VALUES (" + (writer * 100_000_000L + seq++) + ", " + writer + ")");
                            long now = System.nanoTime();
                            if (now > crashAt.get()) {
                                resumedAt.putIfAbsent(writer, now);
                            }
                            Thread.sleep(20);
                        } catch (IOException e) {
                            if (conn != null) {
                                conn.close();
                            }
                            conn = null;
                            if (v.dns()) {
                                if (System.nanoTime() - cachedAt > 30_000_000_000L) {
                                    cached = dnsRecord.get();
                                    cachedAt = System.nanoTime();
                                }
                            } else {
                                locator.relocate();
                            }
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ie) {
                                return;
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    if (conn != null) {
                        conn.close();
                    }
                }));
            }
            AtomicLong promotedAt = new AtomicLong();
            Thread.sleep(5000);
            long crashStart = System.nanoTime();
            primary.crash();
            crashAt.set(System.nanoTime());   // writes that succeed after this went to the new primary
            FailoverController controller = new FailoverController(() -> {
                try (Pg c = new Pg("127.0.0.1", primary.port(), "postgres", "", "postgres", 1000)) {
                    c.query("SELECT 1");
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }, () -> {
                // fencing: the old primary is already powered off here; in production, stop it or cut it off first
            }, () -> {
                try {
                    standby.sql("SELECT pg_promote(true, 60)");
                    dnsRecord.set("pg-b");
                    promotedAt.set(System.nanoTime());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }, v.interval(), 3);
            FailoverController.Timeline t = controller.watch();
            long deadline = System.nanoTime() + 45_000_000_000L;
            while (resumedAt.size() < 4 && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            stop.set(true);
            for (Thread c : clients) {
                c.join(5000);
            }
            List<Long> resumed = resumedAt.values().stream().map(r -> (r - crashStart) / 1_000_000).sorted().toList();
            System.out.printf("%-50s %10.1f s %10.2f s %22s %24s%n", v.name(), (t.declaredDead() - crashStart) / 1e9, t.promotion().toMillis() / 1000.0,
                    resumed.isEmpty() ? "-" : String.format("%.1f s", resumed.get(resumed.size() / 2) / 1000.0),
                    resumed.size() < 4 ? "not within 45 s" : String.format("%.1f s", resumed.get(resumed.size() - 1) / 1000.0));
        }
        Lab.reset();
    }

    // ============================================================================================ backups and restores

    static void restore() throws Exception {
        System.out.println("A) no standby: the primary and its disk are lost; restore = base backup + archived WAL");
        System.out.println("   one client inserts 20 orders/s for 75 s after the base backup, then the primary is powered off and its data deleted");
        System.out.printf("%-56s %22s %20s %18s%n", "WAL shipping", "acknowledged, lost", "oldest lost order", "restore took");
        for (String variant : List.of("archive_command, archive_timeout = 60 s", "archive_command, archive_timeout = 10 s",
                "pg_receivewal --synchronous (streamed to the archive)")) {
            Lab.reset();
            Path archive = Lab.BASE.resolve("archive");
            Lab.run("mkdir", "-p", archive.toString());
            boolean stream = variant.startsWith("pg_receivewal");
            String timeout = variant.contains("60 s") ? "60s" : "10s";
            Lab.Node primary = Lab.primary("primary", stream ? "" : "archive_mode = on\narchive_command = 'cp %p " + archive + "/%f'\narchive_timeout = " + timeout);
            primary.sql("CREATE TABLE orders (id bigint PRIMARY KEY, writer int NOT NULL, placed_at timestamptz NOT NULL DEFAULT now())");
            Process receiver = null;
            if (stream) {
                List<String> cmd = new ArrayList<>();
                if (Lab.ROOT) {
                    cmd.addAll(List.of("runuser", "-u", "postgres", "--"));
                }
                cmd.addAll(List.of(Lab.pg("pg_receivewal"), "-h", "127.0.0.1", "-p", Integer.toString(primary.port()), "-U", "postgres",
                        "-D", archive.toString(), "--synchronous", "--no-loop"));
                receiver = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(Lab.BASE.resolve("receivewal.log").toFile()).start();
            }
            Lab.run(Lab.pg("pg_basebackup"), "-h", "127.0.0.1", "-p", Integer.toString(primary.port()), "-U", "postgres",
                    "-D", Lab.BASE.resolve("backup").toString(), "-X", "none", "-c", "fast");
            Writers w = new Writers(1, primary, 50);
            Thread.sleep(75_000);
            long crashAt = System.nanoTime();
            primary.crash();
            w.join();
            if (receiver != null) {
                receiver.waitFor();
            }
            Lab.run("rm", "-rf", primary.dir().toString());
            long t0 = System.nanoTime();
            Lab.Node restored = restoreFrom("restored", Lab.BASE.resolve("backup"), archive);
            long took = System.nanoTime() - t0;
            Set<Long> present = ids(restored);
            List<Ack> lost = w.acked.stream().filter(a -> !present.contains(a.id())).toList();
            long oldest = lost.stream().mapToLong(Ack::atNanos).min().orElse(crashAt);
            System.out.printf("%-56s %22s %20s %16.1f s%n", variant, String.format("%,d / %,d", w.acked.size(), lost.size()),
                    lost.isEmpty() ? "-" : String.format("%.1f s before", (crashAt - oldest) / 1e9), took / 1e9);
        }

        System.out.println();
        System.out.println("B) restore time against database size: base backup, then 30 s of pgbench writes (4 clients), then the primary is lost");
        System.out.printf("%-26s %14s %16s %26s %18s%n", "pgbench scale", "database", "base backup", "WAL written after backup", "restore took");
        for (int scale : new int[] {10, 50}) {
            Lab.reset();
            Path archive = Lab.BASE.resolve("archive");
            Lab.run("mkdir", "-p", archive.toString());
            Lab.Node primary = Lab.primary("primary", "archive_mode = on\narchive_command = 'cp %p " + archive + "/%f'\narchive_timeout = 10s\nmax_wal_size = 4GB");
            Lab.run(Lab.pg("pgbench"), "-i", "-q", "-s", Integer.toString(scale), "-h", "127.0.0.1", "-p", Integer.toString(primary.port()), "-U", "postgres", "postgres");
            String size = primary.value("SELECT pg_size_pretty(pg_database_size('postgres'))");
            long b0 = System.nanoTime();
            Lab.run(Lab.pg("pg_basebackup"), "-h", "127.0.0.1", "-p", Integer.toString(primary.port()), "-U", "postgres",
                    "-D", Lab.BASE.resolve("backup").toString(), "-X", "none", "-c", "fast");
            double backup = (System.nanoTime() - b0) / 1e9;
            String lsnAfterBackup = primary.value("SELECT pg_current_wal_lsn()");
            Lab.run(Lab.pg("pgbench"), "-T", "30", "-c", "4", "-j", "2", "-n", "-h", "127.0.0.1", "-p", Integer.toString(primary.port()), "-U", "postgres", "postgres");
            long walBytes = Long.parseLong(primary.value("SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), '" + lsnAfterBackup + "')::bigint"));
            primary.sql("SELECT pg_switch_wal()");
            Thread.sleep(2000);
            primary.crash();
            Lab.run("rm", "-rf", primary.dir().toString());
            long t0 = System.nanoTime();
            restoreFrom("restored", Lab.BASE.resolve("backup"), archive);
            double took = (System.nanoTime() - t0) / 1e9;
            System.out.printf("%-26s %14s %14.1f s %23d MB %16.1f s%n", "scale " + scale, size, backup, walBytes >> 20, took);
        }
        Lab.reset();
    }

    /** Copy the base backup, replay every archived segment (a pg_receivewal .partial included), open for writes. */
    static Lab.Node restoreFrom(String name, Path backup, Path archive) throws Exception {
        Lab.Node n = new Lab.Node(name, Lab.freePort());
        Lab.run("cp", "-a", backup.toString(), n.dir().toString());
        try (var files = Files.list(archive)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".partial")).toList()) {
                Lab.run("mv", f.toString(), f.toString().replace(".partial", ""));
            }
        }
        n.conf("port = " + n.port() + "\nrestore_command = 'cp " + archive + "/%f %p'\nrecovery_target_action = 'promote'");
        Lab.run("touch", n.dir().resolve("recovery.signal").toString());
        Lab.run("rm", "-f", n.dir().resolve("postmaster.pid").toString());
        n.start();
        waitFor(n, "SELECT pg_is_in_recovery()", "f", Duration.ofMinutes(10));
        return n;
    }

    // ============================================================================================ cells

    static void cells() {
        System.out.println("10,000 tenants; one tenant's requests crash whatever instance serves them (a poison request, a bad config, a hot shard);");
        System.out.println("8 instances in every layout; a client retries on the other instances of its tenant's cell or shard");
        int tenants = 10_000;
        List<String> eight = List.of("i1", "i2", "i3", "i4", "i5", "i6", "i7", "i8");
        record Layout(String name, java.util.function.Function<String, List<String>> serving) {
        }
        CellRouter four = new CellRouter(List.of("c1", "c2", "c3", "c4"));
        CellRouter eightCells = new CellRouter(List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8"));
        Map<String, List<String>> fourCells = Map.of("c1", List.of("i1", "i2"), "c2", List.of("i3", "i4"), "c3", List.of("i5", "i6"), "c4", List.of("i7", "i8"));
        ShuffleSharder two = new ShuffleSharder(eight, 2);
        ShuffleSharder three = new ShuffleSharder(eight, 3);
        List<Layout> layouts = List.of(
                new Layout("one shared pool of 8", t -> eight),
                new Layout("4 cells of 2 instances", t -> fourCells.get(four.cellOf(t))),
                new Layout("8 cells of 1 instance", t -> List.of("i" + eightCells.cellOf(t).substring(1))),
                new Layout("shuffle sharding, 2 of 8 per tenant", two::shardOf),
                new Layout("shuffle sharding, 3 of 8 per tenant", three::shardOf));
        System.out.printf("%-40s %24s %28s%n", "layout", "tenants fully down", "tenants on a damaged shard");
        for (Layout l : layouts) {
            Set<String> dead = new HashSet<>(l.serving().apply("poison"));
            int down = 0;
            int degraded = 0;
            for (int i = 0; i < tenants; i++) {
                List<String> mine = l.serving().apply("tenant-" + i);
                long hit = mine.stream().filter(dead::contains).count();
                if (hit == mine.size()) {
                    down++;
                } else if (hit > 0) {
                    degraded++;
                }
            }
            System.out.printf("%-40s %16d (%4.1f%%) %20d (%4.1f%%)%n", l.name(), down, 100.0 * down / tenants, degraded, 100.0 * degraded / tenants);
        }
    }
}
