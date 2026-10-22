package dev.foodfleet.ch50;

import dev.foodfleet.ch50.masking.Masker;
import dev.foodfleet.ch50.masking.Pii;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskerTest {

    @Test
    void masksEmailAndUpiAddresses() {
        assertThat(Masker.mask("receipt sent to asha.rao@example.in"))
                .isEqualTo("receipt sent to a***@example.in");
        assertThat(Masker.mask("refund to asha.rao@okhdfc failed"))
                .isEqualTo("refund to a***@okhdfc failed");
    }

    @Test
    void leavesAnnotationsAndHandlesThatAreNotAddressesAlone() {
        assertThat(Masker.mask("see @Transactional on retry@attempt")).isEqualTo("see @Transactional on retry@attempt");
    }

    @Test
    void masksMobileNumbersInTheirUsualShapes() {
        assertThat(Masker.mask("call 9876543210")).isEqualTo("call ******3210");
        assertThat(Masker.mask("call +91 98765 43210")).isEqualTo("call ******3210");
        assertThat(Masker.mask("call 09876543210")).isEqualTo("call ******3210");
    }

    @Test
    void masksLuhnValidCardNumbersKeepingTheLastFour() {
        assertThat(Masker.mask("card 4111 1111 1111 1111 declined")).isEqualTo("card **** **** **** 1111 declined");
        assertThat(Masker.mask("card 5555-5555-5555-4444")).isEqualTo("card **** **** **** 4444");
    }

    @Test
    void leavesTimestampsOrderIdsAndInvalidCardNumbersAlone() {
        String line = "{\"timestamp\":1760423456789,\"order\":\"o-100234\",\"amount\":45900,\"ref\":4111111111111112}";
        assertThat(Masker.mask(line)).isEqualTo(line);
        assertThat(Masker.mask("took 1234 ms, 5 retries")).isEqualTo("took 1234 ms, 5 retries");
    }

    @Test
    void masksJwtsAndBearerTokens() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjLTQyIn0.c2lnbmF0dXJlLWJ5dGVz";
        assertThat(Masker.mask("token=" + jwt + " ok")).isEqualTo("token=[jwt] ok");
        assertThat(Masker.mask("Authorization: Bearer abcdefghijklmnop1234")).isEqualTo("Authorization: Bearer [token]");
    }

    @Test
    void returnsTheSameStringWhenThereIsNothingToMask() {
        String s = "order o-1 placed";
        assertThat(Masker.mask(s) == s).isTrue();
    }

    @Test
    void piiMasksItselfWhenTurnedIntoText() {
        assertThat(Pii.email("asha.rao@example.in").toString()).isEqualTo("a***@example.in");
        assertThat(Pii.phone("9876543210").toString()).isEqualTo("******3210");
        assertThat(Pii.vpa("asha@okicici").toString()).isEqualTo("a***@okicici");
        assertThat(Pii.email("asha.rao@example.in").value()).isEqualTo("asha.rao@example.in");
    }
}
