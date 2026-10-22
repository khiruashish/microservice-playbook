package dev.foodfleet.ch14.placeorder;

import dev.foodfleet.ch14.saga.SagaFailure;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base for simulated participants. It applies injected faults and keeps two promises every real
 * saga participant must keep:
 * <ul>
 *   <li><b>idempotent actions</b>: a repeated (sagaId, operation) has no second effect;</li>
 *   <li><b>safe compensations</b>: undoing something that never happened, or was already undone,
 *       does nothing. The orchestrator can't always know whether an action took effect.</li>
 * </ul>
 * {@code idempotent=false} turns off the first promise to show what goes wrong.
 */
abstract class Participant {

    private final String name;
    private final Faults faults;
    private final boolean idempotent;
    private final Set<String> done = ConcurrentHashMap.newKeySet();
    private final Set<String> undone = ConcurrentHashMap.newKeySet();

    Participant(String name, Faults faults, boolean idempotent) {
        this.name = name;
        this.faults = faults;
        this.idempotent = idempotent;
    }

    protected void perform(String operation, String sagaId, Runnable effect) {
        String key = sagaId + "|" + operation;
        // An idempotent participant answers a repeated request with the ORIGINAL outcome: if the
        // first attempt succeeded (and only its reply was lost), the retry can't be "rejected".
        boolean repeat = idempotent && done.contains(key);
        Faults.Fault fault = injected(operation, sagaId, repeat);
        boolean first = done.add(key);
        if (!idempotent || first) {
            try {
                effect.run();
            } catch (RuntimeException e) {
                if (first) {
                    done.remove(key);
                }
                throw e;
            }
        }
        if (fault == Faults.Fault.LOST_REPLY) {
            throw new SagaFailure.Transient(name + " timed out after " + operation);
        }
    }

    /** Undoes {@code operation} for this saga only if it happened and hasn't been undone yet. */
    protected void compensate(String operation, String compensation, String sagaId, Runnable undo) {
        Faults.Fault fault = injected(compensation, sagaId, false);
        String key = sagaId + "|" + operation;
        if (done.contains(key) && undone.add(key)) {
            undo.run();
        }
        if (fault == Faults.Fault.LOST_REPLY) {
            throw new SagaFailure.Transient(name + " timed out after " + compensation);
        }
    }

    private Faults.Fault injected(String operation, String sagaId, boolean repeatOfSuccess) {
        Faults.Fault fault = faults.next(name, operation, sagaId);
        if (repeatOfSuccess && fault == Faults.Fault.REJECT) {
            fault = Faults.Fault.NONE;
        }
        switch (fault) {
            case REJECT -> throw new SagaFailure.BusinessRejection(name + " rejected " + operation);
            case TRANSIENT -> throw new SagaFailure.Transient(name + " unavailable for " + operation);
            default -> {
                return fault;
            }
        }
    }
}
