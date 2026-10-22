package dev.foodfleet.interview;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An orchestrated saga: steps run in order; if a step up to and including the pivot fails, the completed steps are
 * compensated in reverse; once the pivot has succeeded, the steps after it are retried, never compensated, and a step
 * that keeps failing leaves the saga STUCK for a retry job or a person. Every transition is appended to a log first, so a new coordinator can
 * resume a crashed saga from the log (chapter 14, ADR-014). Steps must be idempotent: after a crash, the step that was
 * in flight runs again.
 */
public final class SagaCoordinator {

    public enum Kind { COMPENSATABLE, PIVOT, RETRIABLE }

    public enum Status { RUNNING, COMPLETED, COMPENSATING, COMPENSATED, STUCK }

    public record Step(String name, Kind kind, Consumer<String> action, Consumer<String> compensation) {
        public static Step compensatable(String name, Consumer<String> action, Consumer<String> compensation) {
            return new Step(name, Kind.COMPENSATABLE, action, compensation);
        }

        public static Step pivot(String name, Consumer<String> action) {
            return new Step(name, Kind.PIVOT, action, null);
        }

        public static Step retriable(String name, Consumer<String> action) {
            return new Step(name, Kind.RETRIABLE, action, null);
        }
    }

    /** One entry per transition; a real log is a table written in the same transaction as the outgoing command. */
    public record Entry(String sagaId, String event, int step) {
    }

    private final List<Step> steps;
    private final List<Entry> log;
    private final int attemptsAfterPivot;

    public SagaCoordinator(List<Step> steps, List<Entry> sharedLog, int attemptsAfterPivot) {
        long pivots = steps.stream().filter(s -> s.kind() == Kind.PIVOT).count();
        int pivot = indexOfPivot(steps);
        if (pivots > 1 || steps.subList(0, Math.max(pivot, 0)).stream().anyMatch(s -> s.kind() != Kind.COMPENSATABLE)
                || pivot >= 0 && steps.subList(pivot + 1, steps.size()).stream().anyMatch(s -> s.kind() != Kind.RETRIABLE)) {
            throw new IllegalArgumentException("compensatable steps, at most one pivot, then retriable steps");
        }
        this.steps = List.copyOf(steps);
        this.log = sharedLog;
        this.attemptsAfterPivot = attemptsAfterPivot;
    }

    private static int indexOfPivot(List<Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).kind() == Kind.PIVOT) {
                return i;
            }
        }
        return -1;
    }

    /** Starts or resumes {@code sagaId} from wherever its log says it got to. */
    public Status run(String sagaId) {
        List<Entry> mine = new ArrayList<>(log.stream().filter(e -> e.sagaId().equals(sagaId)).toList());
        Status status = mine.isEmpty() ? Status.RUNNING : statusOf(mine.getLast());
        if (status == Status.COMPLETED || status == Status.COMPENSATED) {
            return status;                       // finished: running again is a no-op
        }
        int done = (int) mine.stream().filter(e -> e.event().equals("done")).count();
        if (status == Status.COMPENSATING) {
            return compensate(sagaId, done - 1 - (int) mine.stream().filter(e -> e.event().equals("compensated")).count());
        }
        for (int i = done; i < steps.size(); i++) {
            Step step = steps.get(i);
            append(sagaId, "started", i);
            boolean afterPivot = step.kind() == Kind.RETRIABLE;
            int attempts = afterPivot ? attemptsAfterPivot : 1;
            boolean ok = false;
            for (int a = 0; a < attempts && !ok; a++) {
                try {
                    step.action().accept(sagaId);
                    ok = true;
                } catch (RuntimeException e) {
                    // transient or business failure; before the pivot one failure is enough to turn back
                }
            }
            if (ok) {
                append(sagaId, "done", i);
            } else if (afterPivot) {
                append(sagaId, "stuck", i);
                return Status.STUCK;
            } else {
                append(sagaId, "compensating", i);
                return compensate(sagaId, i - 1);
            }
        }
        append(sagaId, "completed", steps.size());
        return Status.COMPLETED;
    }

    private Status compensate(String sagaId, int from) {
        for (int i = from; i >= 0; i--) {
            steps.get(i).compensation().accept(sagaId);   // compensations must always be possible, and idempotent
            append(sagaId, "compensated", i);
        }
        append(sagaId, "rolled-back", -1);
        return Status.COMPENSATED;
    }

    private static Status statusOf(Entry last) {
        return switch (last.event()) {
            case "completed" -> Status.COMPLETED;
            case "rolled-back" -> Status.COMPENSATED;
            case "compensating", "compensated" -> Status.COMPENSATING;
            case "stuck" -> Status.STUCK;
            default -> Status.RUNNING;
        };
    }

    private void append(String sagaId, String event, int step) {
        synchronized (log) {
            log.add(new Entry(sagaId, event, step));
        }
    }
}
