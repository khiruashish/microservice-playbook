package dev.foodfleet.ch14.placeorder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Faults by script: "payment.authorize" → REJECT, then NONE for every later call. */
public final class ScriptedFaults implements Faults {

    private final Map<String, Deque<Fault>> script = new HashMap<>();

    public ScriptedFaults on(String participantDotOperation, Fault... faults) {
        script.computeIfAbsent(participantDotOperation, k -> new ArrayDeque<>()).addAll(java.util.List.of(faults));
        return this;
    }

    @Override
    public Fault next(String participant, String operation, String sagaId) {
        Deque<Fault> queue = script.get(participant + "." + operation);
        return queue == null || queue.isEmpty() ? Fault.NONE : queue.poll();
    }
}
