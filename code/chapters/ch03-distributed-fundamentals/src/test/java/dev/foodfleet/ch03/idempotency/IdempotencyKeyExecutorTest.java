package dev.foodfleet.ch03.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch03.idempotency.IdempotencyKeyExecutor.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyKeyExecutorTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final IdempotencyKeyExecutor<String> executor = new IdempotencyKeyExecutor<>(clock, Duration.ofHours(24));
    private final AtomicInteger sideEffects = new AtomicInteger();

    @Test
    void retryWithSameKeyAndBodyReplaysTheOriginalResult() {
        Outcome<String> first = executor.execute("key-1", "fp-a", this::refund);
        Outcome<String> retry = executor.execute("key-1", "fp-a", this::refund);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.result()).isEqualTo(first.result());
        assertThat(sideEffects).hasValue(1);
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        executor.execute("key-2", "fp-a", this::refund);

        assertThatThrownBy(() -> executor.execute("key-2", "fp-b", this::refund))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(sideEffects).hasValue(1);
    }

    @Test
    void failedAttemptReleasesTheKeySoTheClientCanRetry() {
        assertThatThrownBy(() -> executor.execute("key-3", "fp-a", () -> {
            throw new IllegalStateException("payment gateway timeout");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(executor.execute("key-3", "fp-a", this::refund).replayed()).isFalse();
        assertThat(sideEffects).hasValue(1);
    }

    @Test
    void concurrentDuplicates_sideEffectHappensExactlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();
        AtomicInteger inProgressRejections = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        results.add(executor.execute("key-4", "fp-a", this::slowRefund).result());
                    } catch (RequestInProgressException e) {
                        inProgressRejections.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(sideEffects).hasValue(1);
        assertThat(results).isNotEmpty().allMatch(r -> r.equals(results.peek()));
        assertThat(results.size() + inProgressRejections.get()).isEqualTo(50);
    }

    @Test
    void completedKeysExpireAfterTheRetentionPeriod() {
        executor.execute("key-5", "fp-a", this::refund);
        clock.advance(Duration.ofHours(25));

        assertThat(executor.purgeExpired()).isEqualTo(1);
        assertThat(executor.execute("key-5", "fp-a", this::refund).replayed()).isFalse();
    }

    @Test
    void fingerprintSeparatesFields() {
        assertThat(IdempotencyKeyExecutor.fingerprint("ab", "c"))
                .isNotEqualTo(IdempotencyKeyExecutor.fingerprint("a", "bc"));
    }

    private String refund() {
        return "refund-" + sideEffects.incrementAndGet();
    }

    private String slowRefund() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return refund();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
