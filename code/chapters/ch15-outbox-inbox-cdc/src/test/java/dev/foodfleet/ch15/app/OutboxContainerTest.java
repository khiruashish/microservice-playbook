package dev.foodfleet.ch15.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch15.app.inbox.LoyaltyPointsHandler;
import dev.foodfleet.ch15.app.order.PlaceOrderService;
import dev.foodfleet.ch15.app.outbox.Outbox;
import dev.foodfleet.ch15.app.outbox.OutboxMessage;
import dev.foodfleet.ch15.app.outbox.OutboxRelay;
import dev.foodfleet.ch15.app.outbox.RecordingMessagePublisher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The outbox on real PostgreSQL: atomicity with the business write, the relay's ordering and
 * SKIP LOCKED behaviour, and the inbox. The scheduler is slowed down so tests drive the relay.
 * Skipped automatically when Docker isn't available.
 */
@SpringBootTest(properties = "foodfleet.outbox.poll-interval-ms=3600000")
@Testcontainers(disabledWithoutDocker = true)
class OutboxContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired PlaceOrderService orders;
    @Autowired Outbox outbox;
    @Autowired OutboxRelay relay;
    @Autowired RecordingMessagePublisher publisher;
    @Autowired LoyaltyPointsHandler loyalty;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void clean() {
        jdbc.sql("truncate orders, outbox, inbox, loyalty_points").update();
        publisher.clear();
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    @Test
    void placingAnOrderWritesTheOrderAndItsEventTogether() {
        orders.place("o-1", "c-1", 45_000);

        assertThat(count("orders")).isEqualTo(1);
        assertThat(jdbc.sql("select type from outbox").query(String.class).list()).containsExactly("OrderPlaced");
    }

    @Test
    void ifTheTransactionRollsBackTheEventIsGoneToo() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            orders.place("o-1", "c-1", 45_000);
            throw new IllegalStateException("payment pre-check failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("orders")).isZero();
        assertThat(count("outbox")).isZero();
    }

    @Test
    void appendingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> outbox.append("order", "o-1", "OrderPlaced", "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void theRelayPublishesInOrderAndMarksRows() {
        orders.place("o-1", "c-1", 45_000);
        orders.place("o-2", "c-2", 30_000);

        assertThat(relay.publishBatch()).isEqualTo(2);
        assertThat(publisher.published()).extracting(OutboxMessage::aggregateId).containsExactly("o-1", "o-2");
        assertThat(publisher.published()).extracting(OutboxMessage::topic)
                .containsOnly("foodfleet.order.events");
        assertThat(relay.publishBatch()).isZero();          // nothing left
    }

    @Test
    void aSecondRelaySkipsRowsTheFirstHasLocked() throws Exception {
        orders.place("o-1", "c-1", 45_000);
        orders.place("o-2", "c-2", 30_000);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // Another relay instance holds a lock on o-1's row...
        CompletableFuture<Void> other = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(s -> {
            jdbc.sql("select id from outbox where aggregate_id = 'o-1' for update").query().listOfRows();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        // ...so this one publishes o-2 without waiting for it.
        assertThat(relay.publishBatch()).isEqualTo(1);
        assertThat(publisher.published()).extracting(OutboxMessage::aggregateId).containsExactly("o-2");

        release.countDown();
        other.get(10, TimeUnit.SECONDS);
        assertThat(relay.publishBatch()).isEqualTo(1);
    }

    @Test
    void theInboxAppliesAMessageOnlyOnce() {
        orders.place("o-1", "c-1", 45_000);
        relay.publishBatch();
        OutboxMessage message = publisher.published().getFirst();

        assertThat(loyalty.handle(message)).isTrue();
        assertThat(loyalty.handle(message)).isFalse();       // redelivered: skipped

        assertThat(jdbc.sql("select points from loyalty_points where customer_id = 'c-1'").query(Long.class).single())
                .isEqualTo(45L);
        assertThat(count("inbox")).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
