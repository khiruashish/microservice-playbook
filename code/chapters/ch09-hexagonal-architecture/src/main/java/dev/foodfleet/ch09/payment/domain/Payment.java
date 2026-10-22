package dev.foodfleet.ch09.payment.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Payment aggregate. Every state change goes through a method that checks the rules; there
 * are no setters. Knows nothing about HTTP, SQL or the payment provider.
 */
public final class Payment {

    private final PaymentId id;
    private final String orderId;
    private final Money amount;
    private final PaymentMethod method;
    private final String idempotencyKey;
    private final Instant createdAt;
    private PaymentStatus status;
    private String gatewayReference;
    private String declineReason;
    private final List<Refund> refunds;
    private final long version;

    private Payment(PaymentId id, String orderId, Money amount, PaymentMethod method, String idempotencyKey,
                    Instant createdAt, PaymentStatus status, String gatewayReference, String declineReason,
                    List<Refund> refunds, long version) {
        this.id = Objects.requireNonNull(id);
        this.orderId = Objects.requireNonNull(orderId);
        this.amount = Objects.requireNonNull(amount);
        this.method = Objects.requireNonNull(method);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.status = Objects.requireNonNull(status);
        this.gatewayReference = gatewayReference;
        this.declineReason = declineReason;
        this.refunds = new ArrayList<>(refunds);
        this.version = version;
    }

    /** Factory: a new payment starts PENDING, before the provider is called. */
    public static Payment initiate(String orderId, Money amount, PaymentMethod method, String idempotencyKey, Instant now) {
        if (amount.isZero()) {
            throw new DomainRuleViolation("A payment must be for more than ₹0.00");
        }
        if (amount.compareTo(Money.ofPaise(20_000_00)) > 0) {
            throw new DomainRuleViolation("Food orders are limited to ₹20000.00");
        }
        return new Payment(PaymentId.newId(), orderId, amount, method, idempotencyKey, now,
                PaymentStatus.PENDING, null, null, List.of(), 0);
    }

    /** Rebuilds a payment from storage; used only by repository adapters. */
    public static Payment restore(PaymentId id, String orderId, Money amount, PaymentMethod method, String idempotencyKey,
                                  Instant createdAt, PaymentStatus status, String gatewayReference, String declineReason,
                                  List<Refund> refunds, long version) {
        return new Payment(id, orderId, amount, method, idempotencyKey, createdAt, status, gatewayReference,
                declineReason, refunds, version);
    }

    public void authorized(String reference) {
        require(PaymentStatus.PENDING);
        this.gatewayReference = Objects.requireNonNull(reference);
        this.status = PaymentStatus.AUTHORIZED;
    }

    public void declined(String reason) {
        require(PaymentStatus.PENDING);
        this.declineReason = Objects.requireNonNull(reason);
        this.status = PaymentStatus.DECLINED;
    }

    public void capture() {
        require(PaymentStatus.AUTHORIZED);
        this.status = PaymentStatus.CAPTURED;
    }

    public void voidAuthorization() {
        require(PaymentStatus.AUTHORIZED);
        this.status = PaymentStatus.VOIDED;
    }

    /** Partial refunds are allowed; the total can never exceed what was captured. */
    public Refund refund(Money refundAmount, String reason, Instant now) {
        if (status != PaymentStatus.CAPTURED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new DomainRuleViolation("Only captured payments can be refunded; this one is " + status);
        }
        if (refundAmount.isZero()) {
            throw new DomainRuleViolation("A refund must be for more than ₹0.00");
        }
        if (refundAmount.compareTo(refundable()) > 0) {
            throw new DomainRuleViolation("Refund of " + refundAmount + " exceeds the refundable " + refundable());
        }
        Refund refund = new Refund(refundAmount, reason, now);
        refunds.add(refund);
        status = refundable().isZero() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        return refund;
    }

    public Money refunded() {
        return refunds.stream().map(Refund::amount).reduce(Money.ZERO, Money::plus);
    }

    public Money refundable() {
        return amount.minus(refunded());
    }

    /** Same idempotency key must mean the same request; anything else is a client bug. */
    public boolean sameRequestAs(String otherOrderId, Money otherAmount) {
        return orderId.equals(otherOrderId) && amount.equals(otherAmount);
    }

    private void require(PaymentStatus expected) {
        if (status != expected) {
            throw new DomainRuleViolation("Payment " + id + " is " + status + ", expected " + expected);
        }
    }

    public PaymentId id() { return id; }
    public String orderId() { return orderId; }
    public Money amount() { return amount; }
    public PaymentMethod method() { return method; }
    public String idempotencyKey() { return idempotencyKey; }
    public Instant createdAt() { return createdAt; }
    public PaymentStatus status() { return status; }
    public String gatewayReference() { return gatewayReference; }
    public String declineReason() { return declineReason; }
    public List<Refund> refunds() { return List.copyOf(refunds); }
    public long version() { return version; }
}
