package dev.foodfleet.ch28.correlation;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata every FoodFleet event carries (as Kafka headers, or envelope fields):
 * <ul>
 *   <li>{@code eventId}: unique per event; consumers deduplicate on it;</li>
 *   <li>{@code correlationId}: the same for everything that happened because of one business
 *       request (here: the order's checkout), across services, hops and days;</li>
 *   <li>{@code causationId}: the id of the event or command that directly caused this one, so
 *       the chain can be rebuilt without trusting clocks;</li>
 *   <li>{@code traceparent}: the distributed trace, for latency analysis.</li>
 * </ul>
 */
public record EventEnvelope(String eventId, String type, String correlationId, String causationId, String traceparent,
                            String producer, Instant occurredAt) {

    /** The first event of a flow: it starts the correlation and is caused by the incoming request. */
    public static EventEnvelope first(String type, String requestId, TraceParent trace, String producer, Instant at) {
        String id = UUID.randomUUID().toString();
        return new EventEnvelope(id, type, requestId, requestId, trace.header(), producer, at);
    }

    /** An event caused by this one: same correlation, this event as the cause, a child span. */
    public EventEnvelope causes(String type, String producer, Instant at) {
        TraceParent parent = TraceParent.parse(traceparent);
        return new EventEnvelope(UUID.randomUUID().toString(), type, correlationId, eventId,
                parent == null ? null : parent.child().header(), producer, at);
    }
}
