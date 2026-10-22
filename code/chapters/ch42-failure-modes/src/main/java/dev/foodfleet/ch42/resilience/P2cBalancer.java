package dev.foodfleet.ch42.resilience;

import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.random.RandomGenerator;

/**
 * "Power of two choices": pick two instances at random and send the request to the one with fewer requests in
 * flight. A slow instance accumulates outstanding requests, so it is chosen less, without health checks or latency
 * statistics; round robin keeps sending it its full share.
 */
public final class P2cBalancer<T> {

    private final List<T> instances;
    private final AtomicIntegerArray inFlight;

    public P2cBalancer(List<T> instances) {
        this.instances = List.copyOf(instances);
        this.inFlight = new AtomicIntegerArray(instances.size());
    }

    /** Chooses an instance and counts the request as in flight; call {@link #done} when it completes. */
    public int acquire(RandomGenerator random) {
        int n = instances.size();
        int a = random.nextInt(n);
        int b = n == 1 ? a : (a + 1 + random.nextInt(n - 1)) % n;
        int pick = inFlight.get(a) <= inFlight.get(b) ? a : b;
        inFlight.incrementAndGet(pick);
        return pick;
    }

    public void done(int index) {
        inFlight.decrementAndGet(index);
    }

    public T instance(int index) {
        return instances.get(index);
    }

    public int inFlight(int index) {
        return inFlight.get(index);
    }
}
