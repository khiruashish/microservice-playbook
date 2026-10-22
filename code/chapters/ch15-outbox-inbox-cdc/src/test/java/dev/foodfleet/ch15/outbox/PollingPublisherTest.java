package dev.foodfleet.ch15.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Crash;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import dev.foodfleet.ch15.sim.Message;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PollingPublisherTest {

    private final Database db = new Database("ordering");

    private void placeAndCancel(String orderId) {
        var service = new OutboxOrderService(db, Faults.NONE, false);
        service.place(orderId, "c-1", 45_000);
        service.cancel(orderId);
    }

    @Test
    void publishesInCommitOrderAndMarksRows() {
        placeAndCancel("o-1");
        placeAndCancel("o-2");
        Broker broker = new Broker(Faults.NONE);

        int published = new PollingPublisher(db, broker, Faults.NONE, 10).poll();

        assertThat(published).isEqualTo(4);
        assertThat(broker.log()).extracting(Message::id)
                .containsExactly("o-1:OrderPlaced", "o-1:OrderCancelled", "o-2:OrderPlaced", "o-2:OrderCancelled");
        assertThat(new PollingPublisher(db, broker, Faults.NONE, 10).backlog()).isZero();
    }

    @Test
    void anUnavailableBrokerStopsTheBatchSoNothingOvertakes() {
        placeAndCancel("o-1");
        Faults faults = new Faults(Map.of("broker.unavailable", 1.0), 1);
        Broker broker = new Broker(faults);
        PollingPublisher publisher = new PollingPublisher(db, broker, faults, 10);

        assertThat(publisher.poll()).isZero();
        assertThat(publisher.backlog()).isEqualTo(2);

        faults.heal();
        publisher.poll();
        assertThat(broker.log()).extracting(Message::type).containsExactly("OrderPlaced", "OrderCancelled");
    }

    @Test
    void aCrashBetweenPublishAndMarkPublishesTheRowAgain() {
        placeAndCancel("o-1");
        Faults faults = new Faults(Map.of("relay.after-publish", 1.0), 1);
        Broker broker = new Broker(faults);
        PollingPublisher publisher = new PollingPublisher(db, broker, faults, 10);

        assertThatThrownBy(publisher::poll).isInstanceOf(Crash.class);
        faults.heal();
        publisher.poll();

        assertThat(broker.log()).extracting(Message::id)
                .containsExactly("o-1:OrderPlaced", "o-1:OrderPlaced", "o-1:OrderCancelled");
    }

    @Test
    void aLostAckAlsoMeansADuplicate() {
        placeAndCancel("o-1");
        Faults faults = new Faults(Map.of("broker.ack-lost", 1.0), 1);
        Broker broker = new Broker(faults);
        PollingPublisher publisher = new PollingPublisher(db, broker, faults, 10);

        publisher.poll();             // stored, but the publisher saw a timeout
        faults.heal();
        publisher.poll();

        assertThat(broker.log()).extracting(Message::id)
                .containsExactly("o-1:OrderPlaced", "o-1:OrderPlaced", "o-1:OrderCancelled");
    }
}
