package dev.foodfleet.ch14.saga;

import dev.foodfleet.ch14.saga.SagaFailure.BusinessRejection;
import dev.foodfleet.ch14.saga.SagaFailure.Transient;

/**
 * Runs a saga step by step, recording progress in the {@link SagaLog}:
 * <ul>
 *   <li>a <b>transient</b> failure is retried (up to {@code maxAttempts});</li>
 *   <li>a <b>business rejection</b>, or retries exhausted, before or at the pivot: compensate the
 *       completed compensatable steps in <b>reverse order</b>;</li>
 *   <li>after the pivot there is no going back: retriable steps are retried, and if they keep
 *       failing the saga is marked STUCK for a later retry or a human, never compensated.</li>
 * </ul>
 * Calls are at-least-once: after a crash, the step in progress is called again on resume. That's
 * why every participant must be idempotent per (sagaId, step).
 */
public final class SagaOrchestrator<C> {

    /** Thrown by a {@link CrashPolicy} to simulate the orchestrator process dying. */
    public static final class Crash extends RuntimeException {
        public Crash() {
            super("orchestrator crashed", null, false, false);
        }
    }

    /** Decides whether to crash right after a participant call returned, before the log is written. */
    @FunctionalInterface
    public interface CrashPolicy {
        boolean crashAfterCall(String sagaId, String step);

        CrashPolicy NEVER = (id, step) -> false;
    }

    private final SagaDefinition<C> definition;
    private final SagaLog log;
    private final int maxAttempts;
    private final CrashPolicy crashPolicy;

    public SagaOrchestrator(SagaDefinition<C> definition, SagaLog log, int maxAttempts, CrashPolicy crashPolicy) {
        this.definition = definition;
        this.log = log;
        this.maxAttempts = maxAttempts;
        this.crashPolicy = crashPolicy;
    }

    public SagaStatus start(String sagaId, C context) {
        if (log.state(sagaId).isPresent()) {
            return resume(sagaId, context);                     // starting twice is resuming
        }
        log.write(sagaId, new SagaLog.State(SagaStatus.RUNNING, 0, -1, null), "started", -1);
        return resume(sagaId, context);
    }

    /** Continues a saga from whatever the log says, e.g. after a crash. */
    public SagaStatus resume(String sagaId, C context) {
        SagaLog.State state = log.state(sagaId).orElseThrow(() -> new IllegalArgumentException("Unknown saga " + sagaId));
        return switch (state.status()) {
            case RUNNING -> forward(sagaId, context, state.nextStep());
            case COMPENSATING -> backward(sagaId, context, state.compensateFrom(), state.failureReason());
            default -> state.status();
        };
    }

    /**
     * Retries a STUCK saga from where it stopped (a scheduled job or an operator does this once the
     * failing participant is healthy again): forward if it stopped going forward, backward if it
     * stopped while compensating.
     */
    public SagaStatus retryStuck(String sagaId, C context) {
        SagaLog.State state = log.state(sagaId).orElseThrow(() -> new IllegalArgumentException("Unknown saga " + sagaId));
        if (state.status() != SagaStatus.STUCK) {
            return state.status();
        }
        if (state.compensateFrom() >= 0) {
            log.write(sagaId, new SagaLog.State(SagaStatus.COMPENSATING, state.nextStep(), state.compensateFrom(),
                    state.failureReason()), "retry compensation", state.compensateFrom());
            return backward(sagaId, context, state.compensateFrom(), state.failureReason());
        }
        log.write(sagaId, new SagaLog.State(SagaStatus.RUNNING, state.nextStep(), -1, null), "retry", state.nextStep());
        return forward(sagaId, context, state.nextStep());
    }

    private SagaStatus forward(String sagaId, C context, int from) {
        for (int i = from; i < definition.steps().size(); i++) {
            SagaStep<C> step = definition.steps().get(i);
            Failure failure = attempt(sagaId, context, step, step.action());
            if (failure == null) {
                log.write(sagaId, new SagaLog.State(SagaStatus.RUNNING, i + 1, -1, null), "done " + step.name(), i);
                continue;
            }
            if (step.kind() == StepKind.RETRIABLE || (step.kind() == StepKind.PIVOT && !failure.rejected())) {
                // After the pivot we can't go back. And if the pivot's own outcome is unknown, going
                // back would be a guess: the kitchen may already be cooking. Stop and retry later.
                log.write(sagaId, new SagaLog.State(SagaStatus.STUCK, i, -1, failure.reason()), "stuck at " + step.name(), i);
                return SagaStatus.STUCK;
            }
            // A rejection means the step didn't happen: undo the ones before it. After exhausted
            // retries the outcome is UNKNOWN (the reply may have been lost): undo this step too;
            // compensations are safe when there is nothing to undo.
            int compensateFrom = failure.rejected() ? i - 1 : i;
            log.write(sagaId, new SagaLog.State(SagaStatus.COMPENSATING, i, compensateFrom, failure.reason()), "failed " + step.name(), i);
            return backward(sagaId, context, compensateFrom, failure.reason());
        }
        log.write(sagaId, new SagaLog.State(SagaStatus.COMPLETED, definition.steps().size(), -1, null), "completed", -1);
        return SagaStatus.COMPLETED;
    }

    private SagaStatus backward(String sagaId, C context, int from, String reason) {
        for (int i = from; i >= 0; i--) {
            SagaStep<C> step = definition.steps().get(i);
            if (step.compensation() == null) {
                continue;
            }
            Failure failure = attempt(sagaId, context, step, step.compensation());
            if (failure != null) {
                log.write(sagaId, new SagaLog.State(SagaStatus.STUCK, i, i, reason), "compensation stuck " + step.name(), i);
                return SagaStatus.STUCK;
            }
            log.write(sagaId, new SagaLog.State(SagaStatus.COMPENSATING, i, i - 1, reason), "compensated " + step.name(), i);
        }
        log.write(sagaId, new SagaLog.State(SagaStatus.COMPENSATED, 0, -1, reason), "compensated", -1);
        return SagaStatus.COMPENSATED;
    }

    private record Failure(String reason, boolean rejected) {
    }

    /** @return null on success */
    private Failure attempt(String sagaId, C context, SagaStep<C> step, SagaStep.Action<C> action) {
        String lastFailure = null;
        boolean compensation = action == step.compensation();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run(sagaId, context);
                if (crashPolicy.crashAfterCall(sagaId, step.name())) {
                    throw new Crash();
                }
                return null;
            } catch (BusinessRejection rejected) {
                if (compensation) {
                    throw new IllegalStateException("Compensations must not reject: " + step.name(), rejected);
                }
                return new Failure(rejected.getMessage(), true);
            } catch (Transient transientFailure) {
                lastFailure = transientFailure.getMessage();
            }
        }
        return new Failure(lastFailure, false);
    }
}
