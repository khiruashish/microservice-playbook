package dev.foodfleet.ch03.clock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch03.clock.HybridLogicalClock.Timestamp;
import dev.foodfleet.ch03.clock.VectorClock.Order;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ClocksTest {

    @Test
    void lamport_happenedBeforeImpliesSmallerTimestamp() {
        LamportClock orderService = new LamportClock();
        LamportClock kitchenService = new LamportClock();

        long orderPlaced = orderService.tick();               // event a
        long received = kitchenService.onReceive(orderPlaced); // event b: a → b
        long ticketCreated = kitchenService.tick();             // event c: b → c

        assertThat(orderPlaced).isLessThan(received);
        assertThat(received).isLessThan(ticketCreated);
    }

    @Test
    void lamport_smallerTimestampDoesNotImplyHappenedBefore() {
        LamportClock riderApp = new LamportClock();
        LamportClock restaurantApp = new LamportClock();

        long riderMoved = riderApp.tick();                    // 1
        restaurantApp.tick();
        long foodReady = restaurantApp.tick();                // 2, causally unrelated to riderMoved

        assertThat(riderMoved).isLessThan(foodReady);         // yet neither caused the other
    }

    @Test
    void vectorClock_detectsCausalityAndConcurrency() {
        VectorClock base = VectorClock.empty().increment("customer");          // customer writes delivery note
        VectorClock supportEdit = base.mergeAndIncrement(base, "support");     // support edits after seeing it
        VectorClock customerEdit = base.increment("customer");                 // customer edits again, unaware

        assertThat(base.compareTo(supportEdit)).isEqualTo(Order.BEFORE);
        assertThat(supportEdit.compareTo(base)).isEqualTo(Order.AFTER);
        assertThat(supportEdit.compareTo(customerEdit))
                .as("two edits from the same base: a conflict to resolve, not a winner to pick")
                .isEqualTo(Order.CONCURRENT);
        assertThat(customerEdit.compareTo(customerEdit)).isEqualTo(Order.EQUAL);
    }

    @Test
    void hlc_neverGoesBackwards_evenWhenTheWallClockDoes() {
        AtomicLong wall = new AtomicLong(1_000);
        HybridLogicalClock clock = new HybridLogicalClock(wall::get);

        Timestamp t1 = clock.now();
        wall.set(900);                                        // NTP stepped the clock back 100 ms
        Timestamp t2 = clock.now();

        assertThat(t1).isEqualTo(new Timestamp(1_000, 0));
        assertThat(t2).isEqualTo(new Timestamp(1_000, 1));
        assertThat(t2.isAfter(t1)).isTrue();
    }

    @Test
    void hlc_receiveFromANodeWhoseClockIsAhead() {
        AtomicLong wall = new AtomicLong(1_000);
        HybridLogicalClock local = new HybridLogicalClock(wall::get);
        local.now();

        Timestamp received = local.onReceive(new Timestamp(1_500, 3));
        assertThat(received).isEqualTo(new Timestamp(1_500, 4));

        wall.set(2_000);                                      // physical time catches up
        assertThat(local.now()).isEqualTo(new Timestamp(2_000, 0));
    }
}
