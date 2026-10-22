package dev.foodfleet.ch25.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * The listeners and error handling against an embedded Kafka broker: blocking retries that keep
 * order, not-retryable events straight to the DLT, duplicates applied once, and the receipt
 * listener's dead-letter handler. Backoff is shortened to 50 ms for the test.
 */
@SpringBootTest(properties = "foodfleet.kafka.retry.initial-interval=50ms")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "order-events-dlt"})
class OrderEventsKafkaTest {

    /** Kitchen display that is down for the first {@code failures} calls. */
    static final class FlakyKitchen implements KitchenDisplay {
        final AtomicInteger failures = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final List<String> shown = new CopyOnWriteArrayList<>();

        @Override
        public void show(OrderEvent event) {
            calls.incrementAndGet();
            if (failures.getAndDecrement() > 0) {
                throw new KitchenDisplay.Unavailable("tablet API timed out");
            }
            shown.add(event.orderId() + ":" + event.type());
        }
    }

    @TestConfiguration
    static class Doubles {
        @Bean
        @Primary
        FlakyKitchen flakyKitchen() {
            return new FlakyKitchen();
        }
    }

    @Autowired
    KafkaTemplate<Object, Object> template;

    @Autowired
    FlakyKitchen kitchen;

    @Autowired
    ReceiptEmailListener receipts;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void aTemporaryFailureIsRetriedInPlaceAndOrderIsKept() {
        kitchen.failures.set(2);
        send(event("e-1", "o-1", OrderEvent.Type.PLACED, "r-11"));
        send(event("e-2", "o-1", OrderEvent.Type.ACCEPTED, "r-11"));

        await(() -> kitchen.shown.contains("o-1:ACCEPTED"));
        assertThat(kitchen.shown.subList(kitchen.shown.indexOf("o-1:PLACED"), kitchen.shown.size()))
                .startsWith("o-1:PLACED", "o-1:ACCEPTED");                     // never ACCEPTED first
    }

    @Test
    void anInvalidEventGoesToTheDltWithoutRetries() {
        int callsBefore = kitchen.calls.get();
        send(event("e-bad", "o-2", OrderEvent.Type.PLACED, null));           // no restaurant: can never work

        try (Consumer<String, String> dlt = dltConsumer()) {
            ConsumerRecord<String, String> record = null;
            long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (record == null && System.nanoTime() < until) {
                for (ConsumerRecord<String, String> r : KafkaTestUtils.getRecords(dlt, Duration.ofMillis(500))) {
                    if (r.value().contains("e-bad")) {
                        record = r;
                    }
                }
            }
            assertThat(record).isNotNull();
            assertThat(new String(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).value()))
                    .isEqualTo(InvalidEventException.class.getName());
        }
        assertThat(kitchen.calls.get()).isEqualTo(callsBefore);               // the partner was never called
    }

    @Test
    void aRedeliveredEventIsAppliedOnce() {
        OrderEvent picked = event("e-3", "o-3", OrderEvent.Type.PICKED_UP, "r-12");
        send(picked);
        send(picked);                                                         // e.g. producer retry or replay
        send(event("e-4", "o-3", OrderEvent.Type.DELIVERED, "r-12"));

        await(() -> kitchen.shown.contains("o-3:DELIVERED"));
        assertThat(kitchen.shown.stream().filter("o-3:PICKED_UP"::equals).count()).isEqualTo(1L);
    }

    @Test
    void anInvalidReceiptIsDeadLetteredImmediately() {
        send(event("e-5", null, OrderEvent.Type.DELIVERED, "r-13"));

        await(() -> receipts.deadLettered().contains("e-5"));
    }

    private void send(OrderEvent e) {
        template.send("order-events", e.orderId(), e).join();
    }

    private static OrderEvent event(String id, String orderId, OrderEvent.Type type, String restaurantId) {
        return new OrderEvent(id, orderId, type, restaurantId, Instant.parse("2026-10-06T13:00:00Z"));
    }

    private Consumer<String, String> dltConsumer() {
        var props = KafkaTestUtils.consumerProps(broker, "dlt-reader", false);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "order-events-dlt");
        return consumer;
    }

    private static void await(BooleanSupplier condition) {
        long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > until) {
                throw new AssertionError("condition not met within 15 s");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
