package dev.foodfleet.ch14.saga;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The orchestrator's durable memory. Every transition is written <b>before</b> the orchestrator
 * moves on, so a crashed orchestrator can be replaced by another that reads the log and resumes.
 * (In production: a table in the orchestrator's database, written in the same transaction as the
 * outbox message that calls the next participant, chapter 15.)
 */
public final class SagaLog {

    public record Entry(String sagaId, String what, int step) {
    }

    public record State(SagaStatus status, int nextStep, int compensateFrom, String failureReason) {
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, List<Entry>> history = new ConcurrentHashMap<>();

    public void write(String sagaId, State state, String what, int step) {
        states.put(sagaId, state);
        history.computeIfAbsent(sagaId, id -> new ArrayList<>()).add(new Entry(sagaId, what, step));
    }

    public Optional<State> state(String sagaId) {
        return Optional.ofNullable(states.get(sagaId));
    }

    public List<Entry> history(String sagaId) {
        return List.copyOf(history.getOrDefault(sagaId, List.of()));
    }

    public List<String> unfinished() {
        return states.entrySet().stream()
                .filter(e -> e.getValue().status() == SagaStatus.RUNNING || e.getValue().status() == SagaStatus.COMPENSATING)
                .map(Map.Entry::getKey).sorted().toList();
    }
}
