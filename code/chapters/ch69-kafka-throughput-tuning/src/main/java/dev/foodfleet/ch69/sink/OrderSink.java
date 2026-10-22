package dev.foodfleet.ch69.sink;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/**
 * The write side of an order-events consumer: every OrderPlaced event becomes a row in PostgreSQL. Redelivered events
 * (Kafka delivers at least once) are ignored by the order ID's primary key, so every strategy is idempotent. The
 * strategies differ in how many records share a round trip and a commit.
 */
public final class OrderSink {

    public record OrderPlaced(String orderId, String restaurantId, long totalPaise, long placedAtMillis, String json) {
    }

    public enum Strategy {
        /** One INSERT and one commit per record: the shape of a listener that handles one record at a time. */
        PER_RECORD,
        /** One transaction per poll, the INSERTs sent together with JDBC batching. */
        JDBC_BATCH,
        /** One transaction per poll, one INSERT with a row of VALUES per record. */
        MULTI_ROW,
        /** One transaction per poll: COPY into a temporary table, then one INSERT ... SELECT that skips duplicates. */
        COPY
    }

    static final String INSERT = "insert into order_event (order_id, restaurant_id, total_paise, placed_at, payload) values (?, ?, ?, ?, ?::jsonb) "
            + "on conflict (order_id) do nothing";

    private final Connection db;

    public OrderSink(Connection db) throws SQLException {
        this.db = db;
        try (Statement st = db.createStatement()) {
            st.execute("create table if not exists order_event (order_id text primary key, restaurant_id text not null, total_paise bigint not null, "
                    + "placed_at timestamptz not null, payload jsonb not null)");
        }
    }

    /** Writes one poll's records; returns how many rows were new. */
    public int write(List<OrderPlaced> poll, Strategy strategy) throws Exception {
        return switch (strategy) {
            case PER_RECORD -> perRecord(poll);
            case JDBC_BATCH -> inTransaction(() -> jdbcBatch(poll));
            case MULTI_ROW -> inTransaction(() -> multiRow(poll));
            case COPY -> inTransaction(() -> copy(poll));
        };
    }

    int perRecord(List<OrderPlaced> poll) throws SQLException {
        db.setAutoCommit(true);
        int n = 0;
        try (PreparedStatement ps = db.prepareStatement(INSERT)) {
            for (OrderPlaced e : poll) {
                bind(ps, 1, e);
                n += ps.executeUpdate();
            }
        }
        return n;
    }

    int jdbcBatch(List<OrderPlaced> poll) throws SQLException {
        int n = 0;
        try (PreparedStatement ps = db.prepareStatement(INSERT)) {
            for (OrderPlaced e : poll) {
                bind(ps, 1, e);
                ps.addBatch();
            }
            for (int c : ps.executeBatch()) {
                n += Math.max(c, 0);
            }
        }
        return n;
    }

    int multiRow(List<OrderPlaced> poll) throws SQLException {
        StringBuilder sql = new StringBuilder("insert into order_event (order_id, restaurant_id, total_paise, placed_at, payload) values ");
        for (int i = 0; i < poll.size(); i++) {
            sql.append(i == 0 ? "" : ",").append("(?, ?, ?, ?, ?::jsonb)");
        }
        sql.append(" on conflict (order_id) do nothing");
        try (PreparedStatement ps = db.prepareStatement(sql.toString())) {
            int p = 1;
            for (OrderPlaced e : poll) {
                p = bind(ps, p, e);
            }
            return ps.executeUpdate();
        }
    }

    int copy(List<OrderPlaced> poll) throws Exception {
        try (Statement st = db.createStatement()) {
            st.execute("create temporary table if not exists order_event_in (like order_event) on commit delete rows");
        }
        StringBuilder csv = new StringBuilder(poll.size() * 512);
        for (OrderPlaced e : poll) {
            csv.append(e.orderId()).append('\t').append(e.restaurantId()).append('\t').append(e.totalPaise()).append('\t')
                    .append(new Timestamp(e.placedAtMillis())).append("+00").append('\t').append(e.json().replace("\\", "\\\\")).append('\n');
        }
        CopyManager copy = db.unwrap(PGConnection.class).getCopyAPI();
        copy.copyIn("copy order_event_in from stdin", new StringReader(csv.toString()));
        try (Statement st = db.createStatement()) {
            return st.executeUpdate("insert into order_event select * from order_event_in on conflict (order_id) do nothing");
        }
    }

    static int bind(PreparedStatement ps, int p, OrderPlaced e) throws SQLException {
        ps.setString(p++, e.orderId());
        ps.setString(p++, e.restaurantId());
        ps.setLong(p++, e.totalPaise());
        ps.setTimestamp(p++, new Timestamp(e.placedAtMillis()));
        ps.setString(p++, e.json());
        return p;
    }

    interface Work {
        int run() throws Exception;
    }

    int inTransaction(Work w) throws Exception {
        db.setAutoCommit(false);
        try {
            int n = w.run();
            db.commit();
            return n;
        } catch (Exception e) {
            db.rollback();
            throw e;
        }
    }
}
