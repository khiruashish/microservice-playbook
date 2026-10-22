package dev.foodfleet.ch14.saga;

import java.util.Objects;

/**
 * One local transaction in another service, plus how to undo it.
 *
 * @param action       calls the participant; must be idempotent for the same (sagaId, step)
 * @param compensation semantically undoes the action; idempotent too; null for pivot and retriable steps
 */
public record SagaStep<C>(String name, StepKind kind, Action<C> action, Action<C> compensation) {

    @FunctionalInterface
    public interface Action<C> {
        void run(String sagaId, C context);
    }

    public SagaStep {
        Objects.requireNonNull(name);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(action);
        if ((kind == StepKind.COMPENSATABLE) != (compensation != null)) {
            throw new IllegalArgumentException(name + ": compensatable steps need a compensation, others must not have one");
        }
    }

    public static <C> SagaStep<C> compensatable(String name, Action<C> action, Action<C> compensation) {
        return new SagaStep<>(name, StepKind.COMPENSATABLE, action, compensation);
    }

    public static <C> SagaStep<C> pivot(String name, Action<C> action) {
        return new SagaStep<>(name, StepKind.PIVOT, action, null);
    }

    public static <C> SagaStep<C> retriable(String name, Action<C> action) {
        return new SagaStep<>(name, StepKind.RETRIABLE, action, null);
    }
}
