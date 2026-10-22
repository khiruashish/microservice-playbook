package dev.foodfleet.ch61.order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Orders in PostgreSQL (schema in db/schema.sql), with plain JDBC. */
public final class JdbcOrderRepository implements OrderRepository {

    /** Opens a connection; a pool in production. */
    public interface Connections {
        Connection open() throws SQLException;
    }

    private final Connections db;

    public JdbcOrderRepository(Connections db) {
        this.db = db;
    }

    @Override
    public Stored insertIfAbsent(Order o) {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement("""
                        insert into orders (idempotency_key, customer_id, restaurant_id, subtotal, discount, tax, total, status)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (idempotency_key) do nothing""", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, o.idempotencyKey());
                    ps.setString(2, o.customerId());
                    ps.setInt(3, o.restaurantId());
                    ps.setBigDecimal(4, o.subtotal());
                    ps.setBigDecimal(5, o.discount());
                    ps.setBigDecimal(6, o.tax());
                    ps.setBigDecimal(7, o.total());
                    ps.setString(8, o.status());
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return new Stored(findByKey(c, o.idempotencyKey()).orElseThrow(), false);
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("insert into order_lines (order_id, item_id, quantity, unit_price) values (?, ?, ?, ?)")) {
                    for (Order.Line l : o.lines()) {
                        ps.setLong(1, id);
                        ps.setString(2, l.itemId());
                        ps.setInt(3, l.quantity());
                        ps.setBigDecimal(4, l.unitPrice());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                return new Stored(findById(c, id).orElseThrow(), true);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Order> findById(long id) {
        try (Connection c = db.open()) {
            return findById(c, id);
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public List<Order> recent(String customerId, int limit) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(
                "select id from orders where customer_id = ? order by created_at desc, id desc limit ?")) {
            ps.setString(1, customerId);
            ps.setInt(2, limit);
            List<Order> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(findById(c, rs.getLong(1)).orElseThrow());
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public int countForRestaurant(int restaurantId) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement("select count(*) from orders where restaurant_id = ?")) {
            ps.setInt(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private Optional<Order> findByKey(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select id from orders where idempotency_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? findById(c, rs.getLong(1)) : Optional.empty();
            }
        }
    }

    private Optional<Order> findById(Connection c, long id) throws SQLException {
        List<Order.Line> lines = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("select item_id, quantity, unit_price from order_lines where order_id = ? order by item_id")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new Order.Line(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)));
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                select id, idempotency_key, customer_id, restaurant_id, subtotal, discount, tax, total, status from orders where id = ?""")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Order(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), List.copyOf(lines), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getString(9)));
            }
        }
    }
}
