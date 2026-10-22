import dev.foodfleet.ch69.sink.Events;
import dev.foodfleet.ch69.sink.OrderSink;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Chapter 69's sink experiment: a consumer's polls written to PostgreSQL (in Docker, with its default durability:
 * every commit is flushed to disk) one record at a time or a poll at a time, in four ways. The producer and consumer
 * experiments are in {@code kafkalab} (Go), run by run.sh.
 * System properties: c69.image (default c62/postgres:16).
 */
public final class Lab {

    public static void main(String[] args) throws Exception {
        for (String mode : args) {
            System.out.println("== " + mode);
            if (!mode.equals("sink")) {
                throw new IllegalArgumentException(mode);
            }
            sink();
            System.out.println();
        }
    }

    static void sink() throws Exception {
        String image = System.getProperty("c69.image", "c62/postgres:16");
        String id = sh("docker", "run", "-d", "--rm", "-p", "127.0.0.1::5432", image).strip();
        try {
            String port = sh("docker", "port", id, "5432/tcp").strip().lines().findFirst().orElseThrow().replaceAll(".*:", "");
            String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
            Connection db = null;
            for (int i = 0; i < 200 && db == null; i++) {
                try {
                    db = DriverManager.getConnection(url, "postgres", "postgres");
                } catch (Exception e) {
                    Thread.sleep(100);
                }
            }
            System.out.println("PostgreSQL 16 in Docker, synchronous commits (the default); OrderPlaced events of about 300 bytes; insert ... on conflict do nothing;");
            System.out.println("then the last fifth of the events is delivered again, as after a consumer restart (at-least-once)");
            System.out.printf("%-58s %10s %8s %10s %12s %14s%n", "write", "poll size", "events", "commits", "rows/s", "after replay");
            Object[][] rows = {
                    {OrderSink.Strategy.PER_RECORD, 500, 10_000, false}, {OrderSink.Strategy.JDBC_BATCH, 50, 100_000, false},
                    {OrderSink.Strategy.JDBC_BATCH, 500, 100_000, false}, {OrderSink.Strategy.JDBC_BATCH, 5000, 100_000, false},
                    {OrderSink.Strategy.JDBC_BATCH, 500, 100_000, true}, {OrderSink.Strategy.MULTI_ROW, 500, 100_000, false},
                    {OrderSink.Strategy.COPY, 500, 100_000, false}, {OrderSink.Strategy.COPY, 5000, 100_000, false}};
            for (Object[] row : rows) {
                OrderSink.Strategy s = (OrderSink.Strategy) row[0];
                int poll = (int) row[1];
                int n = (int) row[2];
                boolean rewrite = (boolean) row[3];
                try (Connection c = DriverManager.getConnection(url + (rewrite ? "?reWriteBatchedInserts=true" : ""), "postgres", "postgres")) {
                    OrderSink sink = new OrderSink(c);
                    try (Statement st = c.createStatement()) {
                        st.execute("truncate order_event");
                        st.execute("checkpoint");
                    }
                    List<OrderSink.OrderPlaced> all = Events.orders(0, n);
                    long t = System.nanoTime();
                    long commits = 0;
                    for (int i = 0; i < n; i += poll) {
                        sink.write(all.subList(i, Math.min(n, i + poll)), s);
                        commits += s == OrderSink.Strategy.PER_RECORD ? Math.min(poll, n - i) : 1;
                    }
                    double secs = (System.nanoTime() - t) / 1e9;
                    for (int i = n - n / 5; i < n; i += poll) {
                        sink.write(all.subList(i, Math.min(n, i + poll)), s);
                    }
                    long count;
                    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("select count(*) from order_event")) {
                        rs.next();
                        count = rs.getLong(1);
                    }
                    String label = switch (s) {
                        case PER_RECORD -> "one insert, one commit per record";
                        case JDBC_BATCH -> rewrite ? "a poll per transaction, JDBC batch, reWriteBatchedInserts" : "a poll per transaction, JDBC batch";
                        case MULTI_ROW -> "a poll per transaction, one multi-row insert";
                        case COPY -> "a poll per transaction, COPY to a temp table + insert";
                    };
                    System.out.printf("%-58s %10s %8d %10d %12.0f %9d rows%n", label, s == OrderSink.Strategy.PER_RECORD ? "-" : String.valueOf(poll), n, commits,
                            n / secs, count);
                }
            }
        } finally {
            sh("docker", "rm", "-f", id);
        }
    }

    static String sh(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        return out;
    }
}
