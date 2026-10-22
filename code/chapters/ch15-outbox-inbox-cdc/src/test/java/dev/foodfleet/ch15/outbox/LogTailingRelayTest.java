package dev.foodfleet.ch15.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Crash;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import dev.foodfleet.ch15.sim.Message;
import org.junit.jupiter.api.Test;

class LogTailingRelayTest {

    private final Database db = new Database("ordering");
    private final OutboxOrderService service = new OutboxOrderService(db, Faults.NONE, true);

    @Test
    void publishesOutboxInsertsFromTheLogEvenThoughTheRowsAreGone() {
        service.place("o-1", "c-1", 45_000);
        service.cancel("o-1");
        Broker broker = new Broker(Faults.NONE);

        new LogTailingRelay(db, broker, Faults.NONE, 100).poll();

        assertThat(db.table(OutboxRow.TABLE)).isEmpty();
        assertThat(broker.log()).extracting(Message::id).containsExactly("o-1:OrderPlaced", "o-1:OrderCancelled");
    }

    @Test
    void afterACrashItRepublishesEverythingSinceTheLastFlushedOffset() {
        // Each order writes 3 WAL entries (order insert, outbox insert, outbox delete); flush every 4.
        Faults faults = Faults.onceAt("relay.after-publish", 3);
        Broker broker = new Broker(Faults.NONE);
        LogTailingRelay relay = new LogTailingRelay(db, broker, faults, 4);
        service.place("o-1", "c-1", 45_000);
        service.place("o-2", "c-1", 45_000);
        service.place("o-3", "c-1", 45_000);

        assertThatThrownBy(relay::poll).isInstanceOf(Crash.class);   // crashed right after publishing o-3
        relay.restart();                                              // offset flushed at LSN 4 (o-2's order row)
        relay.poll();

        assertThat(broker.log()).extracting(Message::key)
                .containsExactly("o-1", "o-2", "o-3", "o-2", "o-3");
        assertThat(relay.lag()).isZero();
    }
}
