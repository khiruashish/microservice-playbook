package dev.foodfleet.ch15.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch15.orders.OrderRow;
import dev.foodfleet.ch15.sim.Crash;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxOrderServiceTest {

    private final Database db = new Database("ordering");

    @Test
    void orderAndEventCommitTogether() {
        new OutboxOrderService(db, Faults.NONE, false).place("o-1", "c-1", 45_000);

        assertThat(db.table(OrderRow.TABLE)).containsKey("o-1");
        assertThat(db.table(OutboxRow.TABLE).values())
                .extracting(row -> ((OutboxRow) row).messageId())
                .containsExactly("o-1:OrderPlaced");
    }

    @Test
    void aFailedCommitRollsBackTheOrderAndTheEvent() {
        var service = new OutboxOrderService(db, new Faults(Map.of("service.commit-fails", 1.0), 1), false);

        assertThatThrownBy(() -> service.place("o-1", "c-1", 45_000)).isInstanceOf(Crash.class);

        assertThat(db.table(OrderRow.TABLE)).isEmpty();
        assertThat(db.table(OutboxRow.TABLE)).isEmpty();
    }

    @Test
    void insertThenDeleteLeavesTheTableEmptyButTheInsertInTheLog() {
        new OutboxOrderService(db, Faults.NONE, true).place("o-1", "c-1", 45_000);

        assertThat(db.table(OutboxRow.TABLE)).isEmpty();
        assertThat(db.walAfter(0))
                .filteredOn(change -> change.table().equals(OutboxRow.TABLE))
                .extracting(change -> change.op())
                .containsExactly(Database.Op.INSERT, Database.Op.DELETE);
    }
}
