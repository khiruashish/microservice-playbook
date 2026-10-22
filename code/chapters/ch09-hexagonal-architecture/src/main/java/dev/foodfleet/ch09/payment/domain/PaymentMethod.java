package dev.foodfleet.ch09.payment.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How the customer pays. Cards are represented only by a token from the payment provider:
 * card numbers never enter this service (it stays out of PCI DSS scope, chapter 6).
 */
public record PaymentMethod(Type type, String reference) {

    public enum Type { UPI, CARD_TOKEN }

    private static final Pattern UPI_HANDLE = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$");

    public PaymentMethod {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reference, "reference");
        if (type == Type.UPI && !UPI_HANDLE.matcher(reference).matches()) {
            throw new DomainRuleViolation("Not a valid UPI ID: " + reference);
        }
        if (type == Type.CARD_TOKEN && !reference.startsWith("tok_")) {
            throw new DomainRuleViolation("Cards must be tokenized by the payment provider");
        }
    }

    public static PaymentMethod upi(String handle) {
        return new PaymentMethod(Type.UPI, handle);
    }
}
