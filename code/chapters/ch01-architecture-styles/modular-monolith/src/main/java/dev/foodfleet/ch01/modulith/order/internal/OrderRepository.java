package dev.foodfleet.ch01.modulith.order.internal;

import dev.foodfleet.ch01.modulith.order.OrderStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persists orders in the {@code ordering} schema. Only the order module touches these tables.
 * This is "database per service" applied inside one database, and it makes extraction a
 * schema move instead of a data untangling project.
 */
@Repository
public class OrderRepository {

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Order order) {
        jdbc.sql("""
                insert into ordering.orders (id, customer_id, restaurant_id, status, total_paise, placed_at)
                values (:id, :customerId, :restaurantId, :status, :totalPaise, :placedAt)
                """)
                .param("id", order.id())
                .param("customerId", order.customerId())
                .param("restaurantId", order.restaurantId())
                .param("status", order.status().name())
                .param("totalPaise", order.totalPaise())
                .param("placedAt", Timestamp.from(order.placedAt()))
                .update();

        List<OrderLine> lines = order.lines();
        for (int i = 0; i < lines.size(); i++) {
            OrderLine line = lines.get(i);
            jdbc.sql("""
                    insert into ordering.order_line (order_id, line_no, item_id, item_name, quantity, unit_price_paise)
                    values (:orderId, :lineNo, :itemId, :itemName, :quantity, :unitPricePaise)
                    """)
                    .param("orderId", order.id())
                    .param("lineNo", i + 1)
                    .param("itemId", line.itemId())
                    .param("itemName", line.name())
                    .param("quantity", line.quantity())
                    .param("unitPricePaise", line.unitPricePaise())
                    .update();
        }
    }

    public Optional<Order> findById(UUID orderId) {
        List<OrderLine> lines = jdbc.sql("""
                        select item_id, item_name, quantity, unit_price_paise
                        from ordering.order_line where order_id = :orderId order by line_no
                        """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new OrderLine(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getLong("unit_price_paise")))
                .list();

        return jdbc.sql("""
                        select id, customer_id, restaurant_id, status, placed_at
                        from ordering.orders where id = :id
                        """)
                .param("id", orderId)
                .query((rs, rowNum) -> new Order(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_id"),
                        rs.getString("restaurant_id"),
                        lines,
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("placed_at").toInstant()))
                .optional();
    }
}
