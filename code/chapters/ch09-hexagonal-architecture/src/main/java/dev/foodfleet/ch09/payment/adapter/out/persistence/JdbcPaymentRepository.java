package dev.foodfleet.ch09.payment.adapter.out.persistence;

import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.Payment;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;
import dev.foodfleet.ch09.payment.domain.Refund;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Driven adapter: implements the application's {@link PaymentRepository} port with plain SQL.
 * The domain has no annotations; all mapping lives here. Optimistic locking via {@code version}.
 */
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public JdbcPaymentRepository(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Payment save(Payment p) {
        return tx.execute(status -> {
            long next = p.version() + 1;
            if (p.version() == 0) {
                insert(p);
            } else {
                int updated = jdbc.sql("""
                        update payment set status = :status, gateway_reference = :ref, decline_reason = :decline,
                               version = :next
                        where id = :id and version = :version""")
                        .param("status", p.status().name())
                        .param("ref", p.gatewayReference())
                        .param("decline", p.declineReason())
                        .param("next", next)
                        .param("id", p.id().value())
                        .param("version", p.version())
                        .update();
                if (updated == 0) {
                    throw new ConcurrentUpdateException(p.id().value());
                }
                jdbc.sql("delete from payment_refund where payment_id = :id").param("id", p.id().value()).update();
            }
            List<Refund> refunds = p.refunds();
            for (int i = 0; i < refunds.size(); i++) {
                Refund r = refunds.get(i);
                jdbc.sql("""
                        insert into payment_refund (payment_id, seq, amount_paise, reason, refunded_at)
                        values (:id, :seq, :amount, :reason, :at)""")
                        .param("id", p.id().value())
                        .param("seq", i)
                        .param("amount", r.amount().paise())
                        .param("reason", r.reason())
                        .param("at", Timestamp.from(r.at()))
                        .update();
            }
            return Payment.restore(p.id(), p.orderId(), p.amount(), p.method(), p.idempotencyKey(), p.createdAt(),
                    p.status(), p.gatewayReference(), p.declineReason(), refunds, next);
        });
    }

    private void insert(Payment p) {
        try {
            jdbc.sql("""
                    insert into payment (id, order_id, amount_paise, method_type, method_reference, idempotency_key,
                                         created_at, status, gateway_reference, decline_reason, version)
                    values (:id, :order, :amount, :type, :reference, :key, :created, :status, :ref, :decline, 1)""")
                    .param("id", p.id().value())
                    .param("order", p.orderId())
                    .param("amount", p.amount().paise())
                    .param("type", p.method().type().name())
                    .param("reference", p.method().reference())
                    .param("key", p.idempotencyKey())
                    .param("created", Timestamp.from(p.createdAt()))
                    .param("status", p.status().name())
                    .param("ref", p.gatewayReference())
                    .param("decline", p.declineReason())
                    .update();
        } catch (DuplicateKeyException e) {
            // Two concurrent requests with the same Idempotency-Key: the unique index decides the winner.
            throw new ConcurrentUpdateException(p.id().value());
        }
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return findOne("id = :value", id.value());
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return findOne("idempotency_key = :value", idempotencyKey);
    }

    @Override
    public Optional<Payment> findActiveByOrderId(String orderId) {
        return findOne("order_id = :value and status in ('AUTHORIZED', 'CAPTURED', 'PARTIALLY_REFUNDED')", orderId);
    }

    private Optional<Payment> findOne(String where, String value) {
        return jdbc.sql("select * from payment where " + where)
                .param("value", value)
                .query(this::row)
                .optional()
                .map(this::withRefunds);
    }

    private Payment row(ResultSet rs, int rowNum) throws SQLException {
        return Payment.restore(
                new PaymentId(rs.getString("id")),
                rs.getString("order_id"),
                Money.ofPaise(rs.getLong("amount_paise")),
                new PaymentMethod(PaymentMethod.Type.valueOf(rs.getString("method_type")), rs.getString("method_reference")),
                rs.getString("idempotency_key"),
                rs.getTimestamp("created_at").toInstant(),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("gateway_reference"),
                rs.getString("decline_reason"),
                List.of(),
                rs.getLong("version"));
    }

    private Payment withRefunds(Payment p) {
        List<Refund> refunds = jdbc.sql("select * from payment_refund where payment_id = :id order by seq")
                .param("id", p.id().value())
                .query((rs, n) -> new Refund(Money.ofPaise(rs.getLong("amount_paise")), rs.getString("reason"),
                        rs.getTimestamp("refunded_at").toInstant()))
                .list();
        return Payment.restore(p.id(), p.orderId(), p.amount(), p.method(), p.idempotencyKey(), p.createdAt(),
                p.status(), p.gatewayReference(), p.declineReason(), refunds, p.version());
    }
}
