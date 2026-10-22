package dev.foodfleet.ch26.tracking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Per-order position stream on this instance: numbers each update, keeps the last
 * {@value #REPLAY} for clients that reconnect with {@code Last-Event-ID}, and notifies listeners.
 * Across instances, the same updates arrive from a shared channel (Kafka, Redis pub/sub).
 */
@Component
public class TrackingHub {

    public record Update(long id, String orderId, String json) {
    }

    static final int REPLAY = 50;

    private final Map<String, Deque<Update>> recent = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Update>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Long> sequence = new ConcurrentHashMap<>();

    public Update publish(String orderId, String json) {
        long id = sequence.merge(orderId, 1L, Long::sum);
        Update update = new Update(id, orderId, json);
        Deque<Update> buffer = recent.computeIfAbsent(orderId, k -> new ArrayDeque<>());
        synchronized (buffer) {
            buffer.addLast(update);
            if (buffer.size() > REPLAY) {
                buffer.removeFirst();
            }
        }
        listeners.getOrDefault(orderId, List.of()).forEach(l -> l.accept(update));
        return update;
    }

    /** Updates after {@code lastId} still in the buffer (all of them if the client is new). */
    public List<Update> since(String orderId, long lastId) {
        Deque<Update> buffer = recent.get(orderId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            return buffer.stream().filter(u -> u.id() > lastId).toList();
        }
    }

    public Runnable subscribe(String orderId, Consumer<Update> listener) {
        List<Consumer<Update>> forOrder = listeners.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>());
        forOrder.add(listener);
        return () -> forOrder.remove(listener);
    }

    public int listenerCount(String orderId) {
        return listeners.getOrDefault(orderId, List.of()).size();
    }
}
