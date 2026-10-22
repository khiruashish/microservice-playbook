package dev.foodfleet.ch15.app.inbox;

import dev.foodfleet.ch15.app.order.OrderPlaced;
import dev.foodfleet.ch15.app.outbox.OutboxMessage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * loyalty-service's handler for OrderPlaced. Inbox check and points update in one transaction;
 * the broker offset is committed only after this returns (chapter 25).
 */
@Component
public class LoyaltyPointsHandler {

    private final Inbox inbox;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public LoyaltyPointsHandler(Inbox inbox, JdbcClient jdbc, JsonMapper json) {
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Returns false if the message was a duplicate. */
    @Transactional
    public boolean handle(OutboxMessage message) {
        if (!inbox.firstTime("loyalty-service", message.id())) {
            return false;
        }
        OrderPlaced event = json.readValue(message.payload(), OrderPlaced.class);
        jdbc.sql("""
                        insert into loyalty_points (customer_id, points) values (:customer, :points)
                        on conflict (customer_id) do update set points = loyalty_points.points + excluded.points
                        """)
                .param("customer", event.customerId())
                .param("points", event.points())
                .update();
        return true;
    }
}
