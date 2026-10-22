package dev.foodfleet.ch62;

import dev.foodfleet.ch62.testing.Isolation;
import dev.foodfleet.ch62.testing.PostgresContainer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

/**
 * One PostgreSQL container for the whole test run, kept in the root context's store so JUnit closes it after the
 * last test (values that are {@link AutoCloseable} are closed when their store is). Each test gets its own database,
 * cloned from a template that already has the schema, and dropped afterwards.
 */
public final class PostgresExtension implements ParameterResolver, AfterEachCallback {

    /** What a test receives: the JDBC URL of its own database. */
    public record TestDatabase(String url) {
        public Connection open() throws java.sql.SQLException {
            return DriverManager.getConnection(url, "postgres", "");
        }
    }

    record Server(PostgresContainer container) implements AutoCloseable {
        @Override
        public void close() {
            container.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext p, ExtensionContext c) {
        return p.getParameter().getType() == TestDatabase.class;
    }

    @Override
    public Object resolveParameter(ParameterContext p, ExtensionContext c) {
        Server server = c.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).computeIfAbsent(Server.class, k -> start(), Server.class);
        try {
            String db = Isolation.cloneFromTemplate(server.container().jdbcUrl(""), "template_orders");
            c.getStore(ExtensionContext.Namespace.create(PostgresExtension.class)).put("db", db);
            return new TestDatabase(server.container().jdbcUrl(db));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext c) throws Exception {
        String db = c.getStore(ExtensionContext.Namespace.create(PostgresExtension.class)).remove("db", String.class);
        if (db != null) {
            Server server = c.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).get(Server.class, Server.class);
            Isolation.drop(server.container().jdbcUrl(""), db);
        }
    }

    static Server start() {
        PostgresContainer pg = PostgresContainer.start(System.getProperty("ff.pg.image", "c62/postgres:16"), Boolean.getBoolean("ff.pg.reuse"), Duration.ofSeconds(60));
        try (Connection c = DriverManager.getConnection(pg.jdbcUrl("postgres"), "postgres", ""); Statement s = c.createStatement()) {
            s.execute("drop database if exists template_orders");
            s.execute("create database template_orders");
        } catch (Exception e) {
            pg.close();
            throw new IllegalStateException(e);
        }
        try (Connection c = DriverManager.getConnection(pg.jdbcUrl("template_orders"), "postgres", ""); Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of(System.getProperty("ff.schema", "src/main/resources/db/schema.sql"))));
        } catch (Exception e) {
            pg.close();
            throw new IllegalStateException(e);
        }
        return new Server(pg);
    }
}
