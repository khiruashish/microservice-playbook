package dev.foodfleet.ch09.payment.adapter.in.web;

import dev.foodfleet.ch09.payment.application.port.in.AuthorizePayment;
import dev.foodfleet.ch09.payment.application.port.in.CapturePayment;
import dev.foodfleet.ch09.payment.application.port.in.GetPayment;
import dev.foodfleet.ch09.payment.application.port.in.PaymentView;
import dev.foodfleet.ch09.payment.application.port.in.RefundPayment;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter: translates HTTP into use-case calls. It depends on the driving ports only,
 * never on {@code PaymentService} or a repository (an ArchUnit rule checks this).
 */
@RestController
@RequestMapping("/api/payments")
class PaymentController {

    private final AuthorizePayment authorize;
    private final CapturePayment capture;
    private final RefundPayment refund;
    private final GetPayment get;

    PaymentController(AuthorizePayment authorize, CapturePayment capture, RefundPayment refund, GetPayment get) {
        this.authorize = authorize;
        this.capture = capture;
        this.refund = refund;
        this.get = get;
    }

    @PostMapping
    ResponseEntity<PaymentView> authorize(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @Valid @RequestBody AuthorizeRequest request) {
        PaymentView view = authorize.authorize(new AuthorizePayment.Command(request.orderId(),
                Money.ofPaise(request.amountPaise()),
                new PaymentMethod(request.method().type(), request.method().reference()),
                idempotencyKey));
        return ResponseEntity.created(URI.create("/api/payments/" + view.paymentId())).body(view);
    }

    @GetMapping("/{paymentId}")
    PaymentView get(@PathVariable String paymentId) {
        return get.get(new PaymentId(paymentId));
    }

    @PostMapping("/{paymentId}/capture")
    PaymentView capture(@PathVariable String paymentId) {
        return capture.capture(new PaymentId(paymentId));
    }

    @PostMapping("/{paymentId}/refunds")
    ResponseEntity<PaymentView> refund(@PathVariable String paymentId, @Valid @RequestBody RefundRequest request) {
        PaymentView view = refund.refund(new PaymentId(paymentId), Money.ofPaise(request.amountPaise()), request.reason());
        return ResponseEntity.status(201).body(view);
    }

    record AuthorizeRequest(@NotBlank String orderId, @Positive long amountPaise, @NotNull @Valid MethodDto method) {
    }

    record MethodDto(@NotNull PaymentMethod.Type type, @NotBlank String reference) {
    }

    record RefundRequest(@Positive long amountPaise, @NotBlank String reason) {
    }
}
