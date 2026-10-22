package dev.foodfleet.ch28.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CorrelationTest {

    @Test
    void parsesTheW3cExampleAndRejectsInvalidHeaders() {
        // The example from the W3C Trace Context specification
        TraceParent p = TraceParent.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertThat(p.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(p.parentId()).isEqualTo("00f067aa0ba902b7");
        assertThat(p.sampled()).isTrue();
        assertThat(TraceParent.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01")).isNull();
        assertThat(TraceParent.parse("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isNull();
        assertThat(TraceParent.parse("00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01")).isNull();   // lowercase only
    }

    @Test
    void aChildKeepsTheTraceAndChangesTheParent() {
        TraceParent root = TraceParent.newTrace(true);
        TraceParent child = root.child();

        assertThat(child.traceId()).isEqualTo(root.traceId());
        assertThat(child.parentId()).isNotEqualTo(root.parentId());
        assertThat(TraceParent.parse(child.header())).isEqualTo(child);
    }

    @Test
    void eventsCarryTheCorrelationAndTheirDirectCause() {
        Instant now = Instant.parse("2026-10-06T13:00:00Z");
        EventEnvelope placed = EventEnvelope.first("OrderPlaced", "req-7", TraceParent.newTrace(true), "order-service", now);
        EventEnvelope paid = placed.causes("PaymentCaptured", "payment-service", now);
        EventEnvelope accepted = paid.causes("KitchenAccepted", "kitchen-service", now);

        assertThat(accepted.correlationId()).isEqualTo("req-7");
        assertThat(accepted.causationId()).isEqualTo(paid.eventId());
        assertThat(paid.causationId()).isEqualTo(placed.eventId());
        assertThat(TraceParent.parse(accepted.traceparent()).traceId())
                .isEqualTo(TraceParent.parse(placed.traceparent()).traceId());
    }

    @Test
    void timestampsMisorderCausesAndEffectsCausationIdsDoNot() {
        var synced = CausationVsClocks.run(2_000, 0, 28);
        var drifting = CausationVsClocks.run(2_000, -250, 28);

        assertThat(synced.linksInvertedByTimestamp()).isGreaterThan(0);
        assertThat(drifting.ordersMisordered()).isGreaterThan(4 * synced.ordersMisordered());
        assertThat(synced.eventsFoundByOrderId()).isLessThan(synced.events());
    }
}
