package dev.foodfleet.ch49;

import dev.foodfleet.ch49.spans.PaymentSpans;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Without an SDK (no agent, no Boot tracing) the API is a no-op: code still runs and still propagates. */
class PaymentSpansTest {

    private final PaymentSpans spans = new PaymentSpans(TracerProvider.noop().get("test"));

    @Test
    void returnsTheDecisionAndMakesTheSpanCurrentWhileChecking() {
        boolean[] current = new boolean[1];
        PaymentSpans.Decision d = spans.fraudCheck("upi", () -> {
            current[0] = Span.current() != null;
            return new PaymentSpans.Decision("review", 12);
        });
        assertThat(d.outcome()).isEqualTo("review");
        assertThat(current[0]).isTrue();
    }

    @Test
    void failuresAreRethrownAfterBeingRecorded() {
        assertThatThrownBy(() -> spans.fraudCheck("card", () -> {
            throw new IllegalStateException("rules unavailable");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("rules unavailable");
    }

    @Test
    void attributeNamesFollowTheNamespacedConvention() {
        assertThat(PaymentSpans.DECISION.getKey()).isEqualTo("foodfleet.fraud.decision");
        assertThat(PaymentSpans.TENANT.getKey()).startsWith("foodfleet.");
    }
}
