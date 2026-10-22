package dev.foodfleet.ch15.outbox;

import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import java.util.List;

/**
 * Log tailing (change data capture), as Debezium does it: read the database's write-ahead log,
 * pick out INSERTs into the outbox table and publish them. No polling queries, no "published"
 * column, commit order for free, and the outbox rows may even be deleted in the same transaction.
 *
 * <p>The connector remembers its position (the LSN) by flushing an offset every
 * {@code flushEvery} records. A crash loses the unflushed part, so after a restart it re-reads and
 * re-publishes from the last flushed offset: at-least-once again, and consumers must deduplicate.
 */
public final class LogTailingRelay {

    private final Database db;
    private final Broker broker;
    private final Faults faults;
    private final int flushEvery;
    private long position;
    private long flushedOffset;
    private int sinceFlush;

    public LogTailingRelay(Database db, Broker broker, Faults faults, int flushEvery) {
        this.db = db;
        this.broker = broker;
        this.faults = faults;
        this.flushEvery = flushEvery;
    }

    /** Reads new WAL entries. Returns how many outbox events were published. */
    public int poll() {
        List<Database.Change> changes = db.walAfter(position);
        int published = 0;
        for (Database.Change change : changes) {
            if (change.table().equals(OutboxRow.TABLE) && change.op() == Database.Op.INSERT) {
                try {
                    broker.publish(((OutboxRow) change.row()).toMessage());
                } catch (Broker.PublishFailed e) {
                    return published;                        // retry from this LSN next time
                }
                published++;
                faults.maybeCrash("relay.after-publish");   // offset not flushed yet
            }
            position = change.lsn();
            if (++sinceFlush >= flushEvery) {
                flushedOffset = position;
                sinceFlush = 0;
            }
        }
        flushedOffset = position;                           // flushed when idle, as Debezium does on commit
        sinceFlush = 0;
        return published;
    }

    /** A new connector task resumes from the last flushed offset. */
    public void restart() {
        position = flushedOffset;
        sinceFlush = 0;
    }

    public long lag() {
        return db.walAfter(position).size();
    }
}
