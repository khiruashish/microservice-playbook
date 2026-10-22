package dev.foodfleet.ch15.app.inbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records that a consumer has processed a message. Must run in the SAME transaction as the
 * consumer's effect: if the effect rolls back, so does the record, and the redelivery is processed.
 */
@Component
public class Inbox {

    private final JdbcClient jdbc;

    public Inbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True the first time; false for a duplicate. The primary key does the work, even under concurrency. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, UUID messageId) {
        return jdbc.sql("""
                        insert into inbox (consumer, message_id) values (:consumer, :id)
                        on conflict do nothing
                        """)
                .param("consumer", consumer)
                .param("id", messageId)
                .update() == 1;
    }
}
