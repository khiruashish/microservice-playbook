package dev.foodfleet.ch01.modulith.kitchen.internal;

import dev.foodfleet.ch01.modulith.kitchen.TicketView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Kitchen tickets live in the {@code kitchen} schema, owned by the kitchen module alone. */
@Repository
public class TicketRepository {

    private final JdbcClient jdbc;

    public TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a ticket unless one already exists for the order. Event delivery is at-least-once
     * (the publication registry re-delivers incomplete events on restart), so this write must be
     * idempotent. The unique constraint on order_id is the final safety net.
     *
     * @return true if a new ticket was created
     */
    public boolean createIfAbsent(UUID orderId, String restaurantId, Instant now) {
        try {
            return jdbc.sql("""
                            insert into kitchen.ticket (id, order_id, restaurant_id, status, created_at)
                            select cast(:id as uuid), cast(:orderId as uuid), cast(:restaurantId as varchar(64)),
                                   'QUEUED', cast(:createdAt as timestamp)
                            where not exists (select 1 from kitchen.ticket where order_id = :orderId)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("orderId", orderId)
                    .param("restaurantId", restaurantId)
                    .param("createdAt", Timestamp.from(now))
                    .update() == 1;
        } catch (DuplicateKeyException concurrentDuplicate) {
            return false; // two deliveries raced; the unique constraint kept exactly one ticket
        }
    }

    public int countActive(String restaurantId) {
        return jdbc.sql("""
                        select count(*) from kitchen.ticket
                        where restaurant_id = :restaurantId and status in ('QUEUED', 'PREPARING')
                        """)
                .param("restaurantId", restaurantId)
                .query(Integer.class)
                .single();
    }

    public Optional<TicketView> findByOrderId(UUID orderId) {
        return jdbc.sql("""
                        select id, order_id, restaurant_id, status, created_at
                        from kitchen.ticket where order_id = :orderId
                        """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new TicketView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getString("restaurant_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }
}
