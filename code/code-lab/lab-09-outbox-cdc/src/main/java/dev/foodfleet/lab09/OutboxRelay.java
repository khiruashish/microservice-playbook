package dev.foodfleet.lab09;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The polling publisher, after chapter 15's. Each run, in one transaction: lock a batch of unpublished rows with
 * FOR UPDATE SKIP LOCKED (another relay instance skips them instead of waiting or publishing them twice), publish
 * them in order, waiting for the broker's acknowledgement, and mark the ones acknowledged. On the first failure it
 * stops, so later events never overtake earlier ones. At-least-once: a crash after publishing and before the commit
 * publishes those rows again, and consumers deduplicate on the event id (lab 07).
 */
@Component
public class OutboxRelay {

    record Row(long position, UUID id, String aggregateType, String aggregateId, String type, String payload) {
    }

    static final int BATCH = 100;

    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(JdbcClient db, TransactionTemplate tx, KafkaTemplate<String, String> kafka) {
        this.db = db;
        this.tx = tx;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${foodfleet.outbox.poll-interval:500ms}")
    public void run() {
        while (publishBatch() == BATCH) {
            // keep draining while there's a backlog
        }
    }

    public int publishBatch() {
        Integer published = tx.execute(status -> {
            List<Row> batch = db.sql("""
                            SELECT position, id, aggregate_type, aggregate_id, type, payload::text AS payload
                            FROM outbox
                            WHERE published_at IS NULL
                            ORDER BY position
                            LIMIT :batch
                            FOR UPDATE SKIP LOCKED
                            """)
                    .param("batch", BATCH)
                    .query((rs, n) -> new Row(rs.getLong("position"), rs.getObject("id", UUID.class), rs.getString("aggregate_type"),
                            rs.getString("aggregate_id"), rs.getString("type"), rs.getString("payload")))
                    .list();
            List<UUID> done = new ArrayList<>();
            for (Row row : batch) {
                try {
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                            "foodfleet.ordering." + row.aggregateType() + "-events", row.aggregateId(), row.payload());
                    record.headers().add("event-id", row.id().toString().getBytes(StandardCharsets.UTF_8));
                    record.headers().add("event-type", row.type().getBytes(StandardCharsets.UTF_8));
                    kafka.send(record).get(5, TimeUnit.SECONDS);       // acknowledged (acks=all) before it's marked
                } catch (Exception e) {
                    break;                                           // the rest wait for the next run, in order
                }
                done.add(row.id());
            }
            if (!done.isEmpty()) {
                db.sql("UPDATE outbox SET published_at = now() WHERE id IN (:ids)").param("ids", done).update();
            }
            return done.size();
        });
        return published == null ? 0 : published;
    }
}
