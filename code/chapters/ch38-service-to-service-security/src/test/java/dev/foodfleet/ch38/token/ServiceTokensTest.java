package dev.foodfleet.ch38.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ServiceTokensTest {

    /** A clock the test moves by hand. */
    static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-01T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final AtomicInteger issued = new AtomicInteger();
    private final AtomicBoolean failing = new AtomicBoolean();
    private volatile CountDownLatch slowFetch;

    private ServiceTokens.Token fetch() throws IOException {
        if (failing.get()) {
            throw new IOException("token endpoint unavailable");
        }
        if (slowFetch != null) {
            try {
                slowFetch.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        Instant now = clock.instant();
        return new ServiceTokens.Token("t" + issued.incrementAndGet(), now, now.plus(Duration.ofMinutes(5)));
    }

    private final ServiceTokens tokens = new ServiceTokens(this::fetch, clock, 0.8);

    @Test
    void oneTokenServesEveryCallUntilTheRenewalWindow() throws Exception {
        for (int i = 0; i < 1000; i++) {
            assertThat(tokens.token()).isEqualTo("t1");
        }
        clock.advance(Duration.ofMinutes(4).minusSeconds(1));
        assertThat(tokens.token()).isEqualTo("t1");
        clock.advance(Duration.ofSeconds(2));                        // past 80% of five minutes
        assertThat(tokens.token()).isEqualTo("t2");
        assertThat(tokens.fetches()).isEqualTo(2);
    }

    @Test
    void manyThreadsWaitingForAMissingTokenCauseOneFetch() throws Exception {
        slowFetch = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<String> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 64; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    seen.add(tokens.token());
                } catch (IOException e) {
                    seen.add("error");
                }
            }));
        }
        Thread.sleep(100);
        slowFetch.countDown();
        for (Thread t : threads) {
            t.join();
        }
        assertThat(tokens.fetches()).isEqualTo(1);
        assertThat(seen.stream().distinct().toList()).containsExactly("t1");
    }

    @Test
    void duringRenewalOthersKeepUsingTheValidToken() throws Exception {
        tokens.token();
        clock.advance(Duration.ofMinutes(4).plusSeconds(10));       // in the window, still valid for 50 s
        slowFetch = new CountDownLatch(1);
        Thread renewer = Thread.ofVirtual().start(() -> {
            try {
                tokens.token();
            } catch (IOException ignored) {
            }
        });
        Thread.sleep(50);
        for (int i = 0; i < 100; i++) {
            assertThat(tokens.token()).isEqualTo("t1");             // nobody waits for the renewal
        }
        slowFetch.countDown();
        renewer.join();
        assertThat(tokens.token()).isEqualTo("t2");
        assertThat(tokens.fetches()).isEqualTo(2);
    }

    @Test
    void aFailedRenewalKeepsTheValidTokenAndAnExpiredOneFails() throws Exception {
        tokens.token();
        clock.advance(Duration.ofMinutes(4).plusSeconds(10));
        failing.set(true);
        assertThat(tokens.token()).isEqualTo("t1");
        assertThat(tokens.failedRenewals()).isEqualTo(1);
        clock.advance(Duration.ofMinutes(1));                        // now expired
        assertThatThrownBy(tokens::token).isInstanceOf(IOException.class);
        failing.set(false);
        assertThat(tokens.token()).isEqualTo("t2");
    }

    @Test
    void aRejectedTokenIsDroppedOnce() throws Exception {
        String t = tokens.token();
        tokens.invalidate(t);
        assertThat(tokens.token()).isEqualTo("t2");
        tokens.invalidate(t);                                        // a late 401 for the old token changes nothing
        assertThat(tokens.token()).isEqualTo("t2");
    }
}
