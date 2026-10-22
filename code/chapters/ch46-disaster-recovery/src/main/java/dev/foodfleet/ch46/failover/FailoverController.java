package dev.foodfleet.ch46.failover;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Watches a primary and promotes its standby when the primary has failed {@code threshold} health checks in a row,
 * {@code interval} apart. Before promoting it fences the old primary (stops it accepting writes: shut it down,
 * revoke its storage, remove it from the load balancer), because two primaries accepting writes (split brain) loses
 * more data than a slower failover. Detection time is about interval × threshold; that's the dial this class exposes.
 */
public final class FailoverController {

    /** A health check that answers within its own timeout. */
    @FunctionalInterface
    public interface Probe {
        boolean healthy();
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    /** Nanosecond timestamps of each step; 0 means the step didn't happen. */
    public record Timeline(long firstFailure, long declaredDead, long fenced, long promoted) {

        public Duration detection() {
            return Duration.ofNanos(declaredDead - firstFailure);
        }

        public Duration promotion() {
            return Duration.ofNanos(promoted - declaredDead);
        }
    }

    private final Probe primary;
    private final Runnable fence;
    private final Runnable promote;
    private final Duration interval;
    private final int threshold;
    private final Sleeper sleeper;
    private final LongSupplier clock;

    public FailoverController(Probe primary, Runnable fence, Runnable promote, Duration interval, int threshold) {
        this(primary, fence, promote, interval, threshold, d -> Thread.sleep(d), System::nanoTime);
    }

    FailoverController(Probe primary, Runnable fence, Runnable promote, Duration interval, int threshold,
                       Sleeper sleeper, LongSupplier clock) {
        this.primary = Objects.requireNonNull(primary);
        this.fence = Objects.requireNonNull(fence);
        this.promote = Objects.requireNonNull(promote);
        this.interval = interval;
        this.threshold = threshold;
        this.sleeper = sleeper;
        this.clock = clock;
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least 1");
        }
    }

    /** Blocks until a failover has happened (or the thread is interrupted) and returns its timeline. */
    public Timeline watch() throws InterruptedException {
        int failures = 0;
        long firstFailure = 0;
        while (true) {
            sleeper.sleep(interval);
            if (primary.healthy()) {
                failures = 0;
                continue;
            }
            if (failures++ == 0) {
                firstFailure = clock.getAsLong();
            }
            if (failures >= threshold) {
                long declared = clock.getAsLong();
                fence.run();
                long fenced = clock.getAsLong();
                promote.run();
                return new Timeline(firstFailure, declared, fenced, clock.getAsLong());
            }
        }
    }
}
