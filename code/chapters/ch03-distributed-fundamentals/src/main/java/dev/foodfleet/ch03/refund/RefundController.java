package dev.foodfleet.ch03.refund;

import dev.foodfleet.ch03.idempotency.IdempotencyKeyExecutor;
import dev.foodfleet.ch03.idempotency.IdempotencyKeyExecutor.Outcome;
import dev.foodfleet.ch03.refund.RefundLedger.Refund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST is not idempotent, but customers' apps retry when the network times out. Fallacy #1 again:
 * a timeout doesn't tell you whether the refund happened. The {@code Idempotency-Key} header makes the
 * retry safe: same key + same body → the original result, and the refund is issued only once.
 *
 * <p>The {@code @Pattern} on the header is checked by Spring MVC's built-in method validation
 * (no {@code @Validated} needed), and a bad key becomes a 400 Problem Details response.
 */
@RestController
@RequestMapping("/api/v1/refunds")
class RefundController {

    private final RefundLedger ledger;
    private final IdempotencyKeyExecutor<RefundResponse> idempotency;

    RefundController(RefundLedger ledger, IdempotencyKeyExecutor<RefundResponse> idempotency) {
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @PostMapping
    ResponseEntity<RefundResponse> refund(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {

        String fingerprint = IdempotencyKeyExecutor.fingerprint(request.orderId(), Long.toString(request.amountPaise()));
        Outcome<RefundResponse> outcome = idempotency.execute(idempotencyKey, fingerprint, () -> {
            Refund refund = ledger.issue(request.orderId(), request.amountPaise());
            return new RefundResponse(refund.refundId(), refund.orderId(), refund.amountPaise(), "ISSUED");
        });

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", Boolean.toString(outcome.replayed()))
                .body(outcome.result());
    }

    record RefundRequest(@NotBlank String orderId, @Positive long amountPaise) {
    }

    record RefundResponse(UUID refundId, String orderId, long amountPaise, String status) {
    }
}
