package dev.foodfleet.ch43.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.timelimiter.TimeLimiter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** cancelRunningFuture = true cancels the Future; whether the work stops depends on what kind of Future it is. */
class TimeLimiterTest {

    private final TimeLimiter limiter = TimeLimiter.of(ResiliencePolicies.timeLimiter(Duration.ofMillis(100)));

    @Test
    void aCompletableFutureKeepsRunningAfterTheTimeout() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicBoolean finished = new AtomicBoolean();
        assertThatThrownBy(() -> limiter.executeFutureSupplier(() -> CompletableFuture.runAsync(() -> sleepThenMark(finished), pool)))
                .isInstanceOf(TimeoutException.class);
        Thread.sleep(600);
        assertThat(finished.get()).isTrue();     // the "cancelled" work ran to the end
        pool.shutdownNow();
    }

    @Test
    void anExecutorServiceFutureIsInterrupted() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicBoolean finished = new AtomicBoolean();
        assertThatThrownBy(() -> limiter.executeFutureSupplier(() -> pool.submit(() -> sleepThenMark(finished))))
                .isInstanceOf(TimeoutException.class);
        Thread.sleep(600);
        assertThat(finished.get()).isFalse();    // the sleep was interrupted
        pool.shutdownNow();
    }

    private static void sleepThenMark(AtomicBoolean finished) {
        try {
            Thread.sleep(400);
            finished.set(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
