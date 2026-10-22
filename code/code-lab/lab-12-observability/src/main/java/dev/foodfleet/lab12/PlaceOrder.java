package dev.foodfleet.lab12;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

/**
 * Placing an order, observed. The HTTP server span, the RestClient call and the Kafka send are instrumented by Spring
 * already; this adds one observation for the business operation: a span named after it, and a timer
 * ({@code foodfleet.order.place}) with only bounded tags. The order id goes on the span, never on the metric.
 */
public class PlaceOrder {

    static final String OBSERVATION = "foodfleet.order.place";
    static final String TOPIC = "foodfleet.ordering.order-events";
    private static final Logger log = LoggerFactory.getLogger(PlaceOrder.class);

    public record Request(String orderId, String restaurantId, String paymentMethod) {
    }

    private final ObservationRegistry observations;
    private final RestClient menu;
    private final KafkaTemplate<String, String> kafka;

    public PlaceOrder(ObservationRegistry observations, RestClient menu, KafkaTemplate<String, String> kafka) {
        this.observations = observations;
        this.menu = menu;
        this.kafka = kafka;
    }

    public void place(Request request) {
        Observation.createNotStarted(OBSERVATION, observations)
                .contextualName("place order")
                .lowCardinalityKeyValue("payment.method", bounded(request.paymentMethod()))   // metric tag and span attribute
                .highCardinalityKeyValue("order.id", request.orderId())                     // span attribute only
                .highCardinalityKeyValue("restaurant.id", request.restaurantId())
                .observe(() -> {
                    menu.get().uri("/menus/{id}", request.restaurantId()).retrieve().toBodilessEntity();
                    kafka.send(TOPIC, request.orderId(), "{\"orderId\":\"" + request.orderId() + "\"}");
                    log.info("order placed");                     // traceId and spanId come from the MDC
                });
    }

    /** A tag's values must be a small, known set: anything else would create a time series per value. */
    static String bounded(String paymentMethod) {
        return switch (paymentMethod) {
            case "UPI", "CARD", "WALLET", "COD" -> paymentMethod;
            default -> "OTHER";
        };
    }
}
