package dev.foodfleet.ch65.sample.infrastructure;

import dev.foodfleet.ch65.sample.application.OrderRepository;
import dev.foodfleet.ch65.sample.domain.order.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Stores orders with JDBC. */
public final class JdbcOrderRepository implements OrderRepository {

    private final Supplier<Connection> connections;

    public JdbcOrderRepository(Supplier<Connection> connections) {
        this.connections = connections;
    }

    @Override
    public long save(Order order) {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement("insert into " + OrderTable.NAME + " (customer_id, total) values (?, ?) returning id")) {
            ps.setString(1, order.customer());
            ps.setBigDecimal(2, order.total().amount());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
