package dev.foodfleet.ch14.choreography;

import dev.foodfleet.ch14.saga.SagaFailure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A synchronous, in-memory stand-in for a broker: topics, subscribers, redelivery on transient
 * failures. Every published event is recorded, so a test can print the flow that no single
 * service's code shows.
 */
public final class EventBus {

    public record Event(String type, String sagaId) {
    }

    private final Map<String, List<Consumer<Event>>> subscribers = new LinkedHashMap<>();
    private final Deque<Event> queue = new ArrayDeque<>();
    private final List<String> trace = new ArrayList<>();
    private final int maxDeliveries;
    private boolean draining;

    public EventBus(int maxDeliveries) {
        this.maxDeliveries = maxDeliveries;
    }

    public void subscribe(String type, Consumer<Event> handler) {
        subscribers.computeIfAbsent(type, t -> new ArrayList<>()).add(handler);
    }

    public void publish(String type, String sagaId) {
        queue.add(new Event(type, sagaId));
        if (!draining) {
            drain();
        }
    }

    private void drain() {
        draining = true;
        try {
            while (!queue.isEmpty()) {
                Event e = queue.poll();
                trace.add(e.type());
                for (Consumer<Event> handler : subscribers.getOrDefault(e.type(), List.of())) {
                    deliver(handler, e);
                }
            }
        } finally {
            draining = false;
        }
    }

    private void deliver(Consumer<Event> handler, Event e) {
        for (int attempt = 1; ; attempt++) {
            try {
                handler.accept(e);
                return;
            } catch (SagaFailure.Transient t) {
                if (attempt == maxDeliveries) {
                    trace.add("DEAD-LETTER(" + e.type() + ")");     // a human or a replay tool takes over
                    return;
                }
            }
        }
    }

    public List<String> trace() {
        return List.copyOf(trace);
    }
}
