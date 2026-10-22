package dev.foodfleet.ch09.payment.support;

import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import dev.foodfleet.ch09.payment.domain.Payment;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A real implementation of the port, in memory. Not a mock: it passes the same contract test as
 * the JDBC adapter, so tests that use it test real behaviour (versions, uniqueness, copies).
 */
public class InMemoryPaymentRepository implements PaymentRepository {

    private static final Set<PaymentStatus> ACTIVE =
            EnumSet.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED);

    private final Map<PaymentId, Payment> rows = new ConcurrentHashMap<>();

    @Override
    public synchronized Payment save(Payment p) {
        Payment stored = rows.get(p.id());
        long currentVersion = stored == null ? 0 : stored.version();
        if (currentVersion != p.version()) {
            throw new ConcurrentUpdateException(p.id().value());
        }
        if (stored == null && findByIdempotencyKey(p.idempotencyKey()).isPresent()) {
            throw new ConcurrentUpdateException(p.id().value());
        }
        Payment saved = copy(p, p.version() + 1);
        rows.put(p.id(), saved);
        return copy(saved, saved.version());
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return Optional.ofNullable(rows.get(id)).map(p -> copy(p, p.version()));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return rows.values().stream().filter(p -> p.idempotencyKey().equals(idempotencyKey))
                .findFirst().map(p -> copy(p, p.version()));
    }

    @Override
    public Optional<Payment> findActiveByOrderId(String orderId) {
        return rows.values().stream().filter(p -> p.orderId().equals(orderId) && ACTIVE.contains(p.status()))
                .findFirst().map(p -> copy(p, p.version()));
    }

    /** Callers get copies, like rows loaded from a database: changing one doesn't change storage. */
    private static Payment copy(Payment p, long version) {
        return Payment.restore(p.id(), p.orderId(), p.amount(), p.method(), p.idempotencyKey(), p.createdAt(),
                p.status(), p.gatewayReference(), p.declineReason(), p.refunds(), version);
    }
}
