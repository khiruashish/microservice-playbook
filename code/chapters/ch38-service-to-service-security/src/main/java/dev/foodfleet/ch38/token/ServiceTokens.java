package dev.foodfleet.ch38.token;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One client-credentials access token per service, shared by every request thread, renewed ahead of expiry by a
 * single thread. Three states: fresh (use it), in the renewal window (one thread renews, everyone else keeps using
 * the still-valid token, and a failed renewal is retried by the next caller), expired or missing (callers wait for
 * one fetch). The token is the service's own, never keyed by the user whose request happens to be running.
 */
public final class ServiceTokens {

    public record Token(String value, Instant issuedAt, Instant expiresAt) {
        Instant renewAt(double fraction) {
            long life = Duration.between(issuedAt, expiresAt).toMillis();
            return issuedAt.plusMillis((long) (life * fraction));
        }
    }

    @FunctionalInterface
    public interface TokenEndpoint {
        Token fetch() throws IOException;
    }

    private final TokenEndpoint endpoint;
    private final Clock clock;
    private final double renewAtFraction;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong fetches = new AtomicLong();
    private final AtomicLong failedRenewals = new AtomicLong();
    private volatile Token current;

    /** @param renewAtFraction renew after this fraction of the token's lifetime, e.g. 0.8 */
    public ServiceTokens(TokenEndpoint endpoint, Clock clock, double renewAtFraction) {
        if (renewAtFraction <= 0 || renewAtFraction >= 1) {
            throw new IllegalArgumentException("renewAtFraction must be between 0 and 1");
        }
        this.endpoint = endpoint;
        this.clock = clock;
        this.renewAtFraction = renewAtFraction;
    }

    public String token() throws IOException {
        Token t = current;
        Instant now = clock.instant();
        if (t != null && now.isBefore(t.renewAt(renewAtFraction))) {
            return t.value();                                   // fresh
        }
        if (t != null && now.isBefore(t.expiresAt())) {        // renewal window: still valid
            if (lock.tryLock()) {
                try {
                    renewIfStale();
                } catch (IOException e) {
                    failedRenewals.incrementAndGet();          // keep using the valid token; next caller retries
                } finally {
                    lock.unlock();
                }
            }
            Token latest = current;
            return latest != null ? latest.value() : t.value();
        }
        lock.lock();                                           // missing or expired: wait for one fetch
        try {
            renewIfStale();
            return current.value();
        } finally {
            lock.unlock();
        }
    }

    /** After a 401 for {@code value}: drop it so the next call fetches a new one (only if nobody did already). */
    public void invalidate(String value) {
        lock.lock();
        try {
            if (current != null && current.value().equals(value)) {
                current = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public long fetches() {
        return fetches.get();
    }

    public long failedRenewals() {
        return failedRenewals.get();
    }

    private void renewIfStale() throws IOException {
        Token t = current;
        if (t != null && clock.instant().isBefore(t.renewAt(renewAtFraction))) {
            return;                                            // another thread renewed while we waited
        }
        fetches.incrementAndGet();
        current = endpoint.fetch();
    }
}
