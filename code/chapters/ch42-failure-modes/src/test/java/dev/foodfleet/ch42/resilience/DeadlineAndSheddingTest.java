package dev.foodfleet.ch42.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DeadlineAndSheddingTest {

    @Test
    void aDeadlineTravelsAsAnAbsoluteTimeAndIsNeverExtended() {
        Deadline d = Deadline.after(Duration.ofSeconds(2), 10_000);
        assertThat(d.header()).isEqualTo("12000");
        assertThat(Deadline.parse("12000")).contains(d);
        assertThat(Deadline.parse("soon")).isEmpty();
        assertThat(d.remaining(11_500)).isEqualTo(Duration.ofMillis(500));
        assertThat(d.remaining(13_000)).isEqualTo(Duration.ZERO);
        assertThat(d.expired(12_000)).isTrue();
        assertThat(d.min(Duration.ofSeconds(5), 11_000).epochMillis()).isEqualTo(12_000);   // a hop can't lengthen it
        assertThat(d.min(Duration.ofMillis(300), 11_000).epochMillis()).isEqualTo(11_300);  // but may shorten it
    }

    @Test
    void theShedderDropsStaleAndExpiredWork() {
        QueueAgeShedder s = new QueueAgeShedder(150);
        assertThat(s.admit(1_000, null, 1_100)).isEqualTo(QueueAgeShedder.Verdict.SERVE);
        assertThat(s.admit(1_000, null, 1_200)).isEqualTo(QueueAgeShedder.Verdict.SHED_STALE);
        assertThat(s.admit(1_000, new Deadline(1_050), 1_060)).isEqualTo(QueueAgeShedder.Verdict.SHED_EXPIRED);
    }

    @Test
    void powerOfTwoChoicesAvoidsTheInstanceWithMoreInFlight() {
        P2cBalancer<String> b = new P2cBalancer<>(List.of("a", "b", "c", "d"));
        Random r = new Random(3);
        for (int i = 0; i < 50; i++) {                 // instance d is stuck: 50 requests never finish
            b.done(b.acquire(r));
        }
        int stuck = 3;
        for (int i = 0; i < 10; i++) {
            int idx = b.acquire(r);
            if (idx != stuck) {
                b.done(idx);
            }
        }
        int toStuck = 0;
        for (int i = 0; i < 1000; i++) {
            int idx = b.acquire(r);
            if (idx == stuck) {
                toStuck++;
            } else {
                b.done(idx);
            }
        }
        assertThat(toStuck).isLessThan(50);            // round robin would send it 250
        assertThat(b.instance(0)).isEqualTo("a");
    }
}
