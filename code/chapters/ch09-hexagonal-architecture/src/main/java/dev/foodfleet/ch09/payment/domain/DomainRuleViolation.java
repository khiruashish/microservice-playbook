package dev.foodfleet.ch09.payment.domain;

/** A business rule said no. Adapters map this to 422; it is never retried. */
public class DomainRuleViolation extends RuntimeException {

    public DomainRuleViolation(String message) {
        super(message);
    }
}
