package dev.foodfleet.ch11.simulation;

import java.util.random.RandomGenerator;

/**
 * One step between "an instance died" and "clients stopped sending it traffic". Each stage samples
 * how long it adds, given that the timers involved run independently of the failure (their phase
 * at the moment of failure is uniformly random).
 */
public sealed interface Stage {

    String name();

    double sampleSeconds(RandomGenerator random);

    /** A periodic job (cache refresh, registry fetch, eviction sweep): waits until its next tick. */
    record Periodic(String name, double intervalSeconds) implements Stage {
        @Override
        public double sampleSeconds(RandomGenerator random) {
            return intervalSeconds <= 0 ? 0 : random.nextDouble() * intervalSeconds;
        }
    }

    /** A deterministic delay: propagation time, or the remaining failed probes after the first. */
    record Fixed(String name, double seconds) implements Stage {
        @Override
        public double sampleSeconds(RandomGenerator random) {
            return seconds;
        }
    }

    /**
     * Heartbeat lease: the last renewal happened somewhere in the last heartbeat interval, and the
     * lease expires {@code effectiveLease} after it. Eureka's {@code Lease} adds the duration twice
     * (a long-standing quirk kept for compatibility, documented in its source), so its effective
     * lease is twice the configured one.
     */
    record LeaseExpiry(String name, double heartbeatSeconds, double leaseSeconds, boolean leaseCountedTwice)
            implements Stage {
        @Override
        public double sampleSeconds(RandomGenerator random) {
            double effectiveLease = leaseCountedTwice ? 2 * leaseSeconds : leaseSeconds;
            double sinceLastRenewal = random.nextDouble() * heartbeatSeconds;
            return Math.max(0, effectiveLease - sinceLastRenewal);
        }
    }
}
