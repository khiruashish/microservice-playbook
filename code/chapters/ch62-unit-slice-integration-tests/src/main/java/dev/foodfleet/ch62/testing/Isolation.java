package dev.foodfleet.ch62.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ways to give each test a clean database, on one PostgreSQL server: roll back a transaction the test ran in (what
 * Spring's {@code @Transactional} tests do), truncate the tables after each test, or clone a fresh database from a
 * template that already has the schema.
 */
public final class Isolation {

    public enum Strategy { NONE, ROLLBACK, TRUNCATE, TEMPLATE }

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private Isolation() {
    }

    /** {@code CREATE DATABASE ... TEMPLATE}: a file-level copy of the template, schema and seed data included. */
    public static String cloneFromTemplate(String serverUrl, String template) throws SQLException {
        String name = "t_" + ProcessHandle.current().pid() + "_" + COUNTER.incrementAndGet();
        try (Connection c = DriverManager.getConnection(serverUrl + "postgres", "postgres", ""); Statement s = c.createStatement()) {
            s.execute("create database " + name + " template " + template);
        }
        return name;
    }

    public static void drop(String serverUrl, String database) throws SQLException {
        try (Connection c = DriverManager.getConnection(serverUrl + "postgres", "postgres", ""); Statement s = c.createStatement()) {
            s.execute("drop database if exists " + database + " with (force)");
        }
    }

    public static void truncate(Connection c, String... tables) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("truncate " + String.join(", ", tables));
        }
    }
}
