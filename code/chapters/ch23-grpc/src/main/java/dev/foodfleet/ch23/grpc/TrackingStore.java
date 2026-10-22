package dev.foodfleet.ch23.grpc;

import dev.foodfleet.ch23.grpc.proto.GeoPoint;
import dev.foodfleet.ch23.grpc.proto.OrderStatus;
import dev.foodfleet.ch23.grpc.proto.TrackingEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Current status per order, and listeners for changes (in production: fed by Kafka events). */
@Component
public class TrackingStore {

    public static final class OrderNotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OrderNotFound(String orderId) {
            super("no order " + orderId);
        }
    }

    /** Returned by {@link #subscribe}; closing it stops the events. */
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private final Map<String, OrderStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<TrackingEvent>>> listeners = new ConcurrentHashMap<>();

    public OrderStatus status(String orderId) {
        return Optional.ofNullable(statuses.get(orderId)).orElseThrow(() -> new OrderNotFound(orderId));
    }

    public void update(OrderStatus status) {
        statuses.put(status.getOrderId(), status);
        publish(TrackingEvent.newBuilder().setOrderId(status.getOrderId()).setStatus(status)
                .setAt(status.getUpdatedAt()).build());
    }

    public void riderMoved(String orderId, GeoPoint location, com.google.protobuf.Timestamp at) {
        publish(TrackingEvent.newBuilder().setOrderId(orderId).setRiderLocation(location).setAt(at).build());
    }

    public Subscription subscribe(String orderId, Consumer<TrackingEvent> listener) {
        List<Consumer<TrackingEvent>> forOrder = listeners.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>());
        forOrder.add(listener);
        return () -> forOrder.remove(listener);
    }

    public int listenerCount(String orderId) {
        return listeners.getOrDefault(orderId, List.of()).size();
    }

    private void publish(TrackingEvent event) {
        listeners.getOrDefault(event.getOrderId(), List.of()).forEach(l -> l.accept(event));
    }
}
