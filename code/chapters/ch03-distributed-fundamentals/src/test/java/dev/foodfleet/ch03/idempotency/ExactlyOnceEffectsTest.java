package dev.foodfleet.ch03.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch03.net.UnreliableNetwork;
import dev.foodfleet.ch03.net.UnreliableNetwork.Envelope;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The three delivery semantics, side by side. 500 FoodFleet orders, each worth ₹100, are sent to a
 * billing consumer. The correct total is ₹50,000.
 */
class ExactlyOnceEffectsTest {

    private static final int ORDERS = 500;
    private static final long PAISE_PER_ORDER = 10_000;
    private static final long CORRECT_TOTAL = ORDERS * PAISE_PER_ORDER;

    @Test
    void atMostOnce_losesMoney() {
        UnreliableNetwork<Long> network = UnreliableNetwork.of(7, 0.05, 0, 4); // fire-and-forget, no retries
        sendOrders(network);

        AtomicLong billed = new AtomicLong();
        network.drain().forEach(m -> billed.addAndGet(m.payload()));

        assertThat(billed.get()).isLessThan(CORRECT_TOTAL);
    }

    @Test
    void atLeastOnce_withoutDedup_doubleBills() {
        UnreliableNetwork<Long> network = UnreliableNetwork.of(7, 0, 0.3, 4); // retries cause duplicates
        sendOrders(network);

        AtomicLong billed = new AtomicLong();
        network.drain().forEach(m -> billed.addAndGet(m.payload()));

        assertThat(billed.get()).isGreaterThan(CORRECT_TOTAL);
    }

    @Test
    void atLeastOnce_plusIdempotentConsumer_isExactlyOnceEffect() {
        UnreliableNetwork<Long> network = UnreliableNetwork.of(7, 0, 0.3, 4);
        sendOrders(network);

        AtomicLong billed = new AtomicLong();
        IdempotentConsumer<Long> billing = new IdempotentConsumer<>(10_000, billed::addAndGet);
        List<Envelope<Long>> deliveries = network.drain();
        long applied = deliveries.stream().filter(m -> billing.handle(m.messageId(), m.payload())).count();

        assertThat(deliveries.size()).isGreaterThan(ORDERS);
        assertThat(applied).isEqualTo(ORDERS);
        assertThat(billed.get()).isEqualTo(CORRECT_TOTAL);
    }

    private static void sendOrders(UnreliableNetwork<Long> network) {
        IntStream.range(0, ORDERS).forEach(i -> network.send("order-" + i, PAISE_PER_ORDER));
    }
}
