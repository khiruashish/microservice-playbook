package dev.foodfleet.ch09.payment.application.port.out;

import dev.foodfleet.ch09.payment.domain.Payment;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import java.util.Optional;

/**
 * Driven port, owned by the application: it says what the use cases need from storage, in domain
 * terms. Adapters implement it (JDBC here, an in-memory fake in tests); every adapter must pass
 * the same contract test.
 */
public interface PaymentRepository {

    /** Inserts or updates; throws {@link ConcurrentUpdateException} if someone else saved first. */
    Payment save(Payment payment);

    Optional<Payment> findById(PaymentId id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** The order's payment that currently holds money (authorized or captured), if any. */
    Optional<Payment> findActiveByOrderId(String orderId);

    class ConcurrentUpdateException extends RuntimeException {
        public ConcurrentUpdateException(String paymentId) {
            super("Payment " + paymentId + " was modified concurrently");
        }
    }
}
