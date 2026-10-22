package dev.foodfleet.ch26.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The rider positions for one order, published over time. Keeps every update (with the
 * {@link System#nanoTime()} it was published at) so transports can serve "everything since n", and
 * notifies subscribers and long-pollers.
 */
public final class PositionFeed {

    public record Update(int seq, String json, long publishedNanos) {
    }

    private final List<Update> updates = new ArrayList<>();
    private final List<Consumer<Update>> subscribers = new CopyOnWriteArrayList<>();

    public static String json(int seq) {
        double lat = 12.935192 + seq * 0.00031;
        double lng = 77.624481 + seq * 0.00027;
        return "{\"orderId\":\"o-9001\",\"seq\":%d,\"lat\":%.6f,\"lng\":%.6f,\"etaMin\":%d}"
                .formatted(seq, lat, lng, Math.max(1, 18 - seq / 3));
    }

    public void publish(int seq) {
        Update u = new Update(seq, json(seq), System.nanoTime());
        synchronized (this) {
            updates.add(u);
            notifyAll();
        }
        subscribers.forEach(s -> s.accept(u));
    }

    /** Updates with a sequence number above {@code seq}. */
    public synchronized List<Update> since(int seq) {
        return updates.stream().filter(u -> u.seq() > seq).toList();
    }

    public synchronized Update latest() {
        return updates.isEmpty() ? null : updates.getLast();
    }

    /** Waits up to {@code timeoutMs} for anything newer than {@code seq}. */
    public synchronized List<Update> awaitSince(int seq, long timeoutMs) throws InterruptedException {
        long until = System.currentTimeMillis() + timeoutMs;
        while (since(seq).isEmpty()) {
            long left = until - System.currentTimeMillis();
            if (left <= 0) {
                return List.of();
            }
            wait(left);
        }
        return since(seq);
    }

    public Runnable subscribe(Consumer<Update> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public Update get(int seq) {
        synchronized (this) {
            return updates.stream().filter(u -> u.seq() == seq).findFirst().orElse(null);
        }
    }
}
