package dev.foodfleet.ch15.dualwrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch15.orders.OrderRow;
import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Crash;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DualWriteOrderServiceTest {

    private final Database db = new Database("ordering");

    @Test
    void databaseFirstLosesTheEventWhenTheServiceDiesAfterCommitting() {
        Faults faults = new Faults(Map.of("service.after-commit", 1.0), 1);
        Broker broker = new Broker(faults);
        var service = new DualWriteOrderService(db, broker, DualWriteOrderService.Order.DATABASE_FIRST, faults);

        assertThatThrownBy(() -> service.place("o-1", "c-1", 45_000)).isInstanceOf(Crash.class);

        assertThat(db.table(OrderRow.TABLE)).containsKey("o-1");   // the order exists...
        assertThat(broker.log()).isEmpty();                        // ...and nobody was told
    }

    @Test
    void databaseFirstLosesTheEventWhenTheBrokerIsDown() {
        Faults faults = new Faults(Map.of("broker.unavailable", 1.0), 1);
        Broker broker = new Broker(faults);
        var service = new DualWriteOrderService(db, broker, DualWriteOrderService.Order.DATABASE_FIRST, faults);

        assertThatThrownBy(() -> service.place("o-1", "c-1", 45_000)).isInstanceOf(Broker.PublishFailed.class);

        assertThat(db.table(OrderRow.TABLE)).containsKey("o-1");
        assertThat(broker.log()).isEmpty();
    }

    @Test
    void brokerFirstPublishesAPhantomWhenTheCommitFails() {
        Faults faults = new Faults(Map.of("service.commit-fails", 1.0), 1);
        Broker broker = new Broker(faults);
        var service = new DualWriteOrderService(db, broker, DualWriteOrderService.Order.BROKER_FIRST, faults);

        assertThatThrownBy(() -> service.place("o-1", "c-1", 45_000)).isInstanceOf(Crash.class);

        assertThat(db.table(OrderRow.TABLE)).isEmpty();            // no order...
        assertThat(broker.log()).hasSize(1);                       // ...but everyone heard about it
    }

    @Test
    void withoutFailuresBothOrdersWork() throws Exception {
        Broker broker = new Broker(Faults.NONE);
        var service = new DualWriteOrderService(db, broker, DualWriteOrderService.Order.DATABASE_FIRST, Faults.NONE);

        service.place("o-1", "c-1", 45_000);
        service.cancel("o-1");

        assertThat(broker.log()).extracting(m -> m.type()).containsExactly("OrderPlaced", "OrderCancelled");
    }
}
