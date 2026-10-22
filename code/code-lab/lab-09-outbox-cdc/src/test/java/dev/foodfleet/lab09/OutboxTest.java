package dev.foodfleet.lab09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The outbox end to end on real PostgreSQL and Kafka. The scheduler is slowed down so the tests drive the relay. */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "foodfleet.outbox.poll-interval=1h")
class OutboxTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.13");

    @BeforeAll
    static void topic() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("foodfleet.ordering.order-events", 3, (short) 1))).all().get();
        }
    }

    @Autowired
    PlaceOrder orders;

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcClient db;

    @Test
    void theOrderAndItsEventCommitTogether() {
        String id = orders.place("customer-asha", 64700);
        assertThat(db.sql("SELECT count(*) FROM outbox WHERE aggregate_id = ?").param(id).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aFailedOrderLeavesNoEvent() {
        long before = db.sql("SELECT count(*) FROM outbox").query(Long.class).single();
        assertThatThrownBy(() -> orders.place("customer-asha", 0));          // the CHECK constraint refuses it
        assertThat(db.sql("SELECT count(*) FROM outbox").query(Long.class).single()).isEqualTo(before);
    }

    @Test
    void theRelayPublishesInOrderWithKeyAndEventId() {
        List<String> placed = List.of(orders.place("c-1", 100), orders.place("c-2", 200), orders.place("c-3", 300));
        while (relay.publishBatch() > 0) {
            // drain
        }
        assertThat(db.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Integer.class).single()).isZero();

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("foodfleet.ordering.order-events"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && received.stream().map(ConsumerRecord::key).filter(placed::contains).count() < 3) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
        }
        List<ConsumerRecord<String, String>> ours = received.stream().filter(r -> placed.contains(r.key())).toList();
        assertThat(ours).hasSize(3);
        assertThat(ours).allSatisfy(r -> {
            assertThat(r.headers().lastHeader("event-id")).isNotNull();
            assertThat(new String(r.headers().lastHeader("event-type").value(), StandardCharsets.UTF_8)).isEqualTo("OrderPlaced");
            assertThat(r.value()).contains("orderId").contains(r.key());   // jsonb re-renders the JSON: "a": "b"
        });
    }
}
