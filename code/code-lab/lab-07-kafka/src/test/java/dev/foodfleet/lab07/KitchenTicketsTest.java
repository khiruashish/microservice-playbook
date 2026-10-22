package dev.foodfleet.lab07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real Kafka (KRaft) and real PostgreSQL: delivery, redelivery, and a poison message. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "foodfleet.kafka.retry.max-elapsed=500ms")
class KitchenTicketsTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.13");

    @BeforeAll
    static void topics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            // auto-creation is off in production; the DLT needs at least the source topic's partitions
            admin.createTopics(List.of(new NewTopic(OrderEvents.TOPIC, 3, (short) 1),
                    new NewTopic(OrderEvents.TOPIC + "-dlt", 3, (short) 1))).all().get();
        }
    }

    @Autowired
    OrderEvents events;

    @Autowired
    KitchenTickets tickets;

    @Test
    void anOrderBecomesATicket() throws Exception {
        String orderId = "o-" + UUID.randomUUID();
        events.placed(new OrderPlaced(orderId, "r-1", List.of("m-1", "m-3"))).get();
        await().atMost(Duration.ofSeconds(15)).until(() -> tickets.ticketsFor(orderId) == 1);
    }

    @Test
    void aRedeliveredEventMakesOneTicket() throws Exception {
        String orderId = "o-" + UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"orderId\":\"" + orderId + "\",\"restaurantId\":\"r-1\",\"itemIds\":[\"m-1\"]}";
        events.send(eventId, orderId, payload).get();
        events.send(eventId, orderId, payload).get();              // the same event, delivered again
        String marker = "o-" + UUID.randomUUID();                   // a later event on the same key, to know both were read
        events.placed(new OrderPlaced(marker, "r-1", List.of("m-1"))).get();
        await().atMost(Duration.ofSeconds(15)).until(() -> tickets.ticketsFor(marker) == 1);
        assertThat(tickets.ticketsFor(orderId)).isEqualTo(1);
    }

    @Test
    void aPoisonMessageGoesToTheDltAndTheConsumerMovesOn() throws Exception {
        String orderId = "o-" + UUID.randomUUID();
        events.send(UUID.randomUUID().toString(), orderId, "{not json").get();
        events.placed(new OrderPlaced(orderId, "r-1", List.of("m-2"))).get();  // same key: must not be blocked
        await().atMost(Duration.ofSeconds(15)).until(() -> tickets.ticketsFor(orderId) == 1);

        try (KafkaConsumer<String, String> dlt = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            dlt.subscribe(List.of(OrderEvents.TOPIC + "-dlt"));
            boolean found = false;
            long deadline = System.currentTimeMillis() + 15_000;
            while (!found && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = dlt.poll(Duration.ofMillis(500));
                for (var r : records) {
                    found |= orderId.equals(r.key()) && r.headers().lastHeader("kafka_dlt-exception-fqcn") != null;
                }
            }
            assertThat(found).as("the poison message, with its exception, on the DLT").isTrue();
        }
    }
}
