package dev.foodfleet.ch40.validation;

/**
 * A request value that was rejected. Carries the field and the rule, never the value: the value is attacker
 * controlled and goes nowhere near logs or error responses.
 */
public final class InvalidInput extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String field;
    private final String rule;

    public InvalidInput(String field, String rule) {
        super(field + ": " + rule);
        this.field = field;
        this.rule = rule;
    }

    public String field() {
        return field;
    }

    public String rule() {
        return rule;
    }
}
