package dev.foodfleet.ch35.auth;

import dev.foodfleet.ch35.identity.TokenService;
import java.time.Duration;
import java.util.Optional;

/**
 * Password login done carefully: throttled per account and per source, the same answer and similar work for an
 * unknown user as for a wrong password (no account enumeration), hashes upgraded when the work factor rises.
 */
public class LoginService {

    public sealed interface Outcome permits Success, Failure, Throttled {
    }

    public record Success(String token) implements Outcome {
    }

    public record Failure() implements Outcome {
    }

    public record Throttled(Duration retryAfter) implements Outcome {
    }

    private final UserDirectory users;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final TokenService tokens;
    private final String dummyHash;

    public LoginService(UserDirectory users, PasswordHasher hasher, LoginThrottle throttle, TokenService tokens) {
        this.users = users;
        this.hasher = hasher;
        this.throttle = throttle;
        this.tokens = tokens;
        this.dummyHash = hasher.hash("not-a-real-password".toCharArray());
    }

    public Outcome login(String username, char[] password, String source) {
        LoginThrottle.Verdict verdict = throttle.check(username, source);
        if (!verdict.allowed()) {
            return new Throttled(verdict.retryAfter());
        }
        Optional<UserDirectory.Account> account = users.find(username);
        boolean ok = hasher.matches(password, account.map(UserDirectory.Account::passwordHash).orElse(dummyHash))
                && account.isPresent();
        if (!ok) {
            throttle.failed(username, source);
            return new Failure();
        }
        throttle.succeeded(username);
        if (hasher.needsRehash(account.get().passwordHash())) {
            users.replaceHash(username, hasher.hash(password));
        }
        return new Success(tokens.issue(account.get().caller()));
    }
}
