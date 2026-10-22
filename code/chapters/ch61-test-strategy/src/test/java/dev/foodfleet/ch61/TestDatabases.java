package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.JdbcOrderRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/** Fresh databases with the schema applied: H2 in its PostgreSQL mode, or a real PostgreSQL. */
final class TestDatabases {

    static final AtomicInteger COUNTER = new AtomicInteger();

    private TestDatabases() {
    }

    static String schema() {
        try (InputStream in = TestDatabases.class.getResourceAsStream("/db/schema.sql")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** An in-memory H2 database in PostgreSQL compatibility mode, as many teams use for "fast" repository tests. */
    static JdbcOrderRepository.Connections h2() throws SQLException {
        String url = "jdbc:h2:mem:orders" + COUNTER.incrementAndGet() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute(schema());
        }
        return () -> DriverManager.getConnection(url, "sa", "");
    }

    /** A new database on the PostgreSQL server named by -Dff.pg.url (the server Testcontainers would start). */
    static JdbcOrderRepository.Connections postgres() throws SQLException {
        String server = System.getProperty("ff.pg.url", "jdbc:postgresql://127.0.0.1:5461/");
        String name = "orders_" + ProcessHandle.current().pid() + "_" + COUNTER.incrementAndGet();
        try (Connection c = DriverManager.getConnection(server + "postgres", "postgres", ""); Statement s = c.createStatement()) {
            s.execute("create database " + name);
        }
        try (Connection c = DriverManager.getConnection(server + name, "postgres", ""); Statement s = c.createStatement()) {
            s.execute(schema());
        }
        return () -> DriverManager.getConnection(server + name, "postgres", "");
    }
}
