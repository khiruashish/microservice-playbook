package dev.foodfleet.ch19.bus;

import dev.foodfleet.ch19.sim.EventLoop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * A topic keyed by item id, Kafka-like: each subscriber (consumer group) receives every message,
 * 20–80 ms after publication, in order per key, at least once (1% are delivered a second time,
 * as after a consumer rebalance). It counts messages and bytes.
 */
public final class EventBus<E> {

    private record Subscriber<E>(Consumer<E> handler, Map<String, Long> lastDeliveryPerKey) {
    }

    private final EventLoop loop;
    private final Random random;
    private final List<Subscriber<E>> subscribers = new ArrayList<>();
    private long messages;
    private long bytes;

    public EventBus(EventLoop loop, Random random) {
        this.loop = loop;
        this.random = random;
    }

    public void subscribe(Consumer<E> handler) {
        subscribers.add(new Subscriber<>(handler, new HashMap<>()));
    }

    public void publish(String key, E event, int sizeBytes) {
        messages++;
        bytes += sizeBytes;
        for (Subscriber<E> s : subscribers) {
            long at = Math.max(loop.now() + EventLoop.ms(20 + 60 * random.nextDouble()),
                    s.lastDeliveryPerKey().getOrDefault(key, 0L) + 1);           // per-key order
            s.lastDeliveryPerKey().put(key, at);
            loop.at(at, () -> s.handler().accept(event));
            if (random.nextDouble() < 0.01) {
                long again = at + EventLoop.ms(500 + 1_000 * random.nextDouble());
                loop.at(again, () -> s.handler().accept(event));                  // redelivered
            }
        }
    }

    public long messages() {
        return messages;
    }

    public long bytes() {
        return bytes;
    }
}
