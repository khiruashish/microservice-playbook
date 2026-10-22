package dev.foodfleet.ch21.orders;

import dev.foodfleet.ch21.paging.Cursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcOrderStore implements OrderStore {

    private static final String COLUMNS =
            "id, customer_id, restaurant, total_paise, status, placed_at, delivery_instructions, version";

    private final JdbcClient jdbc;

    JdbcOrderStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Keyset pagination. The row-value comparison {@code (placed_at, id) < (:t, :id)} matches the
     * index (customer_id, placed_at desc, id desc), so page 1,000 costs the same as page 1.
     */
    @Override
    public List<OrderSummary> page(String customerId, Optional<Cursor> after, int limit) {
        if (after.isEmpty()) {
            return jdbc.sql("select " + COLUMNS + " from orders where customer_id = :customer "
                            + "order by placed_at desc, id desc limit :limit")
                    .param("customer", customerId)
                    .param("limit", limit)
                    .query(JdbcOrderStore::map)
                    .list();
        }
        return jdbc.sql("select " + COLUMNS + " from orders where customer_id = :customer "
                        + "and (placed_at, id) < (:placedAt, :id) order by placed_at desc, id desc limit :limit")
                .param("customer", customerId)
                .param("placedAt", Timestamp.from(after.get().placedAt()))
                .param("id", after.get().orderId())
                .param("limit", limit)
                .query(JdbcOrderStore::map)
                .list();
    }

    @Override
    public Optional<OrderSummary> find(String orderId) {
        return jdbc.sql("select " + COLUMNS + " from orders where id = :id")
                .param("id", orderId)
                .query(JdbcOrderStore::map)
                .optional();
    }

    @Override
    @Transactional
    public OrderSummary updateDeliveryInstructions(String orderId, long expectedVersion, String instructions) {
        int updated = jdbc.sql("""
                        update orders set delivery_instructions = :instructions, version = version + 1
                        where id = :id and version = :expected
                        """)
                .param("instructions", instructions)
                .param("id", orderId)
                .param("expected", expectedVersion)
                .update();
        OrderSummary current = find(orderId).orElseThrow(() -> new OrderNotFound(orderId));
        if (updated == 0) {
            throw new VersionConflict(orderId, current.version());
        }
        return current;
    }

    private static OrderSummary map(ResultSet rs, int row) throws SQLException {
        return new OrderSummary(rs.getString("id"), rs.getString("customer_id"), rs.getString("restaurant"),
                rs.getLong("total_paise"), rs.getString("status"), rs.getTimestamp("placed_at").toInstant(),
                rs.getString("delivery_instructions"), rs.getLong("version"));
    }
}
