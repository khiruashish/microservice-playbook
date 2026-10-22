package dev.foodfleet.ch13.wallet;

public class WalletRuleViolation extends RuntimeException {

    public WalletRuleViolation(String message) {
        super(message);
    }
}
