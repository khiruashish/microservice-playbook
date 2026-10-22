package dev.foodfleet.ch09.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The aggregate's rules, tested without Spring, a database or a provider. Milliseconds per test. */
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    private Payment capturedPayment(long paise) {
        Payment p = Payment.initiate("o-1", Money.ofPaise(paise), PaymentMethod.upi("ravi@okaxis"), "key-1", NOW);
        p.authorized("gw_1");
        p.capture();
        return p;
    }

    @Test
    void partialRefundsAddUpToAFullRefund() {
        Payment p = capturedPayment(87_185);

        p.refund(Money.ofPaise(8_625), "Raita missing", NOW);
        assertThat(p.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(p.refundable()).isEqualTo(Money.ofPaise(78_560));

        p.refund(Money.ofPaise(78_560), "Order cancelled", NOW);
        assertThat(p.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.refundable().isZero()).isTrue();
    }

    @Test
    void cannotRefundMoreThanWasCaptured() {
        Payment p = capturedPayment(10_000);
        p.refund(Money.ofPaise(6_000), "Late delivery", NOW);

        assertThatThrownBy(() -> p.refund(Money.ofPaise(4_001), "Again", NOW))
                .isInstanceOf(DomainRuleViolation.class)
                .hasMessage("Refund of ₹40.01 exceeds the refundable ₹40.00");
    }

    @Test
    void lifecycleRulesAreEnforced() {
        Payment p = Payment.initiate("o-1", Money.ofPaise(5_000), PaymentMethod.upi("ravi@okaxis"), "key-1", NOW);

        assertThatThrownBy(p::capture).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> p.refund(Money.ofPaise(1), "x", NOW))
                .hasMessage("Only captured payments can be refunded; this one is PENDING");
        p.declined("Payer declined");
        assertThatThrownBy(() -> p.authorized("gw_late")).isInstanceOf(DomainRuleViolation.class);
    }

    @Test
    void invalidMethodsAndAmountsNeverGetIn() {
        assertThatThrownBy(() -> PaymentMethod.upi("not-a-upi-id")).isInstanceOf(DomainRuleViolation.class);
        assertThatThrownBy(() -> new PaymentMethod(PaymentMethod.Type.CARD_TOKEN, "4111111111111111"))
                .hasMessage("Cards must be tokenized by the payment provider");
        assertThatThrownBy(() -> Payment.initiate("o-1", Money.ZERO, PaymentMethod.upi("ravi@okaxis"), "k", NOW))
                .isInstanceOf(DomainRuleViolation.class);
    }
}
