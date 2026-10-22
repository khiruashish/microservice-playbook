package dev.foodfleet.ch31.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HybridLogicalClockTest {

    @Test
    void aWriteMadeAfterSeeingAnotherIsLaterEvenWithASlowClock() {
        AtomicLong mumbaiWall = new AtomicLong(10_000);
        AtomicLong chennaiWall = new AtomicLong(9_750);                 // 250 ms behind
        HybridLogicalClock mumbai = new HybridLogicalClock("A", mumbaiWall::get);
        HybridLogicalClock chennai = new HybridLogicalClock("B", chennaiWall::get);

        LwwRegister<String> fromMumbai = new LwwRegister<>("ring the bell", mumbai.now());
        chennaiWall.addAndGet(100);                                     // 100 ms later, Chennai sees it...
        chennai.observe(fromMumbai.stamp());
        LwwRegister<String> fromChennai = new LwwRegister<>("leave at the gate", chennai.now());   // ...and replaces it

        assertThat(fromChennai.stamp().compareTo(fromMumbai.stamp()) > 0).isTrue();
        assertThat(fromMumbai.merge(fromChennai).value()).isEqualTo("leave at the gate");
        assertThat(fromChennai.merge(fromMumbai).value()).isEqualTo("leave at the gate");
        // with plain wall clocks the replacement would be stamped 9,850 against 10,000, and lose
    }

    @Test
    void timestampsNeverGoBackwardsWhenTheWallClockDoes() {
        AtomicLong wall = new AtomicLong(5_000);
        HybridLogicalClock clock = new HybridLogicalClock("A", wall::get);
        HybridLogicalClock.Timestamp first = clock.now();
        wall.set(4_000);                                                // NTP stepped the clock back
        HybridLogicalClock.Timestamp second = clock.now();
        assertThat(second.compareTo(first) > 0).isTrue();
        assertThat(second.wallMillis()).isEqualTo(5_000L);
        assertThat(second.counter()).isEqualTo(1);
    }

    @Test
    void observingAFutureTimestampMovesTheClockForward() {
        HybridLogicalClock clock = new HybridLogicalClock("B", () -> 1_000);
        HybridLogicalClock.Timestamp received = new HybridLogicalClock.Timestamp(2_000, 3, "A");
        HybridLogicalClock.Timestamp after = clock.observe(received);
        assertThat(after.wallMillis()).isEqualTo(2_000L);
        assertThat(after.counter()).isEqualTo(4);
        assertThat(clock.now().compareTo(received) > 0).isTrue();
    }
}
