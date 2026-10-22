package dev.foodfleet.ch69.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FoodFleet's Kafka client settings, by purpose, as the Java client's property names. The values are the ones chapter
 * 69 measured; {@code verify/check_apis.sh} checks every name and default against Kafka 4.2.2's sources.
 * Spring Boot takes them as {@code spring.kafka.producer.properties.*} and {@code spring.kafka.consumer.properties.*}.
 */
public final class KafkaProfiles {

    private KafkaProfiles() {
    }

    /** What a Kafka 4.x producer does with no settings: kept here to compare against. */
    public static Map<String, String> producerDefaults() {
        return ordered("acks", "all", "enable.idempotence", "true", "linger.ms", "5", "batch.size", "16384", "compression.type", "none",
                "max.in.flight.requests.per.connection", "5");
    }

    /** Event streams where throughput matters more than a few milliseconds: order events, tracking pings, audit. */
    public static Map<String, String> producerThroughput() {
        return ordered("acks", "all", "enable.idempotence", "true", "linger.ms", "20", "batch.size", "131072", "compression.type", "lz4",
                "max.in.flight.requests.per.connection", "5");
    }

    /** Requests waiting on the answer (a command whose reply someone is waiting for): send at once. */
    public static Map<String, String> producerLatency() {
        return ordered("acks", "all", "enable.idempotence", "true", "linger.ms", "0", "batch.size", "16384", "compression.type", "none",
                "max.in.flight.requests.per.connection", "5");
    }

    /**
     * A consumer that writes each poll to a database in one transaction (see {@code OrderSink}) and commits offsets
     * only after the write: at-least-once, made safe by the sink's idempotent insert.
     */
    public static Map<String, String> consumerBatchSink() {
        return ordered("max.poll.records", "500", "enable.auto.commit", "false");
    }

    /** The same settings as Spring Boot properties, one per line. */
    public static String asSpringProperties(String side, Map<String, String> settings) {
        return settings.entrySet().stream().map(e -> "spring.kafka." + side + ".properties." + e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
