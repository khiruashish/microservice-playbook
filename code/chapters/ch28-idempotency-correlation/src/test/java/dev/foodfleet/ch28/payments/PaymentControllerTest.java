package dev.foodfleet.ch28.payments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentControllerTest {

    @Test
    void idempotencyKeysAreStructuredFieldStrings() {
        assertThat(PaymentController.parseKey("\"8e03978e-40d5-43e8-bc93-6894a57f9324\""))
                .isEqualTo("8e03978e-40d5-43e8-bc93-6894a57f9324");
        assertThat(PaymentController.parseKey("8e03978e")).isEqualTo("8e03978e");        // lenient
        assertThat(PaymentController.parseKey("\"\"")).isNull();
        assertThat(PaymentController.parseKey("has space")).isNull();
        assertThat(PaymentController.parseKey(null)).isNull();
    }

    @Test
    void theFingerprintDependsOnTheRequestOnly() {
        assertThat(PaymentService.fingerprint(new PaymentRequest("o-1", 100)))
                .isEqualTo(PaymentService.fingerprint(new PaymentRequest("o-1", 100)));
        assertThat(PaymentService.fingerprint(new PaymentRequest("o-1", 100)))
                .isNotEqualTo(PaymentService.fingerprint(new PaymentRequest("o-1", 101)));
    }
}
