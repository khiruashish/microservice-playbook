package dev.foodfleet.ch15.outbox;

import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import java.util.Comparator;
import java.util.List;

/**
 * Polling publisher: every few hundred milliseconds, read unpublished outbox rows in order, publish
 * each, then mark it published. Guarantees: every committed event is published <em>at least once</em>,
 * in commit order. Not exactly once: a crash (or a lost broker ack) between publishing and marking
 * means the row is published again on the next poll, so consumers must deduplicate.
 */
public final class PollingPublisher {

    private final Database db;
    private final Broker broker;
    private final Faults faults;
    private final int batchSize;

    public PollingPublisher(Database db, Broker broker, Faults faults, int batchSize) {
        this.db = db;
        this.broker = broker;
        this.faults = faults;
        this.batchSize = batchSize;
    }

    /** One poll. Returns how many rows were published and marked. */
    public int poll() {
        // SELECT ... FROM outbox WHERE published_at IS NULL ORDER BY sequence LIMIT :batch [FOR UPDATE SKIP LOCKED]
        List<OutboxRow> batch = db.table(OutboxRow.TABLE).values().stream()
                .map(OutboxRow.class::cast)
                .filter(row -> !row.published())
                .sorted(Comparator.comparingLong(OutboxRow::sequence))
                .limit(batchSize)
                .toList();
        int published = 0;
        for (OutboxRow row : batch) {
            try {
                broker.publish(row.toMessage());
            } catch (Broker.PublishFailed e) {
                // Stop here: publishing later rows first would reorder events for the same order.
                return published;
            }
            faults.maybeCrash("relay.after-publish");    // published but not marked: sent again later
            db.inTransaction(tx -> {
                tx.update(OutboxRow.TABLE, row.key(), row.markPublished());
                return null;
            });
            published++;
        }
        return published;
    }

    public long backlog() {
        return db.table(OutboxRow.TABLE).values().stream().map(OutboxRow.class::cast)
                .filter(row -> !row.published()).count();
    }
}
