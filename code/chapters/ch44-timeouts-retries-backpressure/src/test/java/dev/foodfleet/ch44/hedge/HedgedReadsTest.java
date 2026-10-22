package dev.foodfleet.ch44.hedge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HedgedReadsTest {

    private final ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void stop() {
        vt.shutdownNow();
    }

    /** The first call stalls for 2 s, every later call answers in 10 ms. */
    private String firstStalls() {
        int n = calls.incrementAndGet();
        try {
            Thread.sleep(n == 1 ? 2000 : 10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return n == 1 ? "slow" : "fast";
    }

    @Test
    void theHedgeAnswersWhenTheFirstCallStalls() {
        HedgedReads reads = new HedgedReads("menu", Duration.ofMillis(50), Duration.ofSeconds(1), 1.0, vt);
        long t0 = System.nanoTime();
        assertThat(reads.read(this::firstStalls).join()).isEqualTo("fast");
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(500);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void withoutBudgetTheReadWaitsForItsFirstCall() {
        HedgedReads reads = new HedgedReads("menu", Duration.ofMillis(50), Duration.ofSeconds(3), 0.0, vt);
        assertThat(reads.read(this::firstStalls).join()).isEqualTo("slow");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(reads.hedgesSkipped()).isEqualTo(1);
    }

    @Test
    void fastReadsNeverHedge() {
        HedgedReads reads = new HedgedReads("menu", Duration.ofMillis(200), Duration.ofSeconds(1), 1.0, vt);
        for (int i = 0; i < 5; i++) {
            reads.read(() -> {
                calls.incrementAndGet();
                return "fast";
            }).join();
        }
        assertThat(calls.get()).isEqualTo(5);
    }
}
