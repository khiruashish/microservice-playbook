package dev.foodfleet.ch09.payment.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository.ConcurrentUpdateException;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.Payment;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The port's contract, written once. Every adapter (JDBC, in-memory, a future one) extends this
 * class, so the fake used in fast tests is proven to behave like the real thing.
 */
abstract class PaymentRepositoryContract {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z").truncatedTo(ChronoUnit.MILLIS);

    protected PaymentRepository repository;

    protected abstract PaymentRepository createRepository();

    @BeforeEach
    void setUp() {
        repository = createRepository();
    }

    private Payment newPayment(String orderId, String key) {
        return Payment.initiate(orderId, Money.ofPaise(87_185), PaymentMethod.upi("ravi@okaxis"), key, NOW);
    }

    @Test
    void savesAndLoadsTheWholeAggregateIncludingRefunds() {
        Payment p = repository.save(newPayment("o-1", "key-1"));
        p.authorized("gw_1");
        p.capture();
        p.refund(Money.ofPaise(8_625), "Raita missing", NOW);
        repository.save(p);

        Payment loaded = repository.findById(p.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(loaded.gatewayReference()).isEqualTo("gw_1");
        assertThat(loaded.refunds()).singleElement().satisfies(r -> {
            assertThat(r.amount()).isEqualTo(Money.ofPaise(8_625));
            assertThat(r.at()).isEqualTo(NOW);
        });
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.method()).isEqualTo(PaymentMethod.upi("ravi@okaxis"));
    }

    @Test
    void staleCopiesCannotOverwriteNewerOnes() {
        Payment saved = repository.save(newPayment("o-1", "key-1"));
        Payment copyA = repository.findById(saved.id()).orElseThrow();
        Payment copyB = repository.findById(saved.id()).orElseThrow();

        copyA.authorized("gw_A");
        repository.save(copyA);
        copyB.declined("late");

        assertThatThrownBy(() -> repository.save(copyB)).isInstanceOf(ConcurrentUpdateException.class);
    }

    @Test
    void idempotencyKeysAreUnique() {
        repository.save(newPayment("o-1", "key-1"));

        assertThatThrownBy(() -> repository.save(newPayment("o-1", "key-1"))).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(repository.findByIdempotencyKey("key-1")).isPresent();
        assertThat(repository.findByIdempotencyKey("key-2")).isEmpty();
    }

    @Test
    void findsOnlyPaymentsThatHoldMoney() {
        Payment declined = repository.save(newPayment("o-1", "key-1"));
        declined.declined("Payer declined");
        repository.save(declined);
        assertThat(repository.findActiveByOrderId("o-1")).isEmpty();

        Payment retry = repository.save(newPayment("o-1", "key-2"));
        retry.authorized("gw_2");
        repository.save(retry);
        assertThat(repository.findActiveByOrderId("o-1")).hasValueSatisfying(p -> assertThat(p.id()).isEqualTo(retry.id()));
    }
}
