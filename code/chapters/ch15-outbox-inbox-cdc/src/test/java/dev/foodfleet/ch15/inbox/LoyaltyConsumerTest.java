package dev.foodfleet.ch15.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Crash;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import dev.foodfleet.ch15.sim.Message;
import org.junit.jupiter.api.Test;

class LoyaltyConsumerTest {

    private static final Message PLACED =
            new Message("o-1:OrderPlaced", "o-1", "OrderPlaced", "order=o-1;customer=c-1;points=45");
    private static final Message CANCELLED =
            new Message("o-1:OrderCancelled", "o-1", "OrderCancelled", "order=o-1;customer=c-1;points=45");

    private static Broker brokerWith(Message... messages) throws Broker.PublishFailed {
        Broker broker = new Broker(Faults.NONE);
        for (Message m : messages) {
            broker.publish(m);
        }
        return broker;
    }

    @Test
    void withoutAnInboxADuplicateIsAppliedTwice() throws Exception {
        Broker broker = brokerWith(PLACED, PLACED);
        LoyaltyConsumer consumer = new LoyaltyConsumer(new Database("loyalty"), broker, Faults.NONE, false);

        consumer.poll(10);

        assertThat(consumer.totalPoints()).isEqualTo(90L);
    }

    @Test
    void theInboxSkipsTheDuplicate() throws Exception {
        Broker broker = brokerWith(PLACED, PLACED, CANCELLED, CANCELLED);
        LoyaltyConsumer consumer = new LoyaltyConsumer(new Database("loyalty"), broker, Faults.NONE, true);

        consumer.poll(10);

        assertThat(consumer.totalPoints()).isZero();
        assertThat(consumer.duplicatesSkipped()).isEqualTo(2);
    }

    @Test
    void aCrashBeforeTheOffsetCommitIsHarmlessWithAnInbox() throws Exception {
        Broker broker = brokerWith(PLACED);
        Faults crashOnce = Faults.onceAt("consumer.before-offset-commit", 1);
        Database loyaltyDb = new Database("loyalty");
        LoyaltyConsumer consumer = new LoyaltyConsumer(loyaltyDb, broker, crashOnce, true);

        assertThatThrownBy(() -> consumer.poll(10)).isInstanceOf(Crash.class);
        consumer.restart();
        consumer.poll(10);                                   // redelivered

        assertThat(consumer.totalPoints()).isEqualTo(45L);
        assertThat(consumer.duplicatesSkipped()).isEqualTo(1);
    }

    @Test
    void theSameCrashDoublesThePointsWithoutAnInbox() throws Exception {
        Broker broker = brokerWith(PLACED);
        LoyaltyConsumer consumer = new LoyaltyConsumer(new Database("loyalty"), broker,
                Faults.onceAt("consumer.before-offset-commit", 1), false);

        assertThatThrownBy(() -> consumer.poll(10)).isInstanceOf(Crash.class);
        consumer.restart();
        consumer.poll(10);

        assertThat(consumer.totalPoints()).isEqualTo(90L);
    }
}
