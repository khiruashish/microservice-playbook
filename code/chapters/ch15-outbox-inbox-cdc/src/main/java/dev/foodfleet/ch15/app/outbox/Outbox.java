package dev.foodfleet.ch15.app.outbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appends events to the outbox. {@code MANDATORY}: calling it outside a transaction is a bug (the
 * event would commit on its own, which is the dual write again), so Spring throws instead.
 */
@Component
public class Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public Outbox(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String aggregateType, String aggregateId, String type, Object payload) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        insert into outbox (id, aggregate_type, aggregate_id, type, payload)
                        values (:id, :aggregateType, :aggregateId, :type, cast(:payload as jsonb))
                        """)
                .param("id", id)
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("type", type)
                .param("payload", json.writeValueAsString(payload))
                .update();
        return id;
    }
}
