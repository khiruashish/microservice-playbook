package dev.foodfleet.ch09.payment.application.port.out;

import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;

/**
 * Driven port to the payment provider, in our words, not the provider's. A decline is a normal
 * answer ({@link Declined}); only technical failures are exceptions ({@link GatewayUnavailableException}).
 */
public interface PaymentGateway {

    /** {@code paymentId} doubles as the provider's idempotency key, so a retried call can't charge twice. */
    Authorization authorize(PaymentId paymentId, Money amount, PaymentMethod method);

    void capture(String gatewayReference, Money amount);

    void voidAuthorization(String gatewayReference);

    void refund(String gatewayReference, Money amount);

    sealed interface Authorization permits Approved, Declined {
    }

    record Approved(String gatewayReference) implements Authorization {
    }

    record Declined(String reason) implements Authorization {
    }

    class GatewayUnavailableException extends RuntimeException {
        public GatewayUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
