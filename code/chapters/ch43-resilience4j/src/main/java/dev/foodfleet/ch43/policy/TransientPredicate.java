package dev.foodfleet.ch43.policy;

import java.util.function.Predicate;

/** {@link Transient#failure} as a class, for {@code retry-exception-predicate} in YAML. */
public class TransientPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        return Transient.failure(t);
    }
}
