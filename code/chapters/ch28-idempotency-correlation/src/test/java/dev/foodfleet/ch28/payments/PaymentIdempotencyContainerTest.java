package dev.foodfleet.ch28.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * The payment flow on real PostgreSQL: concurrent duplicates charge once, completed requests are
 * replayed, a reused key with a different body is refused, and a crashed owner's key is taken over
 * after its lease without a second charge. Skipped automatically when Docker isn't available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentIdempotencyContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired PaymentService payments;
    @Autowired PaymentProvider provider;
    @Autowired JdbcClient jdbc;

    final PaymentRequest request = new PaymentRequest("o-9001", 64_000);

    @BeforeEach
    void clean() {
        jdbc.sql("truncate idempotency_keys, payments").update();
    }

    @Test
    void concurrentDuplicatesChargeOnce() throws Exception {
        int chargesBefore = ((InMemoryPaymentProvider) provider).charges();
        List<Future<PaymentService.Outcome>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 16; i++) {
                results.add(pool.submit((Callable<PaymentService.Outcome>) () -> payments.pay("app", "k-1", request)));
            }
        }
        long created = 0;
        for (Future<PaymentService.Outcome> r : results) {
            int status = r.get().status();
            assertThat(status == 201 || status == 409).isTrue();
            if (status == 201 && !r.get().replayed()) {
                created++;
            }
        }
        assertThat(created).isEqualTo(1L);
        assertThat(((InMemoryPaymentProvider) provider).charges() - chargesBefore).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from payments").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void aCompletedRequestIsReplayedAndADifferentBodyIsRefused() {
        PaymentService.Outcome first = payments.pay("app", "k-2", request);
        PaymentService.Outcome again = payments.pay("app", "k-2", request);
        PaymentService.Outcome other = payments.pay("app", "k-2", new PaymentRequest("o-9001", 1));

        assertThat(again.replayed()).isTrue();
        assertThat(again.body()).isEqualTo(first.body());
        assertThat(other.status()).isEqualTo(422);
    }

    @Test
    void anExpiredClaimIsTakenOverWithoutASecondCharge() {
        int chargesBefore = ((InMemoryPaymentProvider) provider).charges();
        // The first owner charged the provider, then crashed before recording anything.
        jdbc.sql("""
                insert into idempotency_keys (client_id, idempotency_key, request_hash, status, locked_until)
                values ('app', 'k-3', :hash, 'IN_PROGRESS', now() - interval '1 second')""")
                .param("hash", PaymentService.fingerprint(request)).update();
        provider.charge("app:k-3", request);

        PaymentService.Outcome retry = payments.pay("app", "k-3", request);

        assertThat(retry.status()).isEqualTo(201);
        assertThat(((InMemoryPaymentProvider) provider).charges() - chargesBefore).isEqualTo(1);   // the provider deduplicated
    }
}
