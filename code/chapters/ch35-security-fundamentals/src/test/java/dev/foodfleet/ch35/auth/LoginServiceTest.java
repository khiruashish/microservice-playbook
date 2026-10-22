package dev.foodfleet.ch35.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.Role;
import dev.foodfleet.ch35.identity.TokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

    final Clock clock = Clock.fixed(Instant.parse("2025-04-14T10:00:00Z"), ZoneOffset.UTC);
    final PasswordHasher hasher = new PasswordHasher(1_000);                      // fast for tests
    final UserDirectory users = new UserDirectory();
    final LoginService logins = new LoginService(users, hasher, LoginThrottle.standard(clock),
            new TokenService("0123456789abcdef0123456789abcdef".getBytes(), clock, Duration.ofMinutes(15)));

    LoginServiceTest() {
        users.add(new UserDirectory.Account("asha@example.com", hasher.hash("correct horse".toCharArray()),
                new Caller("c01", Set.of(Role.CUSTOMER), Set.of())));
    }

    @Test
    void theRightPasswordGetsAToken() {
        assertThat(logins.login("asha@example.com", "correct horse".toCharArray(), "1.2.3.4"))
                .isInstanceOf(LoginService.Success.class);
    }

    @Test
    void anUnknownUserGetsTheSameAnswerAsAWrongPassword() {
        assertThat(logins.login("nobody@example.com", "x".toCharArray(), "1.2.3.4"))
                .isEqualTo(logins.login("asha@example.com", "x".toCharArray(), "1.2.3.5"));
    }

    @Test
    void repeatedFailuresAreThrottledEvenForTheRightPassword() {
        for (int i = 0; i < 6; i++) {
            logins.login("asha@example.com", "wrong".toCharArray(), "1.2.3.4");
        }
        assertThat(logins.login("asha@example.com", "correct horse".toCharArray(), "1.2.3.4"))
                .isInstanceOf(LoginService.Throttled.class);
    }

    @Test
    void oldHashesAreUpgradedAtTheNextSuccessfulLogin() {
        users.add(new UserDirectory.Account("ravi@example.com", new PasswordHasher(500).hash("pw".toCharArray()),
                new Caller("c02", Set.of(Role.CUSTOMER), Set.of())));
        logins.login("ravi@example.com", "pw".toCharArray(), "1.2.3.4");
        assertThat(hasher.needsRehash(users.find("ravi@example.com").get().passwordHash())).isFalse();
    }
}
