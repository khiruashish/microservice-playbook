package dev.foodfleet.ch30;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch30.indoubt.InDoubtMonitor;
import dev.foodfleet.ch30.indoubt.InDoubtPolicy;
import dev.foodfleet.ch30.orders.OrderRelay;
import dev.foodfleet.ch30.orders.OrderService;
import dev.foodfleet.ch30.orders.OrderView;
import dev.foodfleet.ch30.orders.PlaceOrder;
import dev.foodfleet.ch30.payments.Decision;
import dev.foodfleet.ch30.payments.OrderPlaced;
import dev.foodfleet.ch30.payments.PaymentLedger;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Order placement and its failure cases against real PostgreSQL (prepared transactions enabled for the in-doubt
 * tests). Scheduled jobs are slowed down so each test drives them itself. Skipped without Docker.
 */
@SpringBootTest(properties = {"orders.relay.poll-interval=1h", "indoubt.check-interval=1h", "indoubt.grace=1s"})
@Testcontainers(disabledWithoutDocker = true)
class OrdersAndPaymentsContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withCommand("postgres", "-c", "max_prepared_transactions=10");

    @Autowired OrderService orders;
    @Autowired OrderRelay relay;
    @Autowired PaymentLedger ledger;
    @Autowired InDoubtMonitor monitor;
    @Autowired JdbcClient jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE orders.orders, orders.outbox, orders.tm_log, payments.debits, payments.wallets, payments.stock").update();
        jdbc.sql("INSERT INTO payments.wallets VALUES (1, 100000), (2, 10000)").update();
        jdbc.sql("INSERT INTO payments.stock VALUES (500, 50), (501, 1)").update();
    }

    long balance(long customer) {
        return jdbc.sql("SELECT balance_paise FROM payments.wallets WHERE customer_id = :c").param("c", customer)
                .query(Long.class).single();
    }

    @Test
    void anOrderIsAcceptedAtOnceAndConfirmedByTheRelay() {
        OrderView placed = orders.place(new PlaceOrder(1, 500, 25_000));
        assertThat(placed.status()).isEqualTo("PENDING");
        assertThat(balance(1)).isEqualTo(100_000L);                       // nothing charged yet

        assertThat(relay.relayOnce()).isEqualTo(1);

        assertThat(orders.find(placed.id()).orElseThrow().status()).isEqualTo("CONFIRMED");
        assertThat(balance(1)).isEqualTo(75_000L);
        assertThat(jdbc.sql("SELECT count(*) FROM orders.outbox").query(Long.class).single()).isZero();
    }

    @Test
    void oneOrderThatCannotBePaidRejectsOnlyThatOrder() {
        long ok = orders.place(new PlaceOrder(1, 500, 25_000)).id();
        long broke = orders.place(new PlaceOrder(2, 500, 25_000)).id();      // wallet 2 holds 10,000
        long last = orders.place(new PlaceOrder(1, 501, 25_000)).id();
        long soldOut = orders.place(new PlaceOrder(1, 501, 25_000)).id();    // one portion left of dish 501

        relay.relayOnce();

        assertThat(orders.find(ok).orElseThrow().status()).isEqualTo("CONFIRMED");
        assertThat(orders.find(broke).orElseThrow()).isEqualTo(new OrderView(broke, "REJECTED", "INSUFFICIENT_FUNDS"));
        assertThat(orders.find(last).orElseThrow().status()).isEqualTo("CONFIRMED");
        assertThat(orders.find(soldOut).orElseThrow()).isEqualTo(new OrderView(soldOut, "REJECTED", "SOLD_OUT"));
        assertThat(balance(1)).isEqualTo(50_000L);
        assertThat(balance(2)).isEqualTo(10_000L);
    }

    @Test
    void aRedeliveredBatchGetsTheSameDecisionsAndChargesNothingMore() {
        List<OrderPlaced> batch = List.of(new OrderPlaced(91, 1, 500, 25_000), new OrderPlaced(92, 2, 500, 25_000));

        List<Decision> first = ledger.apply(batch);
        List<Decision> again = ledger.apply(batch);

        assertThat(again).isEqualTo(first);
        assertThat(first).extracting(Decision::debited).containsExactly(true, false);
        assertThat(balance(1)).isEqualTo(75_000L);
    }

    @Test
    void preparedTransactionsLeftInDoubtAreResolvedFromTheDecisionLog() throws Exception {
        prepare("order-commit", "UPDATE payments.wallets SET balance_paise = balance_paise - 1000 WHERE customer_id = 1");
        prepare("order-orphan", "UPDATE payments.wallets SET balance_paise = balance_paise - 5000 WHERE customer_id = 2");
        jdbc.sql("INSERT INTO orders.tm_log (gid, decision) VALUES ('order-commit', 'COMMIT')").update();
        Thread.sleep(1_100);                                                  // past the 1 s grace period

        List<InDoubtPolicy.Resolution> done = monitor.check();

        assertThat(done).extracting(InDoubtPolicy.Resolution::action)
                .containsExactly(InDoubtPolicy.Action.COMMIT, InDoubtPolicy.Action.ROLLBACK);
        assertThat(balance(1)).isEqualTo(99_000L);
        assertThat(balance(2)).isEqualTo(10_000L);
        assertThat(jdbc.sql("SELECT count(*) FROM pg_prepared_xacts").query(Long.class).single()).isZero();
    }

    /** What a participant does in phase one: the work, then PREPARE TRANSACTION on a connection of its own. */
    void prepare(String gid, String work) throws Exception {
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute(work);
            s.execute("PREPARE TRANSACTION '" + gid + "'");
            c.setAutoCommit(true);
        }
    }
}
