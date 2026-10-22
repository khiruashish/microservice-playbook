package dev.foodfleet.ch45.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A chaos experiment in the shape of the Principles of Chaos Engineering: confirm the steady state, inject a fault,
 * watch the steady state window by window, roll back, confirm recovery. The guard aborts the experiment (rolls the
 * fault back early) when the hypothesis fails for {@code abortAfter} consecutive windows: an experiment that is
 * hurting users has already answered its question. The rollback always runs, whatever happens.
 */
public final class Experiment {

    public enum Verdict {
        /** The steady state held throughout: the system tolerates this fault. */
        HELD,
        /** The steady state broke (guard off): the weakness is confirmed. */
        BROKEN,
        /** The guard stopped the experiment: the weakness is confirmed, at limited cost. */
        ABORTED,
        /** The system wasn't in its steady state to begin with: nothing was injected. */
        NOT_RUN
    }

    /** One run's outcome; times are in windows (FoodFleet: 1 s each). */
    public record Report(String name, Verdict verdict, List<Window> during, int failedWindows, int windowsToAbort,
                         int windowsToRecover, boolean recovered) {
    }

    private final String name;
    private final Hypothesis hypothesis;
    private final Runnable inject;
    private final Runnable rollback;
    private final Supplier<Window> nextWindow;
    private final int baselineWindows;
    private final int durationWindows;
    private final int abortAfter;
    private final int recoveryWindows;

    private Experiment(Builder b) {
        this.name = Objects.requireNonNull(b.name);
        this.hypothesis = Objects.requireNonNull(b.hypothesis);
        this.inject = Objects.requireNonNull(b.inject);
        this.rollback = Objects.requireNonNull(b.rollback);
        this.nextWindow = Objects.requireNonNull(b.nextWindow);
        this.baselineWindows = b.baselineWindows;
        this.durationWindows = b.durationWindows;
        this.abortAfter = b.abortAfter;
        this.recoveryWindows = b.recoveryWindows;
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public Report run() {
        for (int i = 0; i < baselineWindows; i++) {
            if (!hypothesis.holds(nextWindow.get())) {
                return new Report(name, Verdict.NOT_RUN, List.of(), 0, -1, -1, true);
            }
        }
        List<Window> during = new ArrayList<>();
        int failed = 0;
        int consecutive = 0;
        int abortedAt = -1;
        try {
            inject.run();
            for (int i = 1; i <= durationWindows; i++) {
                Window w = nextWindow.get();
                during.add(w);
                if (hypothesis.holds(w)) {
                    consecutive = 0;
                } else {
                    failed++;
                    consecutive++;
                    if (abortAfter > 0 && consecutive >= abortAfter) {
                        abortedAt = i;
                        break;
                    }
                }
            }
        } finally {
            rollback.run();
        }
        int recoveredAfter = -1;
        int healthy = 0;
        for (int i = 1; i <= recoveryWindows; i++) {
            if (hypothesis.holds(nextWindow.get())) {
                if (++healthy == 2) {           // two good windows in a row: recovered
                    recoveredAfter = i - 1;
                    break;
                }
            } else {
                healthy = 0;
            }
        }
        Verdict verdict = abortedAt > 0 ? Verdict.ABORTED : failed > 0 ? Verdict.BROKEN : Verdict.HELD;
        return new Report(name, verdict, during, failed, abortedAt, recoveredAfter, recoveredAfter >= 0);
    }

    public static final class Builder {
        private final String name;
        private Hypothesis hypothesis;
        private Runnable inject;
        private Runnable rollback;
        private Supplier<Window> nextWindow;
        private int baselineWindows = 5;
        private int durationWindows = 30;
        private int abortAfter = 3;
        private int recoveryWindows = 30;

        private Builder(String name) {
            this.name = name;
        }

        public Builder hypothesis(Hypothesis h) {
            this.hypothesis = h;
            return this;
        }

        /** The fault, and how to undo it. */
        public Builder method(Runnable inject, Runnable rollback) {
            this.inject = inject;
            this.rollback = rollback;
            return this;
        }

        /** Blocks for one observation window and returns what users saw in it. */
        public Builder observe(Supplier<Window> nextWindow) {
            this.nextWindow = nextWindow;
            return this;
        }

        public Builder baseline(int windows) {
            this.baselineWindows = windows;
            return this;
        }

        public Builder duration(int windows) {
            this.durationWindows = windows;
            return this;
        }

        /** Abort after this many consecutive failing windows; 0 runs to the end whatever happens. */
        public Builder abortAfter(int windows) {
            this.abortAfter = windows;
            return this;
        }

        public Builder recoveryWithin(int windows) {
            this.recoveryWindows = windows;
            return this;
        }

        public Experiment build() {
            return new Experiment(this);
        }
    }
}
