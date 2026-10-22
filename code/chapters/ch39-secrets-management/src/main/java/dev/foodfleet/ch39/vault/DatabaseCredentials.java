package dev.foodfleet.ch39.vault;

import dev.foodfleet.ch39.vault.VaultClient.DatabaseLease;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps a service supplied with valid dynamic database credentials for as long as it runs. Renewing a lease only
 * works up to the role's max_ttl: when a renewal is granted less than asked for, the lease is in its last stretch, so
 * new credentials are requested then, handed to the connection pool, and the old ones are revoked once the pool has
 * had time to replace its connections. Failures keep the current credentials while they are valid and retry sooner.
 */
public final class DatabaseCredentials implements AutoCloseable {

    /** Where credentials come from: the Vault/OpenBao database secrets engine in production. */
    public interface Source {
        DatabaseLease issue() throws Exception;

        Duration renew(String leaseId, Duration increment) throws Exception;

        void revoke(String leaseId) throws Exception;
    }

    /** Called with new credentials, before the previous ones are revoked: point the pool at {@code next} here. */
    @FunctionalInterface
    public interface Listener {
        void onNewCredentials(DatabaseLease next, DatabaseLease previous);
    }

    private final Source source;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Listener listener;
    private final Duration revokeAfter;
    private final AtomicLong renewals = new AtomicLong();
    private final AtomicLong rotations = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile DatabaseLease current;
    private volatile Instant expiresAt;
    private volatile boolean closed;

    /** @param revokeAfter how long the old credentials stay valid after the switch (time for the pool to recycle) */
    public DatabaseCredentials(Source source, Clock clock, ScheduledExecutorService scheduler, Duration revokeAfter,
            Listener listener) {
        this.source = source;
        this.clock = clock;
        this.scheduler = scheduler;
        this.revokeAfter = revokeAfter;
        this.listener = listener;
    }

    public DatabaseCredentials start() throws Exception {
        DatabaseLease first = source.issue();
        install(first, null);
        return this;
    }

    public DatabaseLease current() {
        return current;
    }

    public long renewals() {
        return renewals.get();
    }

    public long rotations() {
        return rotations.get();
    }

    public long failures() {
        return failures.get();
    }

    private void install(DatabaseLease next, DatabaseLease previous) {
        current = next;
        expiresAt = clock.instant().plus(next.ttl());
        listener.onNewCredentials(next, previous);
        if (previous != null) {
            scheduler.schedule(() -> revokeQuietly(previous), revokeAfter.toMillis(), TimeUnit.MILLISECONDS);
        }
        scheduleCheck(next.ttl().dividedBy(3));
    }

    private void scheduleCheck(Duration in) {
        if (!closed) {
            scheduler.schedule(this::check, Math.max(50, in.toMillis()), TimeUnit.MILLISECONDS);
        }
    }

    private void check() {
        DatabaseLease lease = current;
        try {
            if (lease.renewable()) {
                Duration granted = source.renew(lease.leaseId(), lease.ttl());
                renewals.incrementAndGet();
                expiresAt = clock.instant().plus(granted);
                if (granted.compareTo(lease.ttl()) >= 0) {
                    scheduleCheck(lease.ttl().dividedBy(3));
                    return;
                }
                // capped by max_ttl: these credentials end at expiresAt whatever we do
            }
            rotations.incrementAndGet();
            install(source.issue(), lease);
        } catch (Exception e) {
            failures.incrementAndGet();
            Duration left = Duration.between(clock.instant(), expiresAt);
            scheduleCheck(left.isNegative() ? Duration.ofMillis(500) : left.dividedBy(4));
        }
    }

    private void revokeQuietly(DatabaseLease lease) {
        try {
            source.revoke(lease.leaseId());
        } catch (Exception e) {
            failures.incrementAndGet();            // it expires on its own at max_ttl
        } finally {
            lease.password().destroy();
        }
    }

    @Override
    public void close() {
        closed = true;
        DatabaseLease lease = current;
        if (lease != null) {
            revokeQuietly(lease);                  // don't leave a live database role behind a stopped pod
        }
    }
}
