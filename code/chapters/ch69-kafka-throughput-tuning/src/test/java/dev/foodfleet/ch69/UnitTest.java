package dev.foodfleet.ch69;

import dev.foodfleet.ch69.settings.KafkaProfiles;
import dev.foodfleet.ch69.sink.Events;
import dev.foodfleet.ch69.sink.OrderSink;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    @Test
    void theThroughputProfileOnlyChangesBatchingAndCompression() {
        var defaults = KafkaProfiles.producerDefaults();
        var throughput = KafkaProfiles.producerThroughput();
        assertEquals(defaults.keySet(), throughput.keySet());
        assertEquals("all", throughput.get("acks"));
        assertEquals("true", throughput.get("enable.idempotence"));
        assertTrue(Integer.parseInt(throughput.get("linger.ms")) > Integer.parseInt(defaults.get("linger.ms")));
        assertTrue(Integer.parseInt(throughput.get("batch.size")) > Integer.parseInt(defaults.get("batch.size")));
    }

    @Test
    void profilesBecomeSpringBootProperties() {
        String props = KafkaProfiles.asSpringProperties("producer", KafkaProfiles.producerThroughput());
        assertTrue(props.contains("spring.kafka.producer.properties.linger.ms="));
        assertEquals(KafkaProfiles.producerThroughput().size(), props.lines().count());
    }

    @Test
    void eventsAreOrderPlacedJsonOfAboutThreeHundredBytes() {
        List<OrderSink.OrderPlaced> events = Events.orders(0, 1000);
        double avg = events.stream().mapToInt(e -> e.json().length()).average().orElse(0);
        assertTrue(avg > 250 && avg < 350, "average " + avg);
        assertTrue(events.get(0).json().startsWith("{\"type\":\"OrderPlaced\",\"orderId\":\"ord-00000000\""));
        assertEquals(events, Events.orders(0, 1000), "deterministic, so a replay is the same events");
    }
}
