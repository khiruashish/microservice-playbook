package dev.foodfleet.ch50;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.LoggingEvent;
import dev.foodfleet.ch50.masking.MaskingEncoder;
import dev.foodfleet.ch50.masking.Pii;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingEncoderTest {

    private static String encode(MaskingEncoder encoder, Logger logger, String message, Object... args) {
        LoggingEvent e = new LoggingEvent(MaskingEncoderTest.class.getName(), logger, Level.INFO, message, null, args);
        e.setMDCPropertyMap(Map.of("customer.phone", "9876543210"));
        return new String(encoder.encode(e), StandardCharsets.UTF_8);
    }

    @Test
    void masksEveryFieldOfTheJsonLine() {
        LoggerContext ctx = new LoggerContext();
        JsonEncoder json = new JsonEncoder();
        json.setContext(ctx);
        MaskingEncoder masking = new MaskingEncoder();
        masking.setContext(ctx);
        masking.setDelegate(json);
        masking.start();
        String line = encode(masking, ctx.getLogger("order-service"), "receipt for asha.rao@example.in, card {}",
                "4111111111111111");
        assertThat(line.contains("asha.rao@") || line.contains("4111111111111111") || line.contains("9876543210")).isFalse();
        assertThat(line).contains("a***@example.in").contains("**** **** **** 1111").contains("******3210");
    }

    @Test
    void piiArgumentsAreMaskedBeforeTheEncoderSeesThem() {
        LoggerContext ctx = new LoggerContext();
        JsonEncoder json = new JsonEncoder();
        json.setContext(ctx);
        json.start();
        LoggingEvent e = new LoggingEvent(MaskingEncoderTest.class.getName(), ctx.getLogger("x"), Level.INFO,
                "receipt sent to {}", null, new Object[] {Pii.email("asha.rao@example.in")});
        e.setMDCPropertyMap(Map.of());
        String line = new String(json.encode(e), StandardCharsets.UTF_8);
        assertThat(line.contains("asha.rao@")).isFalse();
        assertThat(line).contains("a***@example.in");
    }
}
