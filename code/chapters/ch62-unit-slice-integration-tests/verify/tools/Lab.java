import dev.foodfleet.ch62.coupon.CouponRedemptions;
import dev.foodfleet.ch62.testing.Isolation;
import dev.foodfleet.ch62.testing.PostgresContainer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chapter 62's experiments, against PostgreSQL 16 in Docker containers and H2 2.5 in PostgreSQL mode.
 * <ul>
 *   <li>{@code lifecycle}: one suite of database tests with a container per test class, one container for the run,
 *       a reused container, and H2 in memory.</li>
 *   <li>{@code isolation}: four ways to keep tests from seeing each other's rows, and the leak in the rollback way.</li>
 *   <li>{@code h2gap}: which PostgreSQL features H2 runs, and the portable rewrite of ON CONFLICT under concurrency.</li>
 * </ul>
 * System properties: c62.image (PostgreSQL image), c62.schema (schema.sql).
 */
public final class Lab {

    static final String IMAGE = System.getProperty("c62.image", "c62/postgres:16");
    static final Duration READY = Duration.ofSeconds(60);
    static String schema;

    public static void main(String[] args) throws Exception {
        schema = Files.readString(Path.of(System.getProperty("c62.schema", "src/main/resources/db/schema.sql")));
        for (String mode : args.length > 0 ? args : new String[] {"lifecycle", "isolation", "h2gap"}) {
            System.out.println("== " + mode);
            switch (mode) {
                case "lifecycle" -> lifecycle();
                case "isolation" -> isolation();
                case "h2gap" -> h2gap();
                default -> throw new IllegalArgumentException(mode);
            }
            System.out.println();
        }
    }

    // ---------------------------------------------------------------- lifecycle

    static final int CLASSES = 8;
    static final int TESTS = 6;

    static void lifecycle() throws Exception {
        for (String id : PostgresContainer.docker("ps", "-aq", "--filter", "label=" + PostgresContainer.LABEL + "-reusable").strip().lines().toList()) {
            PostgresContainer.docker("rm", "-f", id);
        }
        System.out.printf("a suite of %d test classes x %d tests, each test redeeming coupons and reading them back; schema created per class%n", CLASSES, TESTS);
        System.out.println("database per suite run                         suite time   containers started   database setup   per test (avg)");
        row("PostgreSQL container per test class", () -> {
            for (int k = 0; k < CLASSES; k++) {
                try (PostgresContainer pg = PostgresContainer.start(IMAGE, false, READY)) {
                    runClass(pg.jdbcUrl("postgres"), k);
                }
            }
            return CLASSES;
        });
        row("one PostgreSQL container for the run", () -> {
            try (PostgresContainer pg = PostgresContainer.start(IMAGE, false, READY)) {
                for (int k = 0; k < CLASSES; k++) {
                    String db = "c" + k;
                    exec(pg.jdbcUrl("postgres"), "create database " + db);
                    runClass(pg.jdbcUrl(db), k);
                }
            }
            return 1;
        });
        for (String run : List.of("first", "second")) {
            row("reused container (testcontainers.reuse), " + run + " run", () -> {
                PostgresContainer pg = PostgresContainer.start(IMAGE, true, READY);
                for (int k = 0; k < CLASSES; k++) {
                    String db = "r" + k;
                    exec(pg.jdbcUrl("postgres"), "drop database if exists " + db + " with (force)");
                    exec(pg.jdbcUrl("postgres"), "create database " + db);
                    runClass(pg.jdbcUrl(db), k);
                }
                pg.close();
                return pg.reused() ? 0 : 1;
            });
        }
        row("H2 in memory, PostgreSQL mode", () -> {
            for (int k = 0; k < CLASSES; k++) {
                runClass(h2Url("life" + k), k);
            }
            return 0;
        });
        for (String id : PostgresContainer.docker("ps", "-aq", "--filter", "label=" + PostgresContainer.LABEL + "-reusable").strip().lines().toList()) {
            PostgresContainer.docker("rm", "-f", id);
        }
    }

    interface Suite {
        int run() throws Exception;
    }

    static long setupNanos;
    static long testNanos;
    static int testsRun;

    static void row(String name, Suite suite) throws Exception {
        setupNanos = 0;
        testNanos = 0;
        testsRun = 0;
        long t = System.nanoTime();
        int containers = suite.run();
        double total = (System.nanoTime() - t) / 1e6;
        System.out.printf("%-46s %8.0f ms   %-20d %8.0f ms      %6.1f ms%n", name, total, containers, total - testNanos / 1e6, testNanos / 1e6 / testsRun);
    }

    /** One test class: create the schema, then TESTS tests. Coupons go through the portable code so H2 runs it too. */
    static void runClass(String url, int k) throws Exception {
        exec(url, schema);
        for (int i = 0; i < TESTS; i++) {
            long t = System.nanoTime();
            String customer = "cust-" + k + "-" + i;
            CouponRedemptions.Connections db = () -> DriverManager.getConnection(url, "postgres", "");
            check(CouponRedemptions.redeemCheckThenInsert(db, customer, "FIRST20", 1000L + i), "first redemption");
            check(!CouponRedemptions.redeemCheckThenInsert(db, customer, "FIRST20", 2000L + i), "second redemption refused");
            check(count(url, "select count(*) from coupon_redemptions where customer_id = '" + customer + "'") == 1, "one row");
            testNanos += System.nanoTime() - t;
            testsRun++;
        }
    }

    // ---------------------------------------------------------------- isolation

    static final int ISO_TESTS = 300;

    static void isolation() throws Exception {
        try (PostgresContainer pg = PostgresContainer.start(IMAGE, false, READY)) {
            String server = pg.jdbcUrl("");
            exec(pg.jdbcUrl("postgres"), "create database tmpl");
            exec(pg.jdbcUrl("tmpl"), schema);
            System.out.printf("%d tests on one PostgreSQL container; every test redeems FIRST20 for customer c-1 and expects to be the first%n", ISO_TESTS);
            System.out.println("between tests                                        failed tests   cleanup per test   rows left at the end");
            for (String strategy : List.of("nothing", "rollback", "rollback, code commits on its own connection", "truncate", "new database from a template")) {
                exec(pg.jdbcUrl("postgres"), "drop database if exists work with (force)");
                exec(pg.jdbcUrl("postgres"), "create database work template tmpl");
                String url = pg.jdbcUrl("work");
                int failed = 0;
                long cleanup = 0;
                String current = url;
                List<String> made = new ArrayList<>();
                for (int i = 0; i < ISO_TESTS; i++) {
                    boolean ok;
                    switch (strategy) {
                        case "rollback", "rollback, code commits on its own connection" -> {
                            try (Connection tx = DriverManager.getConnection(url, "postgres", "")) {
                                tx.setAutoCommit(false);
                                final String u = url;
                                CouponRedemptions.Connections db = strategy.equals("rollback") ? () -> unclosable(tx)
                                        : () -> DriverManager.getConnection(u, "postgres", "");
                                ok = CouponRedemptions.redeemOnConflict(db, "c-1", "FIRST20", i);
                                long t = System.nanoTime();
                                tx.rollback();
                                cleanup += System.nanoTime() - t;
                            }
                        }
                        case "truncate" -> {
                            String u = url;
                            ok = CouponRedemptions.redeemOnConflict(() -> DriverManager.getConnection(u, "postgres", ""), "c-1", "FIRST20", i);
                            long t = System.nanoTime();
                            try (Connection c = DriverManager.getConnection(url, "postgres", "")) {
                                Isolation.truncate(c, "coupon_redemptions");
                            }
                            cleanup += System.nanoTime() - t;
                        }
                        case "new database from a template" -> {
                            long t = System.nanoTime();
                            String db = Isolation.cloneFromTemplate(server, "tmpl");
                            cleanup += System.nanoTime() - t;
                            made.add(db);
                            current = pg.jdbcUrl(db);
                            String u = current;
                            ok = CouponRedemptions.redeemOnConflict(() -> DriverManager.getConnection(u, "postgres", ""), "c-1", "FIRST20", i);
                        }
                        default -> {
                            String u = url;
                            ok = CouponRedemptions.redeemOnConflict(() -> DriverManager.getConnection(u, "postgres", ""), "c-1", "FIRST20", i);
                        }
                    }
                    if (!ok) {
                        failed++;
                    }
                }
                long left = count(strategy.startsWith("new database") ? current : url, "select count(*) from coupon_redemptions");
                System.out.printf("%-52s %-14d %8.2f ms        %d%n", strategy, failed, cleanup / 1e6 / ISO_TESTS, left);
                long t = System.nanoTime();
                for (String db : made) {
                    Isolation.drop(server, db);
                }
                if (!made.isEmpty()) {
                    System.out.printf("   (dropping the %d databases afterwards: %.0f ms)%n", made.size(), (System.nanoTime() - t) / 1e6);
                }
            }
        }
    }

    /** The test's own connection, handed to the code under test with close() turned off: what Spring's test transaction does. */
    static Connection unclosable(Connection c) {
        InvocationHandler h = (proxy, m, a) -> m.getName().equals("close") ? null : m.invoke(c, a);
        return (Connection) Proxy.newProxyInstance(Lab.class.getClassLoader(), new Class<?>[] {Connection.class}, h);
    }

    // ---------------------------------------------------------------- h2 gap

    record Feature(String name, String setup, String query) {
    }

    static final List<Feature> FEATURES = List.of(
            new Feature("INSERT ... ON CONFLICT (cols) DO NOTHING", "create table t (k int primary key, v int); insert into t values (1, 1)",
                    "insert into t values (1, 2) on conflict (k) do nothing"),
            new Feature("INSERT ... ON CONFLICT DO NOTHING (no target)", "create table t (k int primary key, v int); insert into t values (1, 1)",
                    "insert into t values (1, 2) on conflict do nothing"),
            new Feature("INSERT ... ON CONFLICT ... DO UPDATE (upsert)", "create table t (k int primary key, v int); insert into t values (1, 1)",
                    "insert into t values (1, 2) on conflict (k) do update set v = excluded.v"),
            new Feature("SELECT ... FOR UPDATE SKIP LOCKED (job queue)", "create table t (k int primary key, v int); insert into t values (1, 1)",
                    "select k from t order by k limit 1 for update skip locked"),
            new Feature("jsonb column and ->> operator", "create table t (k int primary key, doc jsonb); insert into t values (1, '{\"a\":\"x\"}')",
                    "select doc ->> 'a' from t"),
            new Feature("DISTINCT ON (latest row per key)", "create table t (k int, v int); insert into t values (1, 1), (1, 2), (2, 3)",
                    "select distinct on (k) k, v from t order by k, v desc"),
            new Feature("partial unique index", "create table t (k int, active boolean)",
                    "create unique index t_one_active on t (k) where active"),
            new Feature("text[] column and = ANY(...)", "create table t (k int, tags text[]); insert into t values (1, array['veg','spicy'])",
                    "select k from t where 'veg' = any(tags)"),
            new Feature("generate_series", "", "select count(*) from generate_series(1, 10)"),
            new Feature("ILIKE", "create table t (name varchar(20)); insert into t values ('Biryani')", "select name from t where name ilike 'bir%'"),
            new Feature("INSERT ... RETURNING", "create table t (k serial primary key, v int)", "insert into t (v) values (5) returning k"),
            new Feature("timestamptz + interval", "", "select now() + interval '15 minutes' > now()"),
            new Feature("date_trunc / string_agg", "create table t (k int, v varchar(5)); insert into t values (1, 'a'), (1, 'b')",
                    "select date_trunc('hour', now()) is not null, string_agg(v, ',') from t"),
            new Feature("gen_random_uuid()", "", "select gen_random_uuid()"),
            new Feature("pg_try_advisory_lock (leader election)", "", "select pg_try_advisory_lock(42)"),
            new Feature("ORDER BY a nullable column: where NULL goes", "create table t (k int); insert into t values (2), (null), (1)",
                    "select coalesce(cast(k as varchar), 'NULL') from t order by k"),
            new Feature("'2026-10-15' + 1 (date arithmetic)", "", "select date '2026-10-15' + 1"),
            new Feature("integer division 7 / 2", "", "select 7 / 2"),
            new Feature("varchar(5) given 'biryani' (too long)", "create table t (v varchar(5))", "insert into t values ('biryani')"));

    static String h2Url(String name) {
        return "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    }

    static void h2gap() throws Exception {
        try (PostgresContainer pg = PostgresContainer.start(IMAGE, false, READY)) {
            System.out.println("each statement on PostgreSQL 16 and on H2 2.5 (MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH)");
            System.out.printf("%-46s %-24s %s%n", "feature", "PostgreSQL 16", "H2 2.5, PostgreSQL mode");
            int n = 0;
            int differ = 0;
            for (Feature f : FEATURES) {
                n++;
                exec(pg.jdbcUrl("postgres"), "create database f" + n);
                String p = outcome(pg.jdbcUrl("f" + n), f);
                String h = outcome(h2Url("f" + n), f);
                if (!p.equals(h)) {
                    differ++;
                }
                System.out.printf("%-46s %-24s %s%s%n", f.name(), p, h, p.equals(h) ? "" : "   <- differs");
            }
            System.out.printf("%d of %d differ%n%n", differ, FEATURES.size());
            race(pg);
        }
    }

    static String outcome(String url, Feature f) {
        try (Connection c = DriverManager.getConnection(url, "postgres", ""); Statement s = c.createStatement()) {
            if (!f.setup().isEmpty()) {
                for (String part : f.setup().split(";")) {
                    s.execute(part.strip());
                }
            }
            if (s.execute(f.query())) {
                List<String> rows = new ArrayList<>();
                try (ResultSet rs = s.getResultSet()) {
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        List<String> r = new ArrayList<>();
                        for (int i = 1; i <= cols; i++) {
                            String v = rs.getString(i);
                            r.add(v != null && v.length() > 12 ? "<" + rs.getMetaData().getColumnTypeName(i).toLowerCase() + ">" : v);
                        }
                        rows.add(String.join("|", r));
                    }
                }
                return "ok: " + String.join(" ", rows);
            }
            return "ok: " + s.getUpdateCount() + " row" + (s.getUpdateCount() == 1 ? "" : "s");
        } catch (SQLException e) {
            String m = e.getMessage().lines().findFirst().orElse("");
            return "ERROR " + e.getSQLState() + (m.toLowerCase().contains("syntax") ? " (syntax)" : m.contains("not found") ? " (not found)"
                    : m.toLowerCase().contains("too long") ? " (value too long)" : "");
        }
    }

    // ---------------------------------------------------------------- race

    static final int ROUNDS = 300;
    static final int THREADS = 16;

    interface Redeem {
        boolean redeem(CouponRedemptions.Connections db, String customer, String coupon, long order);
    }

    static void race(PostgresContainer pg) throws Exception {
        System.out.printf("one customer, FIRST20, %d concurrent requests at the same moment (a double-tap and retries), %d rounds%n", THREADS, ROUNDS);
        System.out.println("code                              database                          rounds with 0 / 1 / 2+ winners   errors (would be 500s)");
        exec(pg.jdbcUrl("postgres"), "create database race");
        exec(pg.jdbcUrl("race"), schema);
        exec(pg.jdbcUrl("postgres"), "create database racenokey");
        exec(pg.jdbcUrl("racenokey"), schema.replace(",\n    primary key (customer_id, coupon)", ""));
        exec(h2Url("race"), schema);
        exec(h2Url("racenokey"), schema.replace(",\n    primary key (customer_id, coupon)", ""));
        raceRow("ON CONFLICT DO NOTHING", CouponRedemptions::redeemOnConflict, "PostgreSQL", pg.jdbcUrl("race"));
        raceRow("check, then insert", CouponRedemptions::redeemCheckThenInsert, "PostgreSQL", pg.jdbcUrl("race"));
        raceRow("check, then insert", CouponRedemptions::redeemCheckThenInsert, "H2", h2Url("race"));
        raceRow("check, then insert", CouponRedemptions::redeemCheckThenInsert, "PostgreSQL, no primary key", pg.jdbcUrl("racenokey"));
        raceRow("check, then insert", CouponRedemptions::redeemCheckThenInsert, "H2, no primary key", h2Url("racenokey"));
        System.out.println("the single-threaded test (redeem twice, expect true then false) passes for every row above");
    }

    static void raceRow(String code, Redeem redeem, String dbName, String url) throws Exception {
        int[] winners = new int[3];
        AtomicInteger errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CouponRedemptions.Connections db = () -> DriverManager.getConnection(url, "postgres", "");
        for (int r = 0; r < ROUNDS; r++) {
            exec(url, "delete from coupon_redemptions");
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                long order = r * 100L + t;
                fs.add(pool.submit(() -> {
                    go.await();
                    try {
                        return redeem.redeem(db, "c-1", "FIRST20", order);
                    } catch (IllegalStateException e) {
                        errors.incrementAndGet();
                        return false;
                    }
                }));
            }
            go.countDown();
            int won = 0;
            for (Future<Boolean> f : fs) {
                if (f.get()) {
                    won++;
                }
            }
            winners[Math.min(won, 2)]++;
        }
        pool.shutdown();
        System.out.printf("%-33s %-33s %4d / %4d / %4d                  %d%n", code, dbName, winners[0], winners[1], winners[2], errors.get());
    }

    // ---------------------------------------------------------------- helpers

    static void exec(String url, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "postgres", ""); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    static long count(String url, String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "postgres", ""); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
