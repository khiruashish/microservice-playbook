package dev.foodfleet.ch27.compat;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumer-driven contract verification, as Spring Cloud Contract or Pact run it in the provider's
 * build: for each consumer that published a contract, call the provider in a known state and check
 * that the response has the fields the consumer listed, with the right types, matching the
 * contract's example values, and that interactions behave as recorded (an unknown order returns
 * 404). One example response per interaction (status PICKED_UP, rider assigned).
 */
public final class ContractChecks {

    public static final String EXAMPLE_STATUS = "PICKED_UP";

    private ContractChecks() {
    }

    /** Failures for one consumer's contract against the provider's new schema. */
    public static List<String> verify(Consumer consumer, Schema provider) {
        List<String> failures = new ArrayList<>();
        if (!consumer.hasContract()) {
            return failures;
        }
        for (var e : consumer.reads().entrySet()) {
            if (!e.getValue().required()) {
                continue;                                  // contracts list what the consumer needs
            }
            Schema.Field f = provider.at(e.getKey());
            if (e.getKey().startsWith("@")) {             // an interaction in the contract (e.g. unknown order -> 404)
                if (f != null && !f.meaning().equals(e.getValue().meaning())) {
                    failures.add(e.getKey() + ": expected " + e.getValue().meaning() + ", got " + f.meaning());
                }
                continue;
            }
            if (f == null) {
                failures.add(e.getKey() + " missing from response");
            } else if (f.type() != e.getValue().type()
                    && !(f.type() == Schema.Type.ENUM && e.getValue().type() == Schema.Type.STRING)) {
                failures.add(e.getKey() + " has the wrong type");
            }
            // The example response contains every field, so "sometimes absent" passes; meaning is
            // not checked; unknown fields in the response are allowed by the contract.
        }
        if (consumer.reads().containsKey("status") && provider.at("status") != null
                && !provider.at("status").enumValues().contains(EXAMPLE_STATUS)) {
            failures.add("example status " + EXAMPLE_STATUS + " no longer produced");
        }
        return failures;
    }
}
