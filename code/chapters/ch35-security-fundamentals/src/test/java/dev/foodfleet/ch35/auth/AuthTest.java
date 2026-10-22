package dev.foodfleet.ch35.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2025-04-14T10:00:00Z");

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

    @Test
    void afterFiveFailuresEachAttemptWaitsTwiceAsLong() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = LoginThrottle.standard(clock);
        for (int i = 0; i < 5; i++) {
            assertThat(throttle.check("asha", "1.2.3.4").allowed()).isTrue();
            throttle.failed("asha", "1.2.3.4");
        }
        assertThat(throttle.check("asha", "1.2.3.4").allowed()).isTrue();
        throttle.failed("asha", "1.2.3.4");                                     // 6th failure: wait 1 s
        assertThat(throttle.check("asha", "1.2.3.4").retryAfter()).isEqualTo(Duration.ofSeconds(1));
        clock.now = clock.now.plusSeconds(1);
        throttle.failed("asha", "1.2.3.4");                                     // 7th: wait 2 s
        assertThat(throttle.check("asha", "5.6.7.8").retryAfter()).isEqualTo(Duration.ofSeconds(2));  // any source
    }

    @Test
    void aSuccessfulLoginResetsTheAccount() {
        MutableClock clock = new MutableClock();
        LoginThrottle throttle = LoginThrottle.standard(clock);
        for (int i = 0; i < 8; i++) {
            throttle.failed("asha", "1.2.3." + i);
        }
        throttle.succeeded("asha");
        assertThat(throttle.check("asha", "9.9.9.9").allowed()).isTrue();
    }

    @Test
    void hashesAreSaltedVerifiableAndUpgradable() {
        PasswordHasher weak = new PasswordHasher(1_000);
        String a = weak.hash("dosa1234".toCharArray());
        String b = weak.hash("dosa1234".toCharArray());
        assertThat(a).isNotEqualTo(b);                                           // different salts
        assertThat(weak.matches("dosa1234".toCharArray(), a)).isTrue();
        assertThat(weak.matches("dosa1235".toCharArray(), a)).isFalse();
        assertThat(new PasswordHasher(2_000).needsRehash(a)).isTrue();
        assertThat(weak.needsRehash(a)).isFalse();
    }
}
