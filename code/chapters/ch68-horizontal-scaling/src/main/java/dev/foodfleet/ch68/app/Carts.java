package dev.foodfleet.ch68.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a user's cart lives between requests: the state that decides whether order-service can run as many
 * interchangeable copies.
 */
public interface Carts {

    /** Adds one item and returns how many the cart now holds. */
    int add(String user) throws Exception;

    int count(String user) throws Exception;

    /** In this process's memory, like an HTTP session without Spring Session: gone with the process. */
    final class InMemory implements Carts {

        private final Map<String, Integer> carts = new ConcurrentHashMap<>();

        @Override
        public int add(String user) {
            return carts.merge(user, 1, Integer::sum);
        }

        @Override
        public int count(String user) {
            return carts.getOrDefault(user, 0);
        }
    }

    /** In PostgreSQL, shared by every copy, as Spring Session JDBC (or Redis) would keep it. */
    final class Shared implements Carts {

        private final BlockingQueue<Connection> pool;

        public Shared(String url, String user, String password, int connections) throws SQLException {
            pool = new ArrayBlockingQueue<>(connections);
            for (int i = 0; i < connections; i++) {
                pool.add(DriverManager.getConnection(url, user, password));
            }
            try (var st = pool.peek().createStatement()) {
                st.execute("create table if not exists cart (user_id text primary key, items int not null)");
            } catch (SQLException e) {
                // another replica created it at the same moment
            }
        }

        @Override
        public int add(String user) throws Exception {
            Connection c = pool.take();
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into cart values (?, 1) on conflict (user_id) do update set items = cart.items + 1 returning items")) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                pool.put(c);
            }
        }

        @Override
        public int count(String user) throws Exception {
            Connection c = pool.take();
            try (PreparedStatement ps = c.prepareStatement("select items from cart where user_id = ?")) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } finally {
                pool.put(c);
            }
        }
    }
}
