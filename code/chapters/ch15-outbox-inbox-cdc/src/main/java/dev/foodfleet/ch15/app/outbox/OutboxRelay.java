package dev.foodfleet.ch15.app.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The polling publisher. Each run, in one transaction: lock a batch of unpublished rows with
 * {@code FOR UPDATE SKIP LOCKED} (other relay instances skip them instead of waiting), publish
 * them in order, and mark the ones the broker acknowledged. On the first failure it stops, so later
 * events never overtake earlier ones; the rest are retried on the next run.
 *
 * <p>At-least-once: if the process dies after publishing but before the commit, those rows are
 * published again. Consumers deduplicate on the message id.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final MessagePublisher publisher;
    private final OutboxProperties properties;

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate transaction, MessagePublisher publisher,
                       OutboxProperties properties) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${foodfleet.outbox.poll-interval-ms:500}",
               initialDelayString = "${foodfleet.outbox.poll-interval-ms:500}")
    public void run() {
        int published;
        do {
            published = publishBatch();           // keep going while there is a backlog
        } while (published == properties.batchSize());
    }

    /** Publishes one batch; returns how many rows were published and marked. */
    public int publishBatch() {
        Integer count = transaction.execute(status -> {
            List<OutboxMessage> batch = jdbc.sql("""
                            select position, id, aggregate_type, aggregate_id, type, payload::text as payload, created_at
                            from outbox
                            where published_at is null
                            order by position
                            limit :batch
                            for update skip locked
                            """)
                    .param("batch", properties.batchSize())
                    .query((rs, n) -> new OutboxMessage(rs.getLong("position"), rs.getObject("id", UUID.class),
                            rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getString("type"),
                            rs.getString("payload"), rs.getTimestamp("created_at").toInstant()))
                    .list();
            List<UUID> done = new ArrayList<>();
            for (OutboxMessage message : batch) {
                try {
                    publisher.publish(message);
                } catch (RuntimeException e) {
                    log.warn("publish failed for {} ({}); retrying next run", message.id(), e.getMessage());
                    break;
                }
                done.add(message.id());
            }
            if (!done.isEmpty()) {
                jdbc.sql("update outbox set published_at = now() where id in (:ids)").param("ids", done).update();
            }
            return done.size();
        });
        return count == null ? 0 : count;
    }

    /** Published rows are history: delete them after the retention period. */
    @Scheduled(cron = "0 17 3 * * *")
    public int deletePublished() {
        return jdbc.sql("delete from outbox where published_at < now() - make_interval(days => :days)")
                .param("days", properties.retentionDays())
                .update();
    }
}
