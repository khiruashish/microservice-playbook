package dev.foodfleet.ch09.payment.adapter.out.gateway;

import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Approved;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Authorization;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.Declined;
import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway.GatewayUnavailableException;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;
import java.util.Map;

/**
 * The anti-corruption layer inside the gateway adapter (chapter 5): provider vocabulary in,
 * our vocabulary out. Pure functions, so every mapping decision is unit-tested.
 */
final class PspTranslator {

    private static final Map<String, String> DECLINE_REASONS = Map.of(
            "INSUFFICIENT_FUNDS", "Insufficient balance in the payer's account",
            "UPI_COLLECT_EXPIRED", "The UPI request expired before it was approved",
            "PAYER_DECLINED", "The payer declined the UPI request",
            "RISK_REJECTED", "Declined by the bank");

    private PspTranslator() {
    }

    static Authorization toAuthorization(PspClient.PspPayment response) {
        return switch (response.status()) {
            case "authorized" -> new Approved(response.id());
            case "failed" -> new Declined(DECLINE_REASONS.getOrDefault(
                    response.errorCode() == null ? "" : response.errorCode(), "Declined by the payment provider"));
            // "created" means the provider hasn't decided yet; anything else is unknown.
            // Never treat an unknown answer as approved: fail safe, let reconciliation settle it.
            default -> throw new GatewayUnavailableException(
                    "Provider returned status '" + response.status() + "' for " + response.id(), null);
        };
    }

    static String toPspMethod(PaymentMethod method) {
        return switch (method.type()) {
            case UPI -> "upi";
            case CARD_TOKEN -> "card";
        };
    }
}
