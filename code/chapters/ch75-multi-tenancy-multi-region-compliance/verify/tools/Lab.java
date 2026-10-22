import dev.foodfleet.ch75.erasure.ByteScan;
import dev.foodfleet.ch75.erasure.CryptoShredder;
import dev.foodfleet.ch75.tenancy.Cluster;
import dev.foodfleet.ch75.tenancy.TenancyModel;
import dev.foodfleet.ch75.tenancy.TenantScope;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Chapter 75's experiments on PostgreSQL 16.
 * <ul>
 *   <li>{@code models}: shared table with row-level security, schema per tenant, database per tenant, at 10, 100 and
 *       1,000 tenants: provisioning, footprint, catalog size, migration, query latency, connections</li>
 *   <li>{@code leaks}: what a query that forgets its tenant returns, with and without RLS, as table owner, and on a
 *       pooled connection whose tenant was set per session or per transaction</li>
 *   <li>{@code noisy}: one big tenant's reports against everyone else's p99, unlimited, behind a per-tenant bulkhead,
 *       and with a statement timeout</li>
 *   <li>{@code erasure}: where an erased customer's email survives (table files, WAL, a base backup) after DELETE,
 *       VACUUM and VACUUM FULL, against crypto-shredding</li>
 * </ul>
 * System properties: c75.dir (cluster directories), c75.tenants (default 10,100,1000).
 */
public final class Lab {

    static final Path BASE = Path.of(System.getProperty("c75.dir", "/tmp/c75pg"));

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            switch (mode) {
                case "models" -> models();
                case "leaks" -> leaks();
                case "noisy" -> noisy();
                case "erasure" -> erasure();
                default -> throw new IllegalArgumentException(mode);
            }
        }
    }

    // ------------------------------------------------------------------ models

    static void models() throws Exception {
        int rows = 2_000;
        System.out.printf("%-22s %7s %13s %12s %10s %10s %11s %12s %12s %6s%n", "model", "tenants", "provision ms", "per tenant", "size MB",
                "relations", "migrate ms", "last 20 p50/p99", "revenue p50/p99", "conns");
        for (String n : System.getProperty("c75.tenants", "10,100,1000").split(",")) {
            int tenants = Integer.parseInt(n);
            for (int m = 0; m < 3; m++) {
                if (m == 2 && tenants > 100) {
                    System.out.printf("%-22s %7d  (not run: about 7.5 MB and a connection per database)%n", "database per tenant", tenants);
                    continue;
                }
                try (Cluster c = Cluster.start(BASE.resolve("models"), "max_connections = 300\nshared_buffers = 128MB")) {
                    TenancyModel model = switch (m) {
                        case 0 -> new TenancyModel.SharedTable(c);
                        case 1 -> new TenancyModel.SchemaPerTenant(c);
                        default -> new TenancyModel.DatabasePerTenant(c);
                    };
                    double[] prov = new double[tenants];
                    long t0 = System.nanoTime();
                    for (int t = 1; t <= tenants; t++) {
                        long s = System.nanoTime();
                        model.provision(t);
                        prov[t - 1] = (System.nanoTime() - s) / 1e6;
                    }
                    double provMs = (System.nanoTime() - t0) / 1e6;
                    for (int t = 1; t <= tenants; t++) {
                        model.load(t, rows);
                    }
                    String db = switch (m) {
                        case 0 -> "shared";
                        case 1 -> "schemas";
                        default -> null;
                    };
                    try (Connection a = c.connect("postgres"); Statement s = a.createStatement()) {
                        if (db != null) {
                            try (Connection d = c.connect(db); Statement ds = d.createStatement()) {
                                ds.execute("VACUUM ANALYZE");
                            }
                        } else {
                            for (int t = 1; t <= tenants; t++) {
                                try (Connection d = c.connect("t" + t); Statement ds = d.createStatement()) {
                                    ds.execute("VACUUM ANALYZE");
                                }
                            }
                        }
                    }
                    long bytes = 0;
                    long relations = 0;
                    try (Connection a = c.connect("postgres"); Statement s = a.createStatement();
                         ResultSet r = s.executeQuery("SELECT datname, pg_database_size(datname) FROM pg_database WHERE datname NOT IN ('template0', 'template1', 'postgres')")) {
                        List<String> dbs = new ArrayList<>();
                        while (r.next()) {
                            bytes += r.getLong(2);
                            dbs.add(r.getString(1));
                        }
                        for (String d : dbs) {
                            try (Connection x = c.connect(d); Statement xs = x.createStatement(); ResultSet rr = xs.executeQuery("SELECT count(*) FROM pg_class")) {
                                rr.next();
                                relations += rr.getLong(1);
                            }
                        }
                    }
                    Random rnd = new Random(75);
                    for (int i = 0; i < 500; i++) {
                        model.lastOrders(1 + rnd.nextInt(tenants));
                        model.revenue(1 + rnd.nextInt(tenants));
                    }
                    double[] q1 = new double[3_000];
                    double[] q2 = new double[3_000];
                    for (int i = 0; i < q1.length; i++) {
                        int t = 1 + rnd.nextInt(tenants);
                        long s = System.nanoTime();
                        model.lastOrders(t);
                        q1[i] = (System.nanoTime() - s) / 1e6;
                        s = System.nanoTime();
                        model.revenue(t);
                        q2[i] = (System.nanoTime() - s) / 1e6;
                    }
                    long s = System.nanoTime();
                    model.migrate(tenants);
                    double migrateMs = (System.nanoTime() - s) / 1e6;
                    System.out.printf("%-22s %7d %13.0f %12.1f %10.1f %10d %11.0f %12s %12s %6d%n", model.name(), tenants, provMs, pct(prov, 50), bytes / 1e6, relations,
                            migrateMs, String.format("%.2f/%.2f", pct(q1, 50), pct(q1, 99)), String.format("%.2f/%.2f", pct(q2, 50), pct(q2, 99)), model.connections());
                    if (model instanceof TenancyModel.DatabasePerTenant d) {
                        d.closeAll();
                        double[] open = new double[Math.min(tenants, 50)];
                        for (int i = 0; i < open.length; i++) {
                            long o = System.nanoTime();
                            try (Connection x = c.connect("t" + (1 + i))) {
                                open[i] = (System.nanoTime() - o) / 1e6;
                            }
                        }
                        System.out.printf("%-22s %7s  opening a connection to a tenant's database: p50 %.2f ms, p99 %.2f ms%n", "", "", pct(open, 50), pct(open, 99));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ leaks

    static void leaks() throws Exception {
        try (Cluster c = Cluster.start(BASE.resolve("leaks"), "")) {
            TenancyModel.SharedTable model = new TenancyModel.SharedTable(c);
            for (int t = 1; t <= 20; t++) {
                model.provision(t);
                model.load(t, 500);
            }
            try (Connection a = c.connect("shared"); Statement s = a.createStatement()) {
                s.execute("CREATE TABLE orders_plain AS SELECT * FROM orders");
                s.execute("GRANT SELECT ON orders_plain TO app");
                s.execute("CREATE ROLE reports LOGIN");
                s.execute("CREATE TABLE orders_owned AS SELECT * FROM orders");
                s.execute("ALTER TABLE orders_owned OWNER TO reports");
                s.execute("ALTER TABLE orders_owned ENABLE ROW LEVEL SECURITY");
                s.execute("CREATE POLICY tenant_isolation ON orders_owned USING (tenant_id = " + TenancyModel.POLICY + ")");
            }
            String forgot = "SELECT count(*), count(DISTINCT tenant_id) FROM %s";   // the WHERE tenant_id = ? that someone forgot
            System.out.println("tenant 7 runs a query that forgot its tenant predicate (20 tenants, 500 orders each):");
            try (Connection app = c.connect("shared", "app"); Connection owner = c.connect("shared", "reports")) {
                System.out.println("  no RLS, as the application role:            " + scoped(app, 7, String.format(forgot, "orders_plain")));
                System.out.println("  RLS enabled and forced, application role:   " + scoped(app, 7, String.format(forgot, "orders")));
                System.out.println("  RLS enabled, not forced, as table owner:    " + scoped(owner, 7, String.format(forgot, "orders_owned")));
                try (Connection a = c.connect("shared"); Statement s = a.createStatement()) {
                    s.execute("ALTER TABLE orders_owned FORCE ROW LEVEL SECURITY");
                }
                System.out.println("  RLS enabled and forced, as table owner:     " + scoped(owner, 7, String.format(forgot, "orders_owned")));
                try (Connection su = c.connect("shared", "postgres")) {
                    System.out.println("  RLS enabled and forced, as a superuser:     " + scoped(su, 7, String.format(forgot, "orders")));
                }
            }
            // a pool of 4 connections, 2,000 requests from random tenants; one code path in ten forgets to set the tenant
            System.out.println("pooled connections (4), 2,000 requests, 10% of them on a code path that forgets to set the tenant:");
            String[][] variants = {{"per session (SET)", "false", TenancyModel.POLICY},
                    {"per transaction, policy casts the setting", "true", "current_setting('app.tenant_id', true)::int"},
                    {"per transaction, policy with NULLIF", "true", TenancyModel.POLICY}};
            for (String[] v : variants) {
                boolean local = Boolean.parseBoolean(v[1]);
                try (Connection a = c.connect("shared"); Statement s = a.createStatement()) {
                    s.execute("ALTER POLICY tenant_isolation ON orders USING (tenant_id = " + v[2] + ")");
                }
                BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(4);
                for (int i = 0; i < 4; i++) {
                    pool.add(c.connect("shared", "app"));
                }
                Random rnd = new Random(7);
                int forgotten = 0;
                int leaked = 0;
                int empty = 0;
                int errors = 0;
                for (int i = 0; i < 2_000; i++) {
                    int tenant = 1 + rnd.nextInt(20);
                    boolean forgets = rnd.nextInt(10) == 0;
                    Connection con = pool.take();
                    con.setAutoCommit(false);
                    if (!forgets) {
                        try (PreparedStatement s = con.prepareStatement("SELECT set_config('app.tenant_id', ?, " + local + ")")) {
                            s.setString(1, Integer.toString(tenant));
                            s.execute();
                        }
                    }
                    Set<Integer> seen = new HashSet<>();
                    boolean failed = false;
                    try (Statement s = con.createStatement(); ResultSet r = s.executeQuery("SELECT DISTINCT tenant_id FROM orders")) {
                        while (r.next()) {
                            seen.add(r.getInt(1));
                        }
                    } catch (SQLException e) {
                        failed = true;
                        con.rollback();
                    }
                    con.commit();
                    con.setAutoCommit(true);
                    pool.add(con);
                    if (forgets) {
                        forgotten++;
                        if (failed) {
                            errors++;
                        } else if (seen.isEmpty()) {
                            empty++;
                        } else if (!seen.equals(Set.of(tenant))) {
                            leaked++;
                        }
                    } else if (!seen.equals(Set.of(tenant))) {
                        throw new IllegalStateException("a correct request saw " + seen);
                    }
                }
                for (Connection x : pool) {
                    x.close();
                }
                System.out.printf("  tenant set %-42s forgotten %3d: saw another tenant's orders %3d, saw nothing %3d, failed with an error %3d%n",
                        v[0] + ":", forgotten, leaked, empty, errors);
            }
        }
    }

    static String scoped(Connection c, int tenant, String sql) throws SQLException {
        return TenantScope.inTenant(c, tenant, x -> {
            try (Statement s = x.createStatement(); ResultSet r = s.executeQuery(sql)) {
                r.next();
                return r.getLong(1) + " rows from " + r.getLong(2) + " tenant(s)";
            }
        });
    }

    // ------------------------------------------------------------------ noisy neighbour

    static void noisy() throws Exception {
        try (Cluster c = Cluster.start(BASE.resolve("noisy"), "max_connections = 100\nshared_buffers = 256MB")) {
            TenancyModel.SharedTable model = new TenancyModel.SharedTable(c);
            for (int t = 1; t <= 21; t++) {
                model.provision(t);
                model.load(t, t == 21 ? 1_000_000 : 2_000);
            }
            try (Connection a = c.connect("shared"); Statement s = a.createStatement()) {
                s.execute("VACUUM ANALYZE");
                s.execute("CREATE ROLE app_limited LOGIN IN ROLE app");
                s.execute("ALTER ROLE app_limited SET statement_timeout = '100ms'");
            }
            String report = "SELECT customer_email, count(*), sum(total_paise) FROM orders WHERE tenant_id = 21 GROUP BY customer_email ORDER BY 3 DESC";
            double[] alone = new double[5];
            try (Connection rc = c.connect("shared", "app")) {
                for (int i = 0; i < alone.length; i++) {
                    long t = System.nanoTime();
                    TenantScope.inTenant(rc, 21, x -> {
                        try (Statement s = x.createStatement(); ResultSet r = s.executeQuery(report)) {
                            while (r.next()) {
                                // consume
                            }
                        }
                        return null;
                    });
                    alone[i] = (System.nanoTime() - t) / 1e6;
                }
            }
            quietLoad(c, 100, 3);   // warm-up, not counted
            System.out.printf("tenant 21's report alone: median %.0f ms of 5%n", pct(alone, 50));
            System.out.println("20 small tenants (2,000 orders each) send 'last 20 orders' at 100/s from a pool of 8; tenant 21 (1,000,000 orders) runs a report in a loop");
            String[][] variants = {{"quiet: no reports", "0", "app"}, {"6 concurrent reports, no limit", "6", "app"},
                    {"6 requested, bulkhead of 1 per tenant", "6b", "app"}, {"6 concurrent, statement_timeout 100 ms", "6", "app_limited"}};
            for (String[] v : variants) {
                int reporters = v[1].equals("0") ? 0 : 6;
                boolean bulkhead = v[1].endsWith("b");
                AtomicBoolean stop = new AtomicBoolean();
                AtomicInteger done = new AtomicInteger();
                AtomicInteger cancelled = new AtomicInteger();
                Semaphore oneAtATime = new Semaphore(1);
                List<Thread> noisy = new ArrayList<>();
                for (int i = 0; i < reporters; i++) {
                    Connection rc = c.connect("shared", v[2]);
                    noisy.add(Thread.ofPlatform().start(() -> {
                        try (rc) {
                            while (!stop.get()) {
                                if (bulkhead && !oneAtATime.tryAcquire()) {
                                    LockSupport.parkNanos(5_000_000);   // rejected by the bulkhead: back off
                                    continue;
                                }
                                try {
                                    TenantScope.inTenant(rc, 21, x -> {
                                        try (Statement s = x.createStatement(); ResultSet r = s.executeQuery(report)) {
                                            while (r.next()) {
                                                // consume
                                            }
                                        }
                                        return null;
                                    });
                                    done.incrementAndGet();
                                } catch (SQLException e) {
                                    cancelled.incrementAndGet();
                                } finally {
                                    if (bulkhead) {
                                        oneAtATime.release();
                                    }
                                }
                            }
                        } catch (SQLException ignored) {
                            // closing
                        }
                    }));
                }
                Thread.sleep(reporters == 0 ? 0 : 1_000);
                double[] lat = quietLoad(c, 100, 10);
                stop.set(true);
                for (Thread t : noisy) {
                    t.join();
                }
                System.out.printf("  %-42s small tenants p50 %6.2f ms, p99 %7.2f ms, max %7.1f ms   reports completed %3d, cancelled %3d%n", v[0],
                        pct(lat, 50), pct(lat, 99), pct(lat, 100), done.get(), cancelled.get());
            }
        }
    }

    /** Open load of 'last 20 orders' for random small tenants, latency from each request's scheduled time. */
    static double[] quietLoad(Cluster c, int rate, int seconds) throws Exception {
        BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(8);
        for (int i = 0; i < 8; i++) {
            pool.add(c.connect("shared", "app"));
        }
        int n = rate * seconds;
        double[] lat = new double[n];
        Random rnd = new Random(21);
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.nanoTime() + 50_000_000;
            for (int i = 0; i < n; i++) {
                long due = start + (long) (i * 1e9 / rate);
                long wait = due - System.nanoTime();
                if (wait > 0) {
                    LockSupport.parkNanos(wait);
                }
                int k = i;
                int tenant = 1 + rnd.nextInt(20);
                ex.submit(() -> {
                    Connection con = pool.take();
                    try {
                        TenantScope.inTenant(con, tenant, x -> {
                            try (PreparedStatement s = x.prepareStatement("SELECT total_paise FROM orders WHERE tenant_id = ? ORDER BY placed_at DESC LIMIT 20")) {
                                s.setInt(1, tenant);
                                return TenancyModel.first(s);
                            }
                        });
                    } finally {
                        pool.add(con);
                    }
                    lat[k] = (System.nanoTime() - due) / 1e6;
                    return null;
                });
            }
        }
        for (Connection x : pool) {
            x.close();
        }
        return lat;
    }

    // ------------------------------------------------------------------ erasure

    static void erasure() throws Exception {
        String email = "erase-me-4242@example.in";
        byte[] needle = email.getBytes(StandardCharsets.UTF_8);
        Path backup = BASE.resolve("backup");
        new ProcessBuilder("rm", "-rf", backup.toString()).start().waitFor();
        try (Cluster c = Cluster.start(BASE.resolve("erasure"), "wal_level = replica\nmax_wal_size = 1GB");
             Cluster keys = Cluster.start(BASE.resolve("keys"), "")) {
            try (Connection a = c.connect("postgres"); Statement s = a.createStatement()) {
                s.execute("CREATE TABLE customers (id bigserial PRIMARY KEY, subject text NOT NULL, email text NOT NULL, name text NOT NULL)");
                s.execute("INSERT INTO customers (subject, email, name) SELECT 'subject-' || g, CASE WHEN g = 4242 THEN '" + email
                        + "' ELSE 'customer-' || g || '@example.in' END, 'Name ' || g FROM generate_series(1, 10000) g");
                s.execute("CREATE TABLE customers_enc (id bigserial PRIMARY KEY, subject text NOT NULL, email bytea NOT NULL, name bytea NOT NULL)");
            }
            CryptoShredder shredder;
            try (Connection k = keys.connect("postgres"); Connection a = c.connect("postgres")) {
                shredder = new CryptoShredder(k);
                a.setAutoCommit(false);
                try (PreparedStatement p = a.prepareStatement("INSERT INTO customers_enc (subject, email, name) VALUES (?, ?, ?)")) {
                    for (int g = 1; g <= 10_000; g++) {
                        String subject = "subject-" + g;
                        p.setString(1, subject);
                        p.setBytes(2, shredder.encrypt(subject, g == 4242 ? "enc-" + email : "customer-" + g + "@example.in"));
                        p.setBytes(3, shredder.encrypt(subject, "Name " + g));
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                a.commit();
            }
            sql(c, "CHECKPOINT");
            Cluster.run(Cluster.bin("pg_basebackup"), "-h", "127.0.0.1", "-p", Integer.toString(c.port), "-U", "postgres", "-D", backup.toString(), "-X", "stream", "-c", "fast");
            byte[] encNeedle = ("enc-" + email).getBytes(StandardCharsets.UTF_8);
            System.out.println("customer subject-4242's email, byte occurrences in: table files | WAL | base backup taken before the erasure");
            System.out.printf("  %-44s plaintext table %s   encrypted table %s%n", "stored", where(c, backup, needle), where(c, backup, encNeedle));
            long t = System.nanoTime();
            sql(c, "DELETE FROM customers WHERE subject = 'subject-4242'", "CHECKPOINT");
            double deleteMs = (System.nanoTime() - t) / 1e6;
            System.out.printf("  %-44s plaintext table %s%n", "DELETE (and a checkpoint)", where(c, backup, needle));
            sql(c, "VACUUM customers", "CHECKPOINT");
            System.out.printf("  %-44s plaintext table %s%n", "VACUUM", where(c, backup, needle));
            sql(c, "VACUUM FULL customers", "CHECKPOINT");
            System.out.printf("  %-44s plaintext table %s%n", "VACUUM FULL", where(c, backup, needle));
            // crypto-shredding: delete the key; ciphertext stays everywhere, and is now unreadable
            byte[] keyBytes;
            byte[] cipher;
            try (Connection k = keys.connect("postgres"); Connection a = c.connect("postgres")) {
                CryptoShredder s = new CryptoShredder(k);
                try (Statement st = k.createStatement(); ResultSet r = st.executeQuery("SELECT secret FROM subject_keys WHERE subject = 'subject-4242'")) {
                    r.next();
                    keyBytes = r.getBytes(1);
                }
                try (Statement st = a.createStatement(); ResultSet r = st.executeQuery("SELECT email FROM customers_enc WHERE subject = 'subject-4242'")) {
                    r.next();
                    cipher = r.getBytes(1);
                }
                System.out.printf("  %-44s decrypts to %s%n", "encrypted row, before shredding", s.decrypt("subject-4242", cipher));
                t = System.nanoTime();
                s.shred("subject-4242");
                double shredMs = (System.nanoTime() - t) / 1e6;
                System.out.printf("  %-44s decrypts to %s (same ciphertext in the table, WAL and backup)%n", "after deleting subject-4242's key", s.decrypt("subject-4242", cipher));
                System.out.printf("  erase time: DELETE %.1f ms; key deletion %.1f ms%n", deleteMs, shredMs);
            }
            sql(keys, "CHECKPOINT");
            System.out.printf("  the deleted key's bytes in the key store: table files %d | WAL %d%n", ByteScan.count(keys.dir.resolve("base"), keyBytes),
                    ByteScan.count(keys.dir.resolve("pg_wal"), keyBytes));
            sql(keys, "VACUUM FULL subject_keys", "CHECKPOINT");
            System.out.printf("  ... after VACUUM FULL of the key table:          table files %d | WAL %d%n", ByteScan.count(keys.dir.resolve("base"), keyBytes),
                    ByteScan.count(keys.dir.resolve("pg_wal"), keyBytes));
        }
    }

    static String where(Cluster c, Path backup, byte[] needle) throws Exception {
        return ByteScan.count(c.dir.resolve("base"), needle) + " | " + ByteScan.count(c.dir.resolve("pg_wal"), needle) + " | " + ByteScan.count(backup, needle);
    }

    static void sql(Cluster c, String... statements) throws SQLException {
        try (Connection a = c.connect("postgres"); Statement s = a.createStatement()) {
            for (String x : statements) {
                s.execute(x);
            }
        }
    }

    static double pct(double[] v, double p) {
        double[] s = v.clone();
        Arrays.sort(s);
        return s[Math.max(0, Math.min(s.length - 1, (int) Math.ceil(p / 100 * s.length) - 1))];
    }
}
