package dev.foodfleet.ch39.vault;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch39.secret.Secret;
import dev.foodfleet.ch39.vault.VaultClient.DatabaseLease;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Against a fake database secrets engine that behaves like Vault's: renewals are capped by max_ttl. */
class DatabaseCredentialsTest {

    /** ttl 300 ms, max_ttl 900 ms. */
    static final class FakeEngine implements DatabaseCredentials.Source {
        final Duration ttl = Duration.ofMillis(300);
        final Duration maxTtl = Duration.ofMillis(900);
        final AtomicInteger issued = new AtomicInteger();
        final List<String> revoked = new CopyOnWriteArrayList<>();
        final java.util.Map<String, Instant> created = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicBoolean down = new AtomicBoolean();

        public DatabaseLease issue() throws Exception {
            if (down.get()) {
                throw new java.io.IOException("unavailable");
            }
            String id = "lease-" + issued.incrementAndGet();
            created.put(id, Instant.now());
            return new DatabaseLease(id, "v-user-" + issued.get(), Secret.of(("pw" + issued.get()).toCharArray()), ttl, true);
        }

        public Duration renew(String leaseId, Duration increment) throws Exception {
            if (down.get()) {
                throw new java.io.IOException("unavailable");
            }
            Duration left = Duration.between(Instant.now(), created.get(leaseId).plus(maxTtl));
            return left.compareTo(increment) < 0 ? (left.isNegative() ? Duration.ZERO : left) : increment;
        }

        public void revoke(String leaseId) {
            revoked.add(leaseId);
        }
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
    }

    @Test
    void renewsUntilMaxTtlThenSwitchesToNewCredentialsAndRevokesTheOld() throws Exception {
        FakeEngine engine = new FakeEngine();
        List<String> users = new CopyOnWriteArrayList<>();
        DatabaseCredentials creds = new DatabaseCredentials(engine, Clock.systemUTC(), scheduler, Duration.ofMillis(100),
                (next, previous) -> users.add(next.username())).start();
        Thread.sleep(2500);
        creds.close();
        assertThat(creds.renewals()).isGreaterThan(2);
        assertThat(creds.rotations()).isGreaterThan(1);
        assertThat(users.get(0)).isEqualTo("v-user-1");
        assertThat(users.size()).isGreaterThan(2);
        assertThat(engine.revoked).contains("lease-1");
        assertThat(creds.failures()).isZero();
    }

    @Test
    void anOutageKeepsTheCurrentCredentialsAndRetries() throws Exception {
        FakeEngine engine = new FakeEngine();
        DatabaseCredentials creds = new DatabaseCredentials(engine, Clock.systemUTC(), scheduler, Duration.ofMillis(100),
                (next, previous) -> { }).start();
        engine.down.set(true);
        Thread.sleep(400);
        assertThat(creds.failures()).isGreaterThan(0);
        assertThat(creds.current().username()).isEqualTo("v-user-1");
        engine.down.set(false);
        Thread.sleep(1500);
        assertThat(creds.current().username()).isNotEqualTo("v-user-1");   // recovered by rotating
        creds.close();
    }

    @Test
    void closingRevokesTheCredentialsInUse() throws Exception {
        FakeEngine engine = new FakeEngine();
        DatabaseCredentials creds = new DatabaseCredentials(engine, Clock.systemUTC(), scheduler, Duration.ofMillis(100),
                (next, previous) -> { }).start();
        creds.close();
        assertThat(engine.revoked).containsExactly("lease-1");
        assertThat(creds.current().password().isDestroyed()).isTrue();
    }
}
