package dev.foodfleet.ch09.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch09.payment.application.port.in.AuthorizePayment.Command;
import dev.foodfleet.ch09.payment.application.port.in.IdempotencyConflictException;
import dev.foodfleet.ch09.payment.application.port.in.PaymentView;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.GatewayUnavailableException;
import dev.foodfleet.ch09.payment.application.service.PaymentService;
import dev.foodfleet.ch09.payment.domain.DomainRuleViolation;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;
import dev.foodfleet.ch09.payment.support.InMemoryPaymentRepository;
import dev.foodfleet.ch09.payment.support.RecordingGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Use cases tested through the driving ports, with the driven ports replaced by fakes.
 * No Spring context, no mocks, no network: the whole hexagon runs in-process.
 */
class PaymentServiceTest {

    private final InMemoryPaymentRepository repository = new InMemoryPaymentRepository();
    private final RecordingGateway gateway = new RecordingGateway();
    private final PaymentService payments = new PaymentService(repository, gateway,
            Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC));

    private Command order(String key, long paise) {
        return new Command("o-1", Money.ofPaise(paise), PaymentMethod.upi("ravi@okaxis"), key);
    }

    @Test
    void retryWithTheSameKeyReturnsTheSamePaymentAndChargesOnce() {
        PaymentView first = payments.authorize(order("key-1", 87_185));
        PaymentView retry = payments.authorize(order("key-1", 87_185));

        assertThat(retry).isEqualTo(first);
        assertThat(first.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(gateway.calls).hasSize(1);
    }

    @Test
    void sameKeyForADifferentRequestIsAConflict() {
        payments.authorize(order("key-1", 87_185));

        assertThatThrownBy(() -> payments.authorize(order("key-1", 99_000)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aDeclineIsAnAnswerNotAnError() {
        gateway.declineNext("The payer declined the UPI request");

        PaymentView view = payments.authorize(order("key-2", 5_000));

        assertThat(view.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(view.declineReason()).isEqualTo("The payer declined the UPI request");
    }

    @Test
    void anOutageLeavesAPendingRecordForReconciliation() {
        gateway.down(true);

        assertThatThrownBy(() -> payments.authorize(order("key-3", 5_000)))
                .isInstanceOf(GatewayUnavailableException.class);
        assertThat(repository.findByIdempotencyKey("key-3")).hasValueSatisfying(
                p -> assertThat(p.status()).isEqualTo(PaymentStatus.PENDING));
    }

    @Test
    void releasingAnOrderVoidsAnAuthorizationAndRefundsACapture() {
        PaymentView authorized = payments.authorize(order("key-4", 40_000));
        assertThat(payments.releaseOrder("o-1", "Kitchen rejected")).hasValueSatisfying(
                v -> assertThat(v.status()).isEqualTo(PaymentStatus.VOIDED));

        PaymentView second = payments.authorize(new Command("o-2", Money.ofPaise(30_000), PaymentMethod.upi("ravi@okaxis"), "key-5"));
        payments.capture(new PaymentId(second.paymentId()));
        payments.refund(new PaymentId(second.paymentId()), Money.ofPaise(10_000), "Item missing");
        assertThat(payments.releaseOrder("o-2", "Kitchen rejected")).hasValueSatisfying(v -> {
            assertThat(v.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(v.refundedPaise()).isEqualTo(30_000);
        });
        assertThat(gateway.calls).contains("void gw_" + authorized.paymentId(), "refund gw_" + second.paymentId() + " ₹200.00");
        assertThat(payments.releaseOrder("o-3", "nothing")).isEmpty();
    }

    @Test
    void ruleViolationsHappenBeforeAnySideEffect() {
        PaymentView view = payments.authorize(order("key-6", 5_000));
        int callsBefore = gateway.calls.size();

        assertThatThrownBy(() -> payments.refund(new PaymentId(view.paymentId()), Money.ofPaise(100), "too early"))
                .isInstanceOf(DomainRuleViolation.class);
        assertThat(gateway.calls).hasSize(callsBefore);
    }
}
