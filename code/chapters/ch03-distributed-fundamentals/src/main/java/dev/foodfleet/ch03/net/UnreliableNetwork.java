package dev.foodfleet.ch03.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * A deterministic model of the network the "8 fallacies" warn about. Each message can be dropped,
 * duplicated or delayed by a random number of ticks, and delays cause reordering. A seed makes every
 * run repeatable, so a test that demonstrates a failure demonstrates it every time.
 *
 * <p>Time is logical: call {@link #advanceTo(long)} to receive everything due by that tick.
 * Not thread-safe; it models one link, driven by one test.
 *
 * @param <T> message payload type
 */
public final class UnreliableNetwork<T> {

    private final Random random;
    private final double dropProbability;
    private final double duplicateProbability;
    private final int maxDelayTicks;
    private final PriorityQueue<Envelope<T>> inFlight =
            new PriorityQueue<>(Comparator.comparingLong(Envelope<T>::deliverAt).thenComparingLong(Envelope::sequence));

    private long now;
    private long sequence;
    private long sent;
    private long dropped;
    private long duplicated;

    private UnreliableNetwork(long seed, double dropProbability, double duplicateProbability, int maxDelayTicks) {
        if (dropProbability < 0 || dropProbability >= 1 || duplicateProbability < 0 || duplicateProbability >= 1) {
            throw new IllegalArgumentException("probabilities must be in [0, 1)");
        }
        if (maxDelayTicks < 0) {
            throw new IllegalArgumentException("maxDelayTicks must be >= 0");
        }
        this.random = new Random(seed);
        this.dropProbability = dropProbability;
        this.duplicateProbability = duplicateProbability;
        this.maxDelayTicks = maxDelayTicks;
    }

    /** A perfect network: every message delivered once, in order, on the next tick. Fallacy #1. */
    public static <T> UnreliableNetwork<T> reliable() {
        return new UnreliableNetwork<>(0, 0, 0, 0);
    }

    public static <T> UnreliableNetwork<T> of(long seed, double dropProbability, double duplicateProbability,
                                              int maxDelayTicks) {
        return new UnreliableNetwork<>(seed, dropProbability, duplicateProbability, maxDelayTicks);
    }

    public void send(String messageId, T payload) {
        sent++;
        if (random.nextDouble() < dropProbability) {
            dropped++;
            return;
        }
        enqueue(messageId, payload);
        if (random.nextDouble() < duplicateProbability) {
            duplicated++;
            enqueue(messageId, payload); // e.g. a retry after a lost ACK: same id, sent twice
        }
    }

    /** Advances logical time and returns every message due by {@code tick}, in arrival order. */
    public List<Envelope<T>> advanceTo(long tick) {
        if (tick < now) {
            throw new IllegalArgumentException("time only moves forward");
        }
        now = tick;
        List<Envelope<T>> delivered = new ArrayList<>();
        while (!inFlight.isEmpty() && inFlight.peek().deliverAt() <= now) {
            delivered.add(inFlight.poll());
        }
        return delivered;
    }

    /** Delivers everything still in flight. */
    public List<Envelope<T>> drain() {
        return advanceTo(now + maxDelayTicks + 1);
    }

    public long sent() {
        return sent;
    }

    public long dropped() {
        return dropped;
    }

    public long duplicated() {
        return duplicated;
    }

    private void enqueue(String messageId, T payload) {
        long delay = 1 + (maxDelayTicks == 0 ? 0 : random.nextInt(maxDelayTicks + 1));
        inFlight.add(new Envelope<>(messageId, payload, now + delay, sequence++));
    }

    /**
     * @param sequence send order, used to keep delivery deterministic when two messages are due on the same tick
     */
    public record Envelope<T>(String messageId, T payload, long deliverAt, long sequence) {
    }
}
