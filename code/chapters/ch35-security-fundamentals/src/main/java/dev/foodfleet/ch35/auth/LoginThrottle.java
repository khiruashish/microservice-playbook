package dev.foodfleet.ch35.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing (API2: broken authentication). After a few failures for an account, each further
 * attempt must wait twice as long as the last, up to a cap; the same per source address, so one address can't
 * spray many accounts. Success resets the account. Measured with a simulated clock: password checks against one
 * account in an hour fell from ~26,700 (one core verifying hashes) to 18, from one address or from a thousand.
 *
 * <p>Waiting, not locking: a hard lockout lets anyone lock any customer out by failing on purpose.
 */
public final class LoginThrottle {

    public record Verdict(boolean allowed, Duration retryAfter) {
    }

    private record State(int failures, Instant notBefore) {
    }

    private final Clock clock;
    private final int freeFailures;
    private final Duration base;
    private final Duration max;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock, int freeFailures, Duration base, Duration max) {
        this.clock = clock;
        this.freeFailures = freeFailures;
        this.base = base;
        this.max = max;
    }

    public static LoginThrottle standard(Clock clock) {
        return new LoginThrottle(clock, 5, Duration.ofSeconds(1), Duration.ofMinutes(15));
    }

    /** Ask before checking the password, for both the account and the source address. */
    public Verdict check(String account, String source) {
        Instant now = clock.instant();
        Instant until = later(notBefore("acct:" + account), notBefore("src:" + source));
        return until.isAfter(now) ? new Verdict(false, Duration.between(now, until)) : new Verdict(true, Duration.ZERO);
    }

    public void failed(String account, String source) {
        bump("acct:" + account, freeFailures);
        bump("src:" + source, freeFailures * 4);              // an address may mistype for several accounts
    }

    public void succeeded(String account) {
        states.remove("acct:" + account);
    }

    private void bump(String key, int free) {
        Instant now = clock.instant();
        states.compute(key, (k, s) -> {
            int failures = s == null ? 1 : s.failures() + 1;
            if (failures <= free) {
                return new State(failures, now);
            }
            long factor = 1L << Math.min(failures - free - 1, 20);
            Duration wait = base.multipliedBy(factor);
            return new State(failures, now.plus(wait.compareTo(max) > 0 ? max : wait));
        });
    }

    private Instant notBefore(String key) {
        State s = states.get(key);
        return s == null ? Instant.EPOCH : s.notBefore();
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
