package dev.foodfleet.ch09.payment.application.service;

import dev.foodfleet.ch09.payment.application.port.in.AuthorizePayment;
import dev.foodfleet.ch09.payment.application.port.in.CapturePayment;
import dev.foodfleet.ch09.payment.application.port.in.GetPayment;
import dev.foodfleet.ch09.payment.application.port.in.IdempotencyConflictException;
import dev.foodfleet.ch09.payment.application.port.in.PaymentNotFoundException;
import dev.foodfleet.ch09.payment.application.port.in.PaymentView;
import dev.foodfleet.ch09.payment.application.port.in.RefundPayment;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Approved;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Declined;
import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.Payment;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;
import java.time.Clock;
import java.util.Optional;

/**
 * The application service: implements the driving ports by orchestrating the domain and the
 * driven ports. Plain Java, no Spring; the composition root ({@code config/}) wires it.
 *
 * <p>Rule of thumb for side effects: <b>record the intent before calling the outside world</b>.
 * The PENDING payment is saved before the provider is called, so a crash between the two leaves
 * evidence that reconciliation can resolve, instead of money taken with no record of it.
 */
public class PaymentService implements AuthorizePayment, CapturePayment, RefundPayment, GetPayment {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Override
    public PaymentView authorize(Command command) {
        Optional<Payment> existing = payments.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().sameRequestAs(command.orderId(), command.amount())) {
                throw new IdempotencyConflictException(command.idempotencyKey());
            }
            return PaymentView.of(existing.get());                 // a retry: same answer, no second charge
        }

        Payment payment = payments.save(Payment.initiate(command.orderId(), command.amount(), command.method(),
                command.idempotencyKey(), clock.instant()));      // 1. record intent

        switch (gateway.authorize(payment.id(), payment.amount(), payment.method())) {   // 2. side effect
            case Approved approved -> payment.authorized(approved.gatewayReference());
            case Declined declined -> payment.declined(declined.reason());
        }
        return PaymentView.of(payments.save(payment));            // 3. record outcome
    }

    @Override
    public PaymentView capture(PaymentId paymentId) {
        Payment payment = load(paymentId);
        if (payment.status() == PaymentStatus.CAPTURED) {
            return PaymentView.of(payment);                        // capture is idempotent
        }
        payment.capture();                                          // rules first ...
        gateway.capture(payment.gatewayReference(), payment.amount());   // ... then the side effect
        return PaymentView.of(payments.save(payment));
    }

    @Override
    public PaymentView refund(PaymentId paymentId, Money amount, String reason) {
        Payment payment = load(paymentId);
        payment.refund(amount, reason, clock.instant());
        gateway.refund(payment.gatewayReference(), amount);
        return PaymentView.of(payments.save(payment));
    }

    @Override
    public Optional<PaymentView> releaseOrder(String orderId, String reason) {
        return payments.findActiveByOrderId(orderId).map(payment -> {
            if (payment.status() == PaymentStatus.AUTHORIZED) {
                payment.voidAuthorization();
                gateway.voidAuthorization(payment.gatewayReference());
            } else {
                Money remaining = payment.refundable();
                payment.refund(remaining, reason, clock.instant());
                gateway.refund(payment.gatewayReference(), remaining);
            }
            return PaymentView.of(payments.save(payment));
        });
    }

    @Override
    public PaymentView get(PaymentId paymentId) {
        return PaymentView.of(load(paymentId));
    }

    private Payment load(PaymentId paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId.value()));
    }
}
