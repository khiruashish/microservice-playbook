package dev.foodfleet.ch47.events;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WideEventTest {

    private final Clock clock = Clock.fixed(Instant.parse("2025-09-15T12:30:00Z"), ZoneOffset.UTC);

    @Test
    void oneFlatJsonObjectWithEveryFieldInOrder() {
        WideEvent.Record r = WideEvent.start("payment-service", "POST /payments", clock)
                .with("service.version", "1.5").with("tenant", "chain-7").with("payment.method", "UPI")
                .with("amount_paise", 45_900).with("cache_hit", false)
                .finish("ok", 612.34);

        assertThat(r.toJson()).isEqualTo("{\"timestamp\":\"2025-09-15T12:30:00Z\",\"service\":\"payment-service\","
                + "\"operation\":\"POST /payments\",\"service.version\":\"1.5\",\"tenant\":\"chain-7\","
                + "\"payment.method\":\"UPI\",\"amount_paise\":45900,\"cache_hit\":false,\"outcome\":\"ok\",\"duration_ms\":612.3}");
        assertThat(r.durationMs()).isEqualTo(612.3);
    }

    @Test
    void missingValuesAreLeftOutAndCountsAccumulate() {
        WideEvent.Record r = WideEvent.start("order-service", "POST /orders", clock)
                .with("payment.bank", null).count("retries", 1).count("retries", 2)
                .finish("ok", 1);

        assertThat(r.fields()).containsKeys("retries");
        assertThat(r.toJson()).contains("\"retries\":3");
        assertThat(r.toJson().contains("payment.bank")).isFalse();
    }

    @Test
    void valuesAreEscapedSoOneEventStaysOneLine() {
        WideEvent.Record r = WideEvent.start("order-service", "POST /orders", clock)
                .with("note", "line one\nline \"two\"\t\\ end").finish("ok", 1);

        assertThat(r.toJson()).contains("\"note\":\"line one\\nline \\\"two\\\"\\t\\\\ end\"");
        assertThat(r.toJson().contains("\n")).isFalse();
    }

    @Test
    void theWriterWritesOneLinePerEventAndCountsBytes() {
        StringWriter out = new StringWriter();
        try (WideEventWriter w = new WideEventWriter(out)) {
            w.write(WideEvent.start("a", "op", clock).finish("ok", 1));
            w.write(WideEvent.start("b", "op", clock).finish("error", 2));
            w.flush();
            assertThat(w.events()).isEqualTo(2);
            assertThat(w.bytes()).isEqualTo(out.toString().length());
        }
        assertThat(out.toString().split("\n").length).isEqualTo(2);
        assertThat(out.toString()).startsWith("{\"timestamp\"");
    }
}
