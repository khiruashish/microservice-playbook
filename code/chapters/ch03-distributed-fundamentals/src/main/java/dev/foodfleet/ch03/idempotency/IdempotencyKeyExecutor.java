package dev.foodfleet.ch03.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Server side of the {@code Idempotency-Key} pattern (as used by Stripe and by the IETF
 * idempotency-key header draft): a client that times out can safely retry a non-idempotent POST.
 *
 * <ul>
 *   <li>First request with a key: claim it, run the action, store the result.</li>
 *   <li>Retry with the same key and same payload: return the stored result. The action does not run again.</li>
 *   <li>Same key while the first request is still running: reject (HTTP 409), and the client retries later.</li>
 *   <li>Same key with a <em>different</em> payload: reject (HTTP 422). That's a client bug, not a retry.</li>
 *   <li>Action fails: release the claim so a retry can try again.</li>
 * </ul>
 * In-memory for clarity. In production the store is a database table or Redis with a TTL, shared by
 * all replicas, and the claim must be atomic there (insert-if-absent).
 */
public final class IdempotencyKeyExecutor<R> {

    private final ConcurrentHashMap<String, Entry<R>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration retention;

    public IdempotencyKeyExecutor(Clock clock, Duration retention) {
        this.clock = Objects.requireNonNull(clock);
        this.retention = Objects.requireNonNull(retention);
    }

    public Outcome<R> execute(String key, String requestFingerprint, Supplier<R> action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        InProgress<R> claim = new InProgress<>(requestFingerprint);
        Entry<R> existing = entries.putIfAbsent(key, claim);
        if (existing != null) {
            return switch (existing) {
                case InProgress<R> running when !running.fingerprint().equals(requestFingerprint) ->
                        throw new IdempotencyKeyReusedException(key);
                case InProgress<R> running -> throw new RequestInProgressException(key);
                case Completed<R> done when !done.fingerprint().equals(requestFingerprint) ->
                        throw new IdempotencyKeyReusedException(key);
                case Completed<R> done -> new Outcome<>(done.result(), true);
            };
        }
        try {
            R result = action.get();
            entries.put(key, new Completed<>(requestFingerprint, result, clock.instant()));
            return new Outcome<>(result, false);
        } catch (RuntimeException failure) {
            entries.remove(key, claim); // let the client retry a failed attempt
            throw failure;
        }
    }

    /** Forget completed keys older than the retention period (run on a schedule). */
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(retention);
        int before = entries.size();
        entries.entrySet().removeIf(e -> e.getValue() instanceof Completed<R> done && done.completedAt().isBefore(cutoff));
        return before - entries.size();
    }

    /** SHA-256 over the request's significant fields, so "same key, different request" can be detected. */
    public static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F); // unit separator: ("ab","c") ≠ ("a","bc")
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every JDK", e);
        }
    }

    public record Outcome<R>(R result, boolean replayed) {
    }

    private sealed interface Entry<R> permits InProgress, Completed {
    }

    private record InProgress<R>(String fingerprint) implements Entry<R> {
    }

    private record Completed<R>(String fingerprint, R result, Instant completedAt) implements Entry<R> {
    }
}
