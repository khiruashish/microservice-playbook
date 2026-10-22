package dev.foodfleet.ch15.orders;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The events ordering-service publishes. Payloads are tiny "k=v;k=v" strings to keep the simulation
 * free of a JSON library; the Spring Boot version in {@code app/} uses JSON.
 */
public final class OrderEvents {

    public static final String PLACED = "OrderPlaced";
    public static final String CANCELLED = "OrderCancelled";

    private OrderEvents() {
    }

    public static String placedPayload(OrderRow order) {
        return "order=" + order.id() + ";customer=" + order.customerId() + ";points=" + order.points();
    }

    public static String cancelledPayload(OrderRow order) {
        return placedPayload(order);
    }

    public static Map<String, String> parse(String payload) {
        return Stream.of(payload.split(";"))
                .map(kv -> kv.split("=", 2))
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
    }

    /** Message ids are derived from the business event, so a retry of the same event has the same id. */
    public static String messageId(String orderId, String type) {
        return orderId + ":" + type;
    }
}
